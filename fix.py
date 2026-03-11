import re

with open("src/main/java/org/example/commands/CsvSqlCommand.java", "r") as f:
    text = f.read()

# Let's fix the regex to correctly replace the `if (query != null` block.
import textwrap

replacement = """                // If query is provided, execute and output as CSV
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
                }"""

# A simpler search string to replace the old block
start = text.find("// If query is provided, execute and output as CSV")
end = text.find("} else {", start)
end = text.find("}", text.find("System.out.println", end)) + 1

if start != -1 and end != -1:
    new_text = text[:start] + replacement + text[end:]
    with open("src/main/java/org/example/commands/CsvSqlCommand.java", "w") as f:
        f.write(new_text)
else:
    print("Could not find the block to replace!")
