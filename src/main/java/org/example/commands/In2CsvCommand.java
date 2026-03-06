package org.example.commands;

import com.monitorjbl.xlsx.StreamingReader;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.poi.hssf.eventusermodel.*;
import org.apache.poi.hssf.record.BOFRecord;
import org.apache.poi.hssf.record.BoundSheetRecord;
import org.apache.poi.hssf.record.EOFRecord;
import org.apache.poi.hssf.record.LabelSSTRecord;
import org.apache.poi.hssf.record.NumberRecord;
import org.apache.poi.hssf.record.RowRecord;
import org.apache.poi.hssf.record.SSTRecord;
import org.apache.poi.hssf.record.StringRecord;
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

import org.example.CsvMultitool;

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
                    convertHssfToCsv(inputFile, sheetName);
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
        // Optimize for speed and SSD by using larger cache and buffer sizes
        try (Workbook workbook = StreamingReader.builder()
                .rowCacheSize(1000)
                .bufferSize(16384)
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
        // Optimize for speed and SSD by using larger cache and buffer sizes
        try (Workbook workbook = StreamingReader.builder()
                .rowCacheSize(1000)
                .bufferSize(16384)
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

            // Using BufferedWriter with a large buffer (64KB) to optimize for SSD writes
            try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(System.out), CsvMultitool.BUFFER_SIZE);
                 CSVPrinter csvPrinter = new CSVPrinter(bw, CSVFormat.DEFAULT)) {
                for (Row row : targetSheet) {
                    List<String> values = new ArrayList<>();
                    // To handle sparse rows in streaming properly, get physical cells or iterate with padding
                    int lastCellNum = row.getLastCellNum();
                    if (lastCellNum < 0) {
                        csvPrinter.printRecord(values);
                        continue;
                    }

                    for (int i = 0; i < lastCellNum; i++) {
                        Cell cell = row.getCell(i, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                        values.add(getCellValueAsString(cell));
                    }
                    csvPrinter.printRecord(values);
                }

                csvPrinter.flush();
            }
        } catch (Exception e) {
            System.err.println("Error converting Excel to CSV: " + e.getMessage());
        }
    }

    private void convertHssfToCsv(File file, String targetSheetName) {
        // HSSFWorkbook reads the entire file into memory which causes OOM on large files
        // We will use the event-based API for low-memory footprint extraction
        try (FileInputStream fin = new FileInputStream(file);
             POIFSFileSystem poifs = new POIFSFileSystem(fin);
             InputStream din = poifs.createDocumentInputStream("Workbook")) {

            HSSFRequest req = new HSSFRequest();

            // To properly reconstruct rows in CSV with event API, we need to track state
            List<String> currentValues = new ArrayList<>();
            int currentRow = 0;
            int maxCellInRow = -1;
            boolean inTargetSheet = false;
            int currentSheetIndex = -1;
            String currentSheetName = null;

            // Attempt to determine target sheet index if given as integer
            int targetIndex = -1;
            if (targetSheetName != null) {
                try {
                    targetIndex = Integer.parseInt(targetSheetName);
                } catch (NumberFormatException e) {
                    // Not an index
                }
            } else {
                targetIndex = 0; // Default to first sheet
            }

            // This requires a more complex listener setup to buffer rows and print them
            // For now, let's implement a memory-efficient version using the event API
            // Because full HSSFWorkbook causes OOM on the 450MB benchmark file.

            final int tIndex = targetIndex;
            final String tName = targetSheetName;

            // We use an array of objects to act as a mutable boolean/state across listener callbacks
            final Object[] state = {
                false, // [0] inTargetSheet
                0,     // [1] currentSheetIndex
                null,  // [2] currentSheetName
                0,     // [3] currentRow
                new ArrayList<String>(), // [4] currentValues
                new BufferedWriter(new OutputStreamWriter(System.out), CsvMultitool.BUFFER_SIZE), // [5] bw
                null,  // [6] csvPrinter (will be initialized)
                new SSTRecord(), // [7] sstRecord (for resolving strings)
                new FormatTrackingHSSFListener(null) // [8] formatter
            };

            state[6] = new CSVPrinter((BufferedWriter)state[5], CSVFormat.DEFAULT);

            HSSFListener listener = (org.apache.poi.hssf.record.Record record) -> {
                try {
                    boolean isTarget = (boolean) state[0];
                    int sIndex = (int) state[1];
                    String sName = (String) state[2];
                    int cRow = (int) state[3];
                    List<String> values = (List<String>) state[4];
                    CSVPrinter printer = (CSVPrinter) state[6];
                    SSTRecord sst = (SSTRecord) state[7];

                    switch (record.getSid()) {
                        case BoundSheetRecord.sid:
                            BoundSheetRecord bsr = (BoundSheetRecord) record;
                            if (sIndex == tIndex || bsr.getSheetname().equals(tName)) {
                                state[0] = true;
                                state[2] = bsr.getSheetname();
                            }
                            state[1] = sIndex + 1;
                            break;

                        case BOFRecord.sid:
                            BOFRecord bof = (BOFRecord) record;
                            if (bof.getType() == BOFRecord.TYPE_WORKSHEET) {
                                // Keep track of current sheet index using BOF if needed
                            }
                            break;

                        case EOFRecord.sid:
                            // End of sheet, could trigger flush if we track sheet scope exactly
                            break;

                        case SSTRecord.sid:
                            state[7] = record;
                            break;

                        case NumberRecord.sid:
                            if (!isTarget) break;
                            NumberRecord numrec = (NumberRecord) record;
                            ensureRow(numrec.getRow(), state);
                            values = (List<String>) state[4];
                            ensureCapacity(values, numrec.getColumn());

                            // Check if it's a whole number
                            double value = numrec.getValue();
                            if (value == (long) value) {
                                values.set(numrec.getColumn(), String.format("%d", (long) value));
                            } else {
                                values.set(numrec.getColumn(), String.valueOf(value));
                            }
                            break;


                        case LabelSSTRecord.sid:
                            if (!isTarget) break;
                            LabelSSTRecord lrec = (LabelSSTRecord) record;
                            ensureRow(lrec.getRow(), state);
                            values = (List<String>) state[4];
                            ensureCapacity(values, lrec.getColumn());
                            values.set(lrec.getColumn(), sst.getString(lrec.getSSTIndex()).toString());
                            break;

                        case RowRecord.sid:
                            // We use cell records to determine rows, but we could use this for max columns
                            break;
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            };
            req.addListener(listener, BoundSheetRecord.sid);
            req.addListener(listener, BOFRecord.sid);
            req.addListener(listener, EOFRecord.sid);
            req.addListener(listener, SSTRecord.sid);
            req.addListener(listener, NumberRecord.sid);
            req.addListener(listener, LabelSSTRecord.sid);
            req.addListener(listener, RowRecord.sid);

            HSSFEventFactory factory = new HSSFEventFactory();
            factory.processEvents(req, din);

            // Print the last row
            List<String> values = (List<String>) state[4];
            if (!values.isEmpty()) {
                CSVPrinter printer = (CSVPrinter) state[6];
                printer.printRecord(values);
            }

            ((CSVPrinter)state[6]).flush();
            ((BufferedWriter)state[5]).flush();

        } catch (Exception e) {
            System.err.println("Error converting HSSF Excel to CSV: " + e.getMessage());
        }
    }

    private void ensureRow(int newRow, Object[] state) throws IOException {
        int currentRow = (int) state[3];
        if (newRow > currentRow) {
            // Print previous rows
            CSVPrinter printer = (CSVPrinter) state[6];
            List<String> values = (List<String>) state[4];

            printer.printRecord(values);

            // Print empty rows if we skipped any
            for (int i = currentRow + 1; i < newRow; i++) {
                printer.printRecord(new ArrayList<String>());
            }

            // Reset for new row
            state[3] = newRow;
            state[4] = new ArrayList<String>();
        }
    }

    private void ensureCapacity(List<String> list, int index) {
        while (list.size() <= index) {
            list.add("");
        }
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
