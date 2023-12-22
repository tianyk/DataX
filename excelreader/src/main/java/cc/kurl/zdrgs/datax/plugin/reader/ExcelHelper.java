package cc.kurl.zdrgs.datax.plugin.reader;

import com.alibaba.datax.common.element.*;
import com.alibaba.datax.common.exception.DataXException;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Iterator;

public class ExcelHelper
{
    public boolean header;
    public int skipRows;
    FileInputStream file;
    Workbook workbook;
    private FormulaEvaluator evaluator;
    private Iterator<Row> rowIterator;

    public ExcelHelper(boolean header, int skipRows) {
        this.header = header;
        this.skipRows = skipRows;
    }
    public void open(String filePath)
    {
        try {
            this.file = new FileInputStream(filePath);
            if (filePath.endsWith(".xlsx")) {
                this.workbook = new XSSFWorkbook(file);
            } else {
                this.workbook = new HSSFWorkbook(file);
            }
            // ONLY reader the first sheet
            Sheet sheet = workbook.getSheetAt(0);
            this.evaluator =  workbook.getCreationHelper().createFormulaEvaluator();
            this.rowIterator = sheet.iterator();
            if (this.header && this.rowIterator.hasNext()) {
                // skip header
                this.rowIterator.next();
            }
            if (this.skipRows > 0) {
                int i =0;
                while (this.rowIterator.hasNext() && i < this.skipRows) {
                    this.rowIterator.next();
                    i++;
                }
            }
        }
        catch (FileNotFoundException e) {
            throw DataXException.asDataXException(ExcelReaderErrorCode.OPEN_FILE_ERROR, e);
        }
        catch (IOException e) {
            throw DataXException.asDataXException(ExcelReaderErrorCode.OPEN_FILE_ERROR,
                    "IOException occurred when open '" + filePath + "':" + e.getMessage());
        }
    }

    public void close()
    {
        try {
            this.workbook.close();
            this.file.close();
        }
        catch (IOException ignored) {

        }
    }

    public Record readLine(Record record)
    {
        if (rowIterator.hasNext()) {
            Row row = rowIterator.next();
            //For each row, iterate through all the columns
            Iterator<Cell> cellIterator = row.cellIterator();
            while (cellIterator.hasNext()) {
                Cell cell = cellIterator.next();
                //Check the cell type after evaluating formulae
                //If it is formula cell, it will be evaluated otherwise no change will happen
                switch (evaluator.evaluateInCell(cell).getCellType()) {
                    case NUMERIC:
                        // numeric include whole numbers, fractional numbers, dates
                        if (DateUtil.isCellDateFormatted(cell)) {
                            record.addColumn(new DateColumn(cell.getDateCellValue()));
                        } else {
                            // integer or long ?
                            double a = cell.getNumericCellValue();
                            if ((long) a == a) {
                                record.addColumn(new LongColumn((long) a));
                            } else {
                                record.addColumn(new DoubleColumn(a));
                            }
                        }
                        break;
                    case STRING:
                        record.addColumn(new StringColumn(cell.getStringCellValue().trim()));
                        break;
                    case BOOLEAN:
                        record.addColumn(new BoolColumn(cell.getBooleanCellValue()));
                        break;
                    case FORMULA:
                    case _NONE:
                        break;
                    case ERROR:
                        // #VALUE!
                        record.addColumn(new StringColumn());
                        break;
                    case BLANK:
                        // empty cell
                        record.addColumn(new StringColumn(""));
                        break;
                }
            }
            return record;
        }
        else {
            return null;
        }
    }
}