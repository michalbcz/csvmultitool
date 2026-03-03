package org.example.commands;

import org.apache.commons.csv.*;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.io.*;
import java.util.*;
import java.util.concurrent.Callable;

@Command(name = "csvstack", description = "Stack multiple CSV files vertically")
public class CsvStackCommand implements Callable<Integer> {

    @Parameters(description = "CSV files to stack", arity = "1..*")
    private List<String> inputFiles;

    @Override
    public Integer call() throws Exception {
        try {
            if (inputFiles == null || inputFiles.isEmpty()) {
                System.err.println("Error: At least one input file required");
                return 1;
            }

            List<String> commonHeaders = null;
            CSVPrinter printer = null;

            for (String inputFile : inputFiles) {
                try (Reader reader = new FileReader(new File(inputFile));
                     CSVParser parser = CSVFormat.DEFAULT.withFirstRecordAsHeader().parse(reader)) {

                    List<String> headers = new ArrayList<>(parser.getHeaderMap().keySet());

                    // First file establishes the headers
                    if (commonHeaders == null) {
                        commonHeaders = headers;
                        printer = new CSVPrinter(System.out, CSVFormat.DEFAULT);
                        printer.printRecord(commonHeaders);
                    } else {
                        // Verify headers match
                        if (!headers.equals(commonHeaders)) {
                            System.err.println("Warning: Headers don't match in file: " + inputFile);
                            System.err.println("Expected: " + commonHeaders);
                            System.err.println("Found: " + headers);
                        }
                    }

                    // Copy all records
                    for (CSVRecord record : parser) {
                        List<String> values = new ArrayList<>();
                        for (String header : commonHeaders) {
                            try {
                                values.add(record.get(header));
                            } catch (IllegalArgumentException e) {
                                // Column doesn't exist in this file
                                values.add("");
                            }
                        }
                        printer.printRecord(values);
                    }
                }
            }

            if (printer != null) {
                printer.flush();
            }

            return 0;

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            return 1;
        }
    }
}
