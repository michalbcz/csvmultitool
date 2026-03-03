package org.example.commands;

import com.monitorjbl.xlsx.StreamingReader;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.poi.hssf.eventusermodel.*;
import org.apache.poi.hssf.record.BOFRecord;
import org.apache.poi.hssf.record.BoundSheetRecord;
import org.apache.poi.hssf.record.LabelSSTRecord;
import org.apache.poi.hssf.record.NumberRecord;
import org.apache.poi.hssf.record.RowRecord;
import org.apache.poi.hssf.record.SSTRecord;
import org.apache.poi.poifs.filesystem.FileMagic;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.util.IOUtils;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

@Command(name = "in2csv", description = "Convert Excel files to CSV")
public class In2CsvCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "Excel file to convert")
    private File inputFile;

    @Option(names = {"-n", "--names"}, description = "List sheet names only")
    private boolean listNames;

    @Option(names = {"-s", "--sheet"}, description = "Sheet name or index to convert (default: first sheet)")
    private String sheetName;

    @Override
    public Integer call() throws Exception {
        IOUtils.setByteArrayMaxOverride(500_000_000);

        if (!inputFile.exists()) {
            System.err.println("Error: File not found: " + inputFile);
            return 1;
        }

        FileMagic fileMagic = FileMagic.valueOf(inputFile);

        switch (fileMagic) {
            case OLE2:
                if (listNames) {
                    listHssfSheetNames(inputFile);
                } else {
                    System.err.println("Error: Converting Excel 97-2003 (.xls) to CSV is not fully implemented yet.");
                    System.err.println("Please convert your file to .xlsx format first or use sheet listing with -n option.");
                    return 1;
                }
                break;
            case OOXML:
                if (listNames) {
                    listOoxmlSheetNames(inputFile);
                } else {
                    convertOoxmlToCsv(inputFile, sheetName);
                }
                break;
            default:
                System.err.println("Unsupported file type: " + fileMagic.name());
                return 1;
        }

        return 0;
    }

    private void listOoxmlSheetNames(File file) {
        try (Workbook workbook = StreamingReader.builder()
                .rowCacheSize(100)
                .bufferSize(4096)
                .open(file)) {
            for (Sheet sheet : workbook) {
                System.out.println(sheet.getSheetName());
            }
        } catch (Exception e) {
            System.err.println("Error reading Excel file: " + e.getMessage());
        }
    }

    private void listHssfSheetNames(File file) throws IOException {
        try (FileInputStream fin = new FileInputStream(file);
             POIFSFileSystem poifs = new POIFSFileSystem(fin);
             InputStream din = poifs.createDocumentInputStream("Workbook")) {

            HSSFRequest req = new HSSFRequest();
            req.addListener((org.apache.poi.hssf.record.Record record) -> {
                BoundSheetRecord bsr = (BoundSheetRecord) record;
                System.out.println(bsr.getSheetname());
            }, BoundSheetRecord.sid);

            HSSFEventFactory factory = new HSSFEventFactory();
            factory.processEvents(req, din);
        }
    }

    private void convertOoxmlToCsv(File file, String targetSheetName) {
        try (Workbook workbook = StreamingReader.builder()
                .rowCacheSize(100)
                .bufferSize(4096)
                .open(file)) {

            Sheet targetSheet = null;
            
            if (targetSheetName == null) {
                targetSheet = workbook.getSheetAt(0);
            } else {
                // Try to parse as index first
                try {
                    int index = Integer.parseInt(targetSheetName);
                    targetSheet = workbook.getSheetAt(index);
                } catch (NumberFormatException e) {
                    // Not a number, try as sheet name
                    for (Sheet sheet : workbook) {
                        if (sheet.getSheetName().equals(targetSheetName)) {
                            targetSheet = sheet;
                            break;
                        }
                    }
                }
            }

            if (targetSheet == null) {
                System.err.println("Error: Sheet not found: " + targetSheetName);
                return;
            }

            CSVPrinter csvPrinter = new CSVPrinter(System.out, CSVFormat.DEFAULT);

            for (Row row : targetSheet) {
                List<String> values = new ArrayList<>();
                for (Cell cell : row) {
                    values.add(getCellValueAsString(cell));
                }
                csvPrinter.printRecord(values);
            }

            csvPrinter.flush();
        } catch (Exception e) {
            System.err.println("Error converting Excel to CSV: " + e.getMessage());
        }
    }

    private void convertHssfToCsv(File file, String targetSheetName) {
        System.err.println("HSSF (Excel 97-2003) conversion to CSV not yet fully implemented. Use OOXML format (.xlsx)");
    }

    private String getCellValueAsString(Cell cell) {
        if (cell == null) {
            return "";
        }

        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toString();
                } else {
                    double value = cell.getNumericCellValue();
                    // Check if it's a whole number
                    if (value == (long) value) {
                        return String.format("%d", (long) value);
                    } else {
                        return String.valueOf(value);
                    }
                }
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                return cell.getCellFormula();
            case BLANK:
                return "";
            default:
                return "";
        }
    }
}
