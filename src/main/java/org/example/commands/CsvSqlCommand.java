package org.example.commands;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.File;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.io.Reader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

@Command(name = "csvsql", description = "Generate SQL statements for a CSV file or execute those statements directly on a database.")
public class CsvSqlCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "CSV file to process", arity = "0..1")
    private String inputFile = "-";

    @Option(names = {"--query"}, description = "Execute one or more SQL queries delimited by ';' and output the result of the last one as CSV.")
    private String query;

    @Override
    public Integer call() throws Exception {
        try {
            Class.forName("org.h2.Driver");
            try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:csvsql;DB_CLOSE_DELAY=-1", "sa", "");
                 Reader reader = getReader()) {

                String tableName = "csvdata";
                if (!"-".equals(inputFile)) {
                    tableName = new File(inputFile).getName().replace(".csv", "").replaceAll("[^a-zA-Z0-9_]", "_");
                    if (tableName.isEmpty() || Character.isDigit(tableName.charAt(0))) {
                        tableName = "t_" + tableName;
                    }
                }

                try (CSVParser parser = CSVFormat.DEFAULT.withFirstRecordAsHeader().parse(reader)) {
                    List<String> headerNames = parser.getHeaderNames();
                    if (headerNames == null || headerNames.isEmpty()) {
                        System.err.println("Error: Empty CSV or no headers found.");
                        return 1;
                    }

                    // Create table
                    StringBuilder createTableSql = new StringBuilder("CREATE TABLE ").append(tableName).append(" (");
                    List<String> columnNames = new ArrayList<>();
                    for (int i = 0; i < headerNames.size(); i++) {
                        String rawColName = headerNames.get(i);
                        String colName = (rawColName == null || rawColName.trim().isEmpty()) ? "column_" + (i + 1) : rawColName.trim().replaceAll("[^a-zA-Z0-9_]", "_");
                        if (Character.isDigit(colName.charAt(0))) {
                            colName = "c_" + colName;
                        }
                        createTableSql.append(colName).append(" VARCHAR");
                        if (i < headerNames.size() - 1) {
                            createTableSql.append(", ");
                        }
                        columnNames.add(colName);
                    }
                    createTableSql.append(")");

                    try (Statement stmt = conn.createStatement()) {
                        stmt.execute(createTableSql.toString());
                    }

                    // Insert data
                    StringBuilder insertSql = new StringBuilder("INSERT INTO ").append(tableName).append(" VALUES (");
                    for (int i = 0; i < columnNames.size(); i++) {
                        insertSql.append("?");
                        if (i < columnNames.size() - 1) {
                            insertSql.append(", ");
                        }
                    }
                    insertSql.append(")");

                    try (PreparedStatement pstmt = conn.prepareStatement(insertSql.toString())) {
                        for (CSVRecord record : parser) {
                            for (int i = 0; i < columnNames.size(); i++) {
                                if (i < record.size()) {
                                    pstmt.setString(i + 1, record.get(i));
                                } else {
                                    pstmt.setNull(i + 1, java.sql.Types.VARCHAR);
                                }
                            }
                            pstmt.addBatch();
                        }
                        pstmt.executeBatch();
                    }
                }

                                // If query is provided, execute and output as CSV
                if (query != null && !query.trim().isEmpty()) {
                    try (Statement stmt = conn.createStatement()) {
                        boolean isResultSet = stmt.execute(query);

                        while (true) {
                            if (isResultSet) {
                                try (ResultSet rs = stmt.getResultSet();
                                     CSVPrinter printer = new CSVPrinter(System.out, CSVFormat.DEFAULT)) {

                                    ResultSetMetaData rsmd = rs.getMetaData();
                                    int columnCount = rsmd.getColumnCount();

                                    List<String> headers = new ArrayList<>();
                                    for (int c = 1; c <= columnCount; c++) {
                                        headers.add(rsmd.getColumnLabel(c));
                                    }
                                    printer.printRecord(headers);

                                    while (rs.next()) {
                                        List<String> row = new ArrayList<>();
                                        for (int c = 1; c <= columnCount; c++) {
                                            String val = rs.getString(c);
                                            row.add(val == null ? "" : val);
                                        }
                                        printer.printRecord(row);
                                    }
                                }
                            } else {
                                if (stmt.getUpdateCount() == -1) {
                                    break;
                                }
                            }
                            isResultSet = stmt.getMoreResults();
                        }
                    }
                } else {
                    System.out.println("No query provided. Data loaded into table: " + tableName);
                }
            }
            return 0;
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            return 1;
        }
    }

    private Reader getReader() throws Exception {
        if ("-".equals(inputFile)) {
            return new InputStreamReader(System.in);
        } else {
            File file = new File(inputFile);
            if (!file.exists()) {
                throw new Exception("File not found: " + inputFile);
            }
            return new FileReader(file);
        }
    }
}
