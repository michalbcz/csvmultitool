package org.example.commands;

import org.apache.commons.csv.*;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.io.*;
import java.util.*;
import java.util.concurrent.Callable;

import org.example.CsvMultitool;

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

            try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(System.out), CsvMultitool.BUFFER_SIZE)) {
                // Print header separator
                printSeparator(widths, bw);

                // Print header
                printRow(headers, widths, bw);

                // Print header separator
                printSeparator(widths, bw);

                // Print data rows
                for (List<String> row : rows) {
                    printRow(row, widths, bw);
                }

                // Print footer separator
                printSeparator(widths, bw);

                bw.flush();
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

    private void printSeparator(int[] widths, BufferedWriter bw) throws IOException {
        bw.write("|");
        for (int width : widths) {
            bw.write("-");
            for (int i = 0; i < width; i++) {
                bw.write("-");
            }
            bw.write("-|");
        }
        bw.newLine();
    }

    private void printRow(List<String> values, int[] widths, BufferedWriter bw) throws IOException {
        bw.write("|");
        for (int i = 0; i < values.size(); i++) {
            String value = values.get(i);
            int width = widths[i];
            bw.write(" " + padRight(value, width) + " |");
        }
        bw.newLine();
    }

    private String padRight(String s, int n) {
        return String.format("%-" + n + "s", s);
    }
}
