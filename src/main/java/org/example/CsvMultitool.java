package org.example;

import org.example.commands.*;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(
    name = "csvmultitool",
    description = "A suite of command-line tools for working with CSV files",
    mixinStandardHelpOptions = true,
    version = "1.0",
    subcommands = {
        In2CsvCommand.class,
        CsvCutCommand.class,
        CsvLookCommand.class,
        CsvSortCommand.class,
        CsvGrepCommand.class,
        CsvStatCommand.class,
        CsvStackCommand.class,
        CsvSqlCommand.class
    }
)
public class CsvMultitool {
    public static void main(String[] args) {
        int exitCode = new CommandLine(new CsvMultitool()).execute(args);
        System.exit(exitCode);
    }
}
