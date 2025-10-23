package org.example;

import de.siegmar.fastcsv.writer.CsvWriter;
import org.apache.poi.hssf.eventusermodel.HSSFEventFactory;
import org.apache.poi.hssf.eventusermodel.HSSFListener;
import org.apache.poi.hssf.eventusermodel.HSSFRequest;
import org.apache.poi.hssf.record.*;
import org.apache.poi.hssf.record.Record;
import org.apache.poi.poifs.filesystem.FileMagic;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.util.IOUtils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;



public class CsvMultitool implements HSSFListener {

    private SSTRecord sstrec;
    /**
     * This method listens for incoming records and handles them as required.
     * @param record    The record that was found while reading.
     */
    public void processRecord(Record record)
    {
        switch (record.getSid())
        {
            // the BOFRecord can represent either the beginning of a sheet or the workbook
            case BOFRecord.sid:
                BOFRecord bof = (BOFRecord) record;
                if (bof.getType() == bof.TYPE_WORKBOOK)
                {
                    System.out.println("Encountered workbook");
                    // assigned to the class level member
                } else if (bof.getType() == bof.TYPE_WORKSHEET)
                {
                    System.out.println("Encountered sheet reference");
                }
                break;
            case BoundSheetRecord.sid:
                BoundSheetRecord bsr = (BoundSheetRecord) record;
                System.out.println("New sheet named: " + bsr.getSheetname());
                break;
            case RowRecord.sid:
                RowRecord rowrec = (RowRecord) record;
                System.out.println("Row found, first column at "
                        + rowrec.getFirstCol() + " last column at " + rowrec.getLastCol());
                break;
            case NumberRecord.sid:
                NumberRecord numrec = (NumberRecord) record;
                System.out.println("Cell found with value " + numrec.getValue()
                        + " at row " + numrec.getRow() + " and column " + numrec.getColumn());
                break;
                // SSTRecords store an array of unique strings used in Excel.
            case SSTRecord.sid:
                sstrec = (SSTRecord) record;
                for (int k = 0; k < sstrec.getNumUniqueStrings(); k++)
                {
                    System.out.println("String table value " + k + " = " + sstrec.getString(k));
                }
                break;
            case LabelSSTRecord.sid:
                LabelSSTRecord lrec = (LabelSSTRecord) record;
                System.out.println("String cell found with value "
                        + sstrec.getString(lrec.getSSTIndex()));
                break;
        }
    }
    /**
     * Read an excel file and convert a sheet to CSV.
     *
     * @param args      Arguments: <file-path> [sheet-name-or-index] [output-file]
     * @throws IOException  When there is an error processing the file.
     */
    public static void main(String[] args) throws IOException
    {
        IOUtils.setByteArrayMaxOverride(500_000_000);

        if (args.length < 1) {
            println("Usage: <file-path> [sheet-name-or-index] [output-file]");
            println("  file-path: Path to the Excel file");
            println("  sheet-name-or-index: (Optional) Sheet name or 0-based index. If not provided, lists all sheets.");
            println("  output-file: (Optional) Output CSV file path. If not provided, writes to stdout.");
            return;
        }

        String filePath = args[0];
        String sheetIdentifier = args.length > 1 ? args[1] : null;
        String outputFile = args.length > 2 ? args[2] : null;
        
        File file = new File(filePath);
        FileMagic fileMagic = FileMagic.valueOf(file);

        switch (fileMagic) {
            case OLE2 -> processHssfExcel97(file, sheetIdentifier, outputFile);
            case OOXML -> processOoxmlExcel2003(file, sheetIdentifier, outputFile);
            default -> println("Unsupported file type: " + fileMagic.name());
        }
    }

    private static void processOoxmlExcel2003(File file, String sheetIdentifier, String outputFile) throws IOException {
        
        // Use the full Apache POI API
        try (FileInputStream fis = new FileInputStream(file);
             org.apache.poi.xssf.usermodel.XSSFWorkbook workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook(fis)) {
            
            // If no sheet identifier is provided, list all sheets
            if (sheetIdentifier == null) {
                for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                    println(workbook.getSheetName(i));
                }
                return;
            }
            
            // Find the target sheet by name or index
            Sheet targetSheet = findSheet(workbook, sheetIdentifier);
            
            if (targetSheet == null) {
                println("Sheet not found: " + sheetIdentifier);
                return;
            }
            
            // Convert sheet to CSV
            convertSheetToCsv(targetSheet, outputFile);
        }
    }

    private static void println(String text) {
        System.out.println(text);
    }

    private static Sheet findSheet(Workbook workbook, String sheetIdentifier) {
        // Try to parse as an integer (0-based index)
        try {
            int sheetIndex = Integer.parseInt(sheetIdentifier);
            if (sheetIndex >= 0 && sheetIndex < workbook.getNumberOfSheets()) {
                return workbook.getSheetAt(sheetIndex);
            }
        } catch (NumberFormatException e) {
            // Not an integer, try as sheet name
        }
        
        // Try to find by name
        return workbook.getSheet(sheetIdentifier);
    }
    
    private static void convertSheetToCsv(Sheet sheet, String outputFile) throws IOException {
        Writer writer;
        
        if (outputFile != null) {
            writer = new FileWriter(outputFile, StandardCharsets.UTF_8);
        } else {
            writer = new OutputStreamWriter(System.out, StandardCharsets.UTF_8);
        }
        
        try (CsvWriter csvWriter = CsvWriter.builder().build(writer)) {
            for (Row row : sheet) {
                List<String> values = new ArrayList<>();
                
                // Get the last cell number to ensure we write all columns
                int lastCellNum = row.getLastCellNum();
                
                for (int cellIndex = 0; cellIndex < lastCellNum; cellIndex++) {
                    Cell cell = row.getCell(cellIndex);
                    values.add(getCellValueAsString(cell));
                }
                
                csvWriter.writeRecord(values);
            }
        }
        
        if (outputFile != null) {
            writer.close();
        }
    }
    
    private static String getCellValueAsString(Cell cell) {
        if (cell == null) {
            return "";
        }
        
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (org.apache.poi.ss.usermodel.DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toString();
                }
                return String.valueOf(cell.getNumericCellValue());
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                try {
                    return cell.getStringCellValue();
                } catch (IllegalStateException e) {
                    try {
                        return String.valueOf(cell.getNumericCellValue());
                    } catch (IllegalStateException e2) {
                        return cell.getCellFormula();
                    }
                }
            case BLANK:
                return "";
            default:
                return "";
        }
    }

    private static void processHssfExcel97(File file, String sheetIdentifier, String outputFile) throws IOException {
        // For Excel 97, we need to use the full POI library to properly convert sheets
        
        try (FileInputStream fis = new FileInputStream(file);
             org.apache.poi.hssf.usermodel.HSSFWorkbook workbook = new org.apache.poi.hssf.usermodel.HSSFWorkbook(fis)) {
            
            // If no sheet identifier is provided, list all sheets
            if (sheetIdentifier == null) {
                for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                    println(workbook.getSheetName(i));
                }
                return;
            }
            
            // Find the target sheet by name or index
            Sheet targetSheet = findSheet(workbook, sheetIdentifier);
            
            if (targetSheet == null) {
                println("Sheet not found: " + sheetIdentifier);
                return;
            }
            
            // Convert sheet to CSV
            convertSheetToCsv(targetSheet, outputFile);
        }
    }

}