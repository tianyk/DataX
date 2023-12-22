package cc.kurl.zdrgs.datax.plugin.reader;

import com.google.common.collect.ImmutableMap;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Pattern;

public class FileHelper {
    public final static Logger LOG = LoggerFactory.getLogger(FileHelper.class);

    private final static ImmutableMap<String, String> COMPRESS_TYPE_SUFFIX_MAP = new ImmutableMap.Builder<String, String>()
            .put("BZIP", ".bz2")
            .put("BZIP2", ".bz2")
            .put("DEFLATE", ".deflate")
            .put("DEFLATE64", ".deflate")
            .put("GZIP", ".gz")
            .put("GZ", ".gz")
            .put("LZ4", ".lz4")
            .put("LZ4-BLOCK", ".lz4")
            .put("LZ4-FRAMED", ".lz4")
            .put("LZO", ".lzo")
            .put("LZOP", ".lzo")
            .put("SNAPPY", ".snappy")
            .put("XZ", ".xz")
            .put("Z", ".z")
            .put("ZIP", ".zip")
            .put("ZLIB", ".zlib")
            .put("ZSTANDARD", ".zstd")
            .put("ZSTD", ".zstd")
            .build();

    public static final HashMap<String, String> mFileTypes = new HashMap<>();

    static {
        // compress type magic number
        mFileTypes.put("504B", "zip");// zip 压缩文件
        mFileTypes.put("5261", "rar");
        mFileTypes.put("1F8B", "gz");
        mFileTypes.put("1F9D", "z");// z, tar.z using Lempel-Ziv-Welch algorithm
        mFileTypes.put("1FA0", "z"); //z, tar.z using LZH algorithm
        mFileTypes.put("425A", "bz2");
        mFileTypes.put("377A", "7z");
        mFileTypes.put("FD37", "xz");
        mFileTypes.put("0422", "lz4");
        mFileTypes.put("7573", "tar");
    }

    /**
     * Return the corresponding file name suffixes according to different compression algorithms
     *
     * @param compress the compression type name
     * @return the suffix if present, otherwise ""
     */
    public static String getCompressFileSuffix(String compress) {
        if (compress == null || compress.isEmpty() || "none".equalsIgnoreCase(compress)) {
            return "";
        }
        return COMPRESS_TYPE_SUFFIX_MAP.getOrDefault(compress.toUpperCase(), "." + compress.toLowerCase());
    }

    public static boolean checkDirectoryReadable(String directory) {
        return checkDirPermission(directory, "r");
    }

    public static boolean checkDirectoryWritable(String directory) {
        return checkDirPermission(directory, "w");
    }

    private static boolean checkDirPermission(String directory, String permission) {
        File file = new File(FilenameUtils.getFullPath(directory));
        if (!file.exists()) {
            throw new RuntimeException("The directory [" + directory + "] does not exists.");
        }
        if (!file.isDirectory()) {
            throw new RuntimeException("The [" + directory + "] is not a directory.");
        }

        if ("r".equalsIgnoreCase(permission)) {
            return file.canRead();
        } else {
            return file.canWrite();
        }
    }

    public static Pattern generatePattern(String dir) {
        String regexString = dir.replace("*", ".*").replace("?", ".?");
        return Pattern.compile(regexString);
    }

    public static boolean isTargetFile(Map<String, Pattern> patterns, Map<String, Boolean> isRegexPath, String regexPath, String absoluteFilePath) {
        if (isRegexPath.get(regexPath)) {
            return patterns.get(regexPath).matcher(absoluteFilePath).matches();
        } else {
            return true;
        }
    }

    // validate the path, path must be an absolute path
    public static List<String> buildSourceTargets(List<String> directories) {
        Map<String, Boolean> isRegexPath = new HashMap<>();
        Map<String, Pattern> patterns = new HashMap<>();

        // for each path
        Set<String> toBeReadFiles = new HashSet<>();
        for (String eachPath : directories) {
            int endMark;
            for (endMark = 0; endMark < eachPath.length(); endMark++) {
                if ('*' == eachPath.charAt(endMark) || '?' == eachPath.charAt(endMark)) {
                    isRegexPath.put(eachPath, true);
                    patterns.put(eachPath, generatePattern(eachPath));
                    break;
                }
            }

            String parentDirectory;
            if (!isRegexPath.isEmpty() && isRegexPath.get(eachPath)) {
                int lastDirSeparator = eachPath.substring(0, endMark).lastIndexOf(IOUtils.DIR_SEPARATOR);
                parentDirectory = eachPath.substring(0, lastDirSeparator + 1);
            } else {
                isRegexPath.put(eachPath, false);
                parentDirectory = eachPath;
            }
            buildSourceTargetsEachPath(eachPath, parentDirectory, toBeReadFiles, patterns, isRegexPath);
        }
        return Arrays.asList(toBeReadFiles.toArray(new String[0]));
    }

    private static void buildSourceTargetsEachPath(String regexPath, String parentDirectory, Set<String> toBeReadFiles,
                                                   Map<String, Pattern> patterns, Map<String, Boolean> isRegexPath) {
        // 检测目录是否存在，错误情况更明确
        assert checkDirectoryReadable(parentDirectory);

        directoryRover(regexPath, parentDirectory, toBeReadFiles, patterns, isRegexPath);
    }

    private static void directoryRover(String regexPath, String parentDirectory, Set<String> toBeReadFiles,
                                       Map<String, Pattern> patterns, Map<String, Boolean> isRegexPath) {
        File directory = new File(parentDirectory);
        // is a normal file
        if (!directory.isDirectory()) {
            if (isTargetFile(patterns, isRegexPath, regexPath, directory.getAbsolutePath())) {
                toBeReadFiles.add(parentDirectory);
                LOG.info("Adding the file [{}] as a candidate to be read.", parentDirectory);
            }
        } else {
            // 是目录
            try {
                // warn:对于没有权限的目录,listFiles 返回null，而不是抛出SecurityException
                File[] files = directory.listFiles();
                if (null != files) {
                    for (File subFileNames : files) {
                        directoryRover(regexPath, subFileNames.getAbsolutePath(), toBeReadFiles, patterns, isRegexPath);
                    }
                } else {
                    // warn: 对于没有权限的文件，是直接throw AddaxException
                    String message = String.format("Permission denied for reading directory [%s].", directory);
                    LOG.error(message);
                    throw new RuntimeException(message);
                }
            } catch (SecurityException e) {
                String message = String.format("Permission denied for reading directory [%s].", directory);
                LOG.error(message);
                throw new RuntimeException(message);
            }
        }
    }

    //    -----
    public static <T> List<List<T>> splitSourceFiles(final List<T> sourceList, int adviceNumber) {
        List<List<T>> splitedList = new ArrayList<>();
        int averageLength = sourceList.size() / adviceNumber;
        averageLength = averageLength == 0 ? 1 : averageLength;

        for (int begin = 0, end; begin < sourceList.size(); begin = end) {
            end = begin + averageLength;
            if (end > sourceList.size()) {
                end = sourceList.size();
            }
            splitedList.add(sourceList.subList(begin, end));
        }
        return splitedList;
    }

    public static String generateFileMiddleName() {
        String randomChars = "0123456789abcdefghmnpqrstuvwxyz";
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS");
        // like 2021-12-03-14-33-29-237-6587fddb
        return dateFormat.format(new Date()) + "_" + RandomStringUtils.random(8, randomChars);
    }
}