package cc.kurl.zdrgs.datax.plugin.reader;

import com.alibaba.datax.common.element.Record;
import com.alibaba.datax.common.exception.DataXException;
import com.alibaba.datax.common.plugin.RecordSender;
import com.alibaba.datax.common.spi.Reader;
import com.alibaba.datax.common.util.Configuration;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class ExcelReader
        extends Reader {
    public static class Job
            extends Reader.Job {

        private static final Logger LOG = LoggerFactory.getLogger(Job.class);

        private Configuration originConfig = null;
        private List<String> path = null;
        private List<String> sourceFiles;

        @Override
        public void init() {
            this.originConfig = this.getPluginJobConf();
            // Compatible with the old version, path is a string before
            String pathInString = this.originConfig.getNecessaryValue(Key.PATH, ExcelReaderErrorCode.REQUIRED_VALUE);
            if (StringUtils.isBlank(pathInString)) {
                throw DataXException.asDataXException(ExcelReaderErrorCode.REQUIRED_VALUE, "the path is required");
            }
            if (!pathInString.startsWith("[") && !pathInString.endsWith("]")) {
                path = new ArrayList<>();
                path.add(pathInString);
            } else {
                path = this.originConfig.getList(Key.PATH, String.class);
                if (null == path || path.isEmpty()) {
                    throw DataXException.asDataXException(ExcelReaderErrorCode.REQUIRED_VALUE, "the path is required");
                }
            }

//            this.sourceFiles = this.buildSourceTargets();
            this.sourceFiles = FileHelper.buildSourceTargets(path);
            LOG.info("The number of files to read is: [{}]", this.sourceFiles.size());
        }

        @Override
        public void destroy() {
            //
        }

        @Override
        public List<Configuration> split(int adviceNumber) {
            LOG.debug("Begin to split...");
            List<Configuration> readerSplitConfigs = new ArrayList<>();

            // warn:每个slice拖且仅拖一个文件,
            // int splitNumber = adviceNumber
            int splitNumber = this.sourceFiles.size();
            if (0 == splitNumber) {
                throw DataXException.asDataXException(
                        ExcelReaderErrorCode.EMPTY_DIR_EXCEPTION,
                        "Nothing found in the directory " + this.originConfig.getString(Key.PATH) + ". Please check it");
            }

            List<List<String>> splitSourceFiles = FileHelper.splitSourceFiles(this.sourceFiles, splitNumber);
            for (List<String> files : splitSourceFiles) {
                Configuration splitConfig = this.originConfig.clone();
                splitConfig.set(Key.SOURCE_FILES, files);
                readerSplitConfigs.add(splitConfig);
            }
            LOG.debug("Split finished...");
            return readerSplitConfigs;
        }
    }

    public static class Task
            extends Reader.Task {
        private static final Logger LOG = LoggerFactory.getLogger(Task.class);

        private List<String> sourceFiles;
        private boolean header = false;
        private int skipRows = 0;

        @Override
        public void init() {
            Configuration readerSliceConfig = this.getPluginJobConf();
            this.sourceFiles = readerSliceConfig.getList(Key.SOURCE_FILES, String.class);
            this.header = readerSliceConfig.getBool("header", false);
            if (this.header) {
                LOG.info("The first row is skipped as a table header");
            }
            this.skipRows = readerSliceConfig.getInt("skipRows", 0);
            if (this.skipRows > 0) {
                LOG.info("The first {} rows is skipped", this.skipRows);
            }
        }

        @Override
        public void destroy() {
            //
        }

        @Override
        public void startRead(RecordSender recordSender) {
            for (String file : sourceFiles) {
                LOG.info("begin read file {}", file);
                ExcelHelper excelHelper = new ExcelHelper(header, skipRows);
                excelHelper.open(file);
                Record record = excelHelper.readLine(recordSender.createRecord());
                while (record != null) {
                    recordSender.sendToWriter(record);
                    record = excelHelper.readLine(recordSender.createRecord());
                }
                excelHelper.close();
            }
        }
    }
}