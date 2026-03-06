package org.example.commands;

import org.apache.commons.csv.*;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.io.*;
import java.util.*;
import java.util.concurrent.Callable;

import org.example.CsvMultitool;

@Command(name = "csvstat", description = "Generate summary statistics for CSV columns")
public class CsvStatCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "CSV file to analyze (use '-' for stdin)", arity = "0..1")
    private String inputFile = "-";

    @Override
    public Integer call() throws Exception {
        try (Reader reader = getReader();
             CSVParser parser = CSVFormat.DEFAULT.withFirstRecordAsHeader().parse(reader)) {

            List<String> headers = new ArrayList<>(parser.getHeaderMap().keySet());
            Map<String, ColumnStats> stats = new LinkedHashMap<>();

            // Initialize stats for each column
            for (String header : headers) {
                stats.put(header, new ColumnStats());
            }

            // Collect stats
            int rowCount = 0;
            for (CSVRecord record : parser) {
                rowCount++;
                for (String header : headers) {
                    String value = record.get(header);
                    stats.get(header).addValue(value);
                }
            }

            // Print statistics
            System.out.println("Total rows: " + rowCount);
            System.out.println();

            int colIndex = 1;
            for (String header : headers) {
                ColumnStats colStats = stats.get(header);
                System.out.println(colIndex + ". \"" + header + "\"");
                System.out.println();
                System.out.println("\tType: " + colStats.getType());
                System.out.println("\tNulls: " + colStats.getNullCount());
                System.out.println("\tUnique values: " + colStats.getUniqueCount());

                if (colStats.isNumeric()) {
                    System.out.println("\tMin: " + colStats.getMin());
                    System.out.println("\tMax: " + colStats.getMax());
                    System.out.println("\tMean: " + String.format("%.2f", colStats.getMean()));
                } else {
                    System.out.println("\tMax length: " + colStats.getMaxLength());
                }

                if (colStats.getUniqueCount() <= 5) {
                    System.out.println("\tValues: " + colStats.getUniqueValues());
                }

                System.out.println();
                colIndex++;
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

    private static class ColumnStats {
        private int nullCount = 0;
        private Set<String> uniqueValues = new LinkedHashSet<>();
        private List<Double> numericValues = new ArrayList<>();
        private boolean isNumeric = true;
        private int maxLength = 0;

        public void addValue(String value) {
            if (value == null || value.trim().isEmpty()) {
                nullCount++;
                return;
            }

            uniqueValues.add(value);
            maxLength = Math.max(maxLength, value.length());

            if (isNumeric) {
                try {
                    double numValue = Double.parseDouble(value);
                    numericValues.add(numValue);
                } catch (NumberFormatException e) {
                    isNumeric = false;
                    numericValues.clear();
                }
            }
        }

        public String getType() {
            if (isNumeric && !numericValues.isEmpty()) {
                return "Number";
            }
            return "Text";
        }

        public int getNullCount() {
            return nullCount;
        }

        public int getUniqueCount() {
            return uniqueValues.size();
        }

        public Set<String> getUniqueValues() {
            return uniqueValues;
        }

        public boolean isNumeric() {
            return isNumeric && !numericValues.isEmpty();
        }

        public double getMin() {
            return numericValues.stream().mapToDouble(Double::doubleValue).min().orElse(0);
        }

        public double getMax() {
            return numericValues.stream().mapToDouble(Double::doubleValue).max().orElse(0);
        }

        public double getMean() {
            return numericValues.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        }

        public int getMaxLength() {
            return maxLength;
        }
    }
}
