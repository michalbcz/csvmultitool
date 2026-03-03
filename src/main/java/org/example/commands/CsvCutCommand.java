package org.example.commands;

import org.apache.commons.csv.*;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.*;
import java.util.*;
import java.util.concurrent.Callable;

@Command(name = "csvcut", description = "Select or reorder columns from CSV files")
public class CsvCutCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "CSV file to process (use '-' for stdin)", arity = "0..1")
    private String inputFile = "-";

    @Option(names = {"-n", "--names"}, description = "Display column names and indices")
    private boolean displayNames;

    @Option(names = {"-c", "--columns"}, description = "Columns to include (comma-separated names or 1-based indices)", split = ",")
    private String[] columns;

    @Option(names = {"-C", "--not-columns"}, description = "Columns to exclude", split = ",")
    private String[] notColumns;

    @Override
    public Integer call() throws Exception {
        try (Reader reader = getReader();
             CSVParser parser = CSVFormat.DEFAULT.withFirstRecordAsHeader().parse(reader)) {

            Map<String, Integer> headerMap = parser.getHeaderMap();
            List<String> headers = new ArrayList<>(headerMap.keySet());

            if (displayNames) {
                int index = 1;
                for (String header : headers) {
                    System.out.println("  " + index + ": " + header);
                    index++;
                }
                return 0;
            }

            List<String> selectedColumns = determineSelectedColumns(headers);
            
            if (selectedColumns.isEmpty()) {
                System.err.println("Error: No columns selected");
                return 1;
            }

            try (CSVPrinter printer = new CSVPrinter(System.out, CSVFormat.DEFAULT)) {
                // Print header
                printer.printRecord(selectedColumns);

                // Print data rows
                for (CSVRecord record : parser) {
                    List<String> values = new ArrayList<>();
                    for (String col : selectedColumns) {
                        values.add(record.get(col));
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
            return new InputStreamReader(System.in);
        } else {
            File file = new File(inputFile);
            if (!file.exists()) {
                throw new IOException("File not found: " + inputFile);
            }
            return new FileReader(file);
        }
    }

    private List<String> determineSelectedColumns(List<String> allHeaders) {
        Set<String> selected = new LinkedHashSet<>();

        if (columns == null && notColumns == null) {
            // No selection specified, return all columns
            return allHeaders;
        }

        if (columns != null) {
            // Add specified columns
            for (String col : columns) {
                col = col.trim();
                // Try to parse as 1-based index
                try {
                    int index = Integer.parseInt(col) - 1;
                    if (index >= 0 && index < allHeaders.size()) {
                        selected.add(allHeaders.get(index));
                    }
                } catch (NumberFormatException e) {
                    // Not a number, treat as column name
                    if (allHeaders.contains(col)) {
                        selected.add(col);
                    }
                }
            }
        } else {
            // Start with all columns if using NOT
            selected.addAll(allHeaders);
        }

        if (notColumns != null) {
            // Remove excluded columns
            for (String col : notColumns) {
                col = col.trim();
                try {
                    int index = Integer.parseInt(col) - 1;
                    if (index >= 0 && index < allHeaders.size()) {
                        selected.remove(allHeaders.get(index));
                    }
                } catch (NumberFormatException e) {
                    selected.remove(col);
                }
            }
        }

        return new ArrayList<>(selected);
    }
}
