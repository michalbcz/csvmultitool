package org.example.commands;

import org.apache.commons.csv.*;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.*;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;

import org.example.CsvMultitool;

@Command(name = "csvgrep", description = "Filter CSV rows based on pattern matching")
public class CsvGrepCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "CSV file to filter (use '-' for stdin)", arity = "0..1")
    private String inputFile = "-";

    @Option(names = {"-c", "--column"}, description = "Column name or 1-based index to search", required = true)
    private String column;

    @Option(names = {"-m", "--match"}, description = "Exact string to match")
    private String matchString;

    @Option(names = {"-r", "--regex"}, description = "Regular expression to match")
    private String regex;

    @Option(names = {"-i", "--invert"}, description = "Invert match (select non-matching rows)")
    private boolean invert;

    @Override
    public Integer call() throws Exception {
        try {
            if (matchString == null && regex == null) {
                System.err.println("Error: Either --match or --regex must be specified");
                return 1;
            }

            try (Reader reader = getReader();
                 CSVParser parser = CSVFormat.DEFAULT.withFirstRecordAsHeader().parse(reader)) {

                List<String> headers = new ArrayList<>(parser.getHeaderMap().keySet());
                String targetColumn = resolveColumn(headers);

                if (targetColumn == null) {
                    System.err.println("Error: Column not found: " + column);
                    return 1;
                }

                Pattern pattern = null;
                if (regex != null) {
                    pattern = Pattern.compile(regex);
                }

                try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(System.out), CsvMultitool.BUFFER_SIZE);
                     CSVPrinter printer = new CSVPrinter(bw, CSVFormat.DEFAULT)) {
                    printer.printRecord(headers);

                    for (CSVRecord record : parser) {
                        String value = record.get(targetColumn);
                        boolean matches = false;

                        if (matchString != null) {
                            matches = value.equals(matchString);
                        } else if (pattern != null) {
                            matches = pattern.matcher(value).find();
                        }

                        if (invert) {
                            matches = !matches;
                        }

                        if (matches) {
                            List<String> values = new ArrayList<>();
                            for (String header : headers) {
                                values.add(record.get(header));
                            }
                            printer.printRecord(values);
                        }
                    }

                    printer.flush();
                }
                return 0;
            }

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

    private String resolveColumn(List<String> headers) {
        // Try to parse as 1-based index
        try {
            int index = Integer.parseInt(column) - 1;
            if (index >= 0 && index < headers.size()) {
                return headers.get(index);
            }
        } catch (NumberFormatException e) {
            // Not a number, treat as column name
            if (headers.contains(column)) {
                return column;
            }
        }
        return null;
    }
}
