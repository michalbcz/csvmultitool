package org.example.commands;

import org.apache.commons.csv.*;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.io.*;
import java.util.*;
import java.util.concurrent.Callable;

@Command(name = "csvlook", description = "Pretty-print CSV data in a table format")
public class CsvLookCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "CSV file to display (use '-' for stdin)", arity = "0..1")
    private String inputFile = "-";

    @Override
    public Integer call() throws Exception {
        try (Reader reader = getReader();
             CSVParser parser = CSVFormat.DEFAULT.withFirstRecordAsHeader().parse(reader)) {

            List<String> headers = new ArrayList<>(parser.getHeaderMap().keySet());
            List<List<String>> rows = new ArrayList<>();

            // Read all records
            for (CSVRecord record : parser) {
                List<String> row = new ArrayList<>();
                for (String header : headers) {
                    row.add(record.get(header));
                }
                rows.add(row);
            }

            // Calculate column widths
            int[] widths = calculateColumnWidths(headers, rows);

            // Print header separator
            printSeparator(widths);

            // Print header
            printRow(headers, widths);

            // Print header separator
            printSeparator(widths);

            // Print data rows
            for (List<String> row : rows) {
                printRow(row, widths);
            }

            // Print footer separator
            printSeparator(widths);

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

    private int[] calculateColumnWidths(List<String> headers, List<List<String>> rows) {
        int[] widths = new int[headers.size()];

        // Initialize with header widths
        for (int i = 0; i < headers.size(); i++) {
            widths[i] = headers.get(i).length();
        }

        // Update with data widths
        for (List<String> row : rows) {
            for (int i = 0; i < row.size() && i < widths.length; i++) {
                widths[i] = Math.max(widths[i], row.get(i).length());
            }
        }

        return widths;
    }

    private void printSeparator(int[] widths) {
        System.out.print("|");
        for (int width : widths) {
            System.out.print("-");
            for (int i = 0; i < width; i++) {
                System.out.print("-");
            }
            System.out.print("-|");
        }
        System.out.println();
    }

    private void printRow(List<String> values, int[] widths) {
        System.out.print("|");
        for (int i = 0; i < values.size(); i++) {
            String value = values.get(i);
            int width = widths[i];
            System.out.print(" " + padRight(value, width) + " |");
        }
        System.out.println();
    }

    private String padRight(String s, int n) {
        return String.format("%-" + n + "s", s);
    }
}
