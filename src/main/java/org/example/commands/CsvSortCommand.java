package org.example.commands;

import org.apache.commons.csv.*;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.*;
import java.util.*;
import java.util.concurrent.Callable;

import org.example.CsvMultitool;

@Command(name = "csvsort", description = "Sort CSV data by columns")
public class CsvSortCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "CSV file to sort (use '-' for stdin)", arity = "0..1")
    private String inputFile = "-";

    @Option(names = {"-c", "--columns"}, description = "Columns to sort by (comma-separated)", split = ",")
    private String[] sortColumns;

    @Option(names = {"-r", "--reverse"}, description = "Sort in descending order")
    private boolean reverse;

    @Override
    public Integer call() throws Exception {
        try (Reader reader = getReader();
             CSVParser parser = CSVFormat.DEFAULT.withFirstRecordAsHeader().parse(reader)) {

            List<String> headers = new ArrayList<>(parser.getHeaderMap().keySet());
            List<CSVRecord> records = new ArrayList<>();

            // Read all records
            for (CSVRecord record : parser) {
                records.add(record);
            }

            // Determine sort columns
            List<String> columnsToSort = determineSortColumns(headers);

            if (columnsToSort.isEmpty()) {
                System.err.println("Error: No sort columns specified");
                return 1;
            }

            // Sort records
            records.sort((r1, r2) -> {
                for (String col : columnsToSort) {
                    String v1 = r1.get(col);
                    String v2 = r2.get(col);
                    
                    // Try numeric comparison first
                    try {
                        Double d1 = Double.parseDouble(v1);
                        Double d2 = Double.parseDouble(v2);
                        int cmp = d1.compareTo(d2);
                        if (cmp != 0) {
                            return reverse ? -cmp : cmp;
                        }
                    } catch (NumberFormatException e) {
                        // Fall back to string comparison
                        int cmp = v1.compareTo(v2);
                        if (cmp != 0) {
                            return reverse ? -cmp : cmp;
                        }
                    }
                }
                return 0;
            });

            // Output sorted data
            try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(System.out), CsvMultitool.BUFFER_SIZE);
                 CSVPrinter printer = new CSVPrinter(bw, CSVFormat.DEFAULT)) {
                printer.printRecord(headers);

                for (CSVRecord record : records) {
                    List<String> values = new ArrayList<>();
                    for (String header : headers) {
                        values.add(record.get(header));
                    }
                    printer.printRecord(values);
                }

                printer.flush();
            }
            return 0;

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            return 1;
        }
    }

    private Reader getReader() throws IOException {
        if ("-".equals(inputFile)) {
            return new BufferedReader(new InputStreamReader(System.in), CsvMultitool.BUFFER_SIZE);
        } else {
            File file = new File(inputFile);
            if (!file.exists()) {
                throw new IOException("File not found: " + inputFile);
            }
            return new BufferedReader(new FileReader(file), CsvMultitool.BUFFER_SIZE);
        }
    }

    private List<String> determineSortColumns(List<String> headers) {
        List<String> result = new ArrayList<>();

        if (sortColumns == null || sortColumns.length == 0) {
            // Default to first column
            if (!headers.isEmpty()) {
                result.add(headers.get(0));
            }
            return result;
        }

        for (String col : sortColumns) {
            col = col.trim();
            // Try to parse as 1-based index
            try {
                int index = Integer.parseInt(col) - 1;
                if (index >= 0 && index < headers.size()) {
                    result.add(headers.get(index));
                }
            } catch (NumberFormatException e) {
                // Not a number, treat as column name
                if (headers.contains(col)) {
                    result.add(col);
                }
            }
        }

        return result;
    }
}
