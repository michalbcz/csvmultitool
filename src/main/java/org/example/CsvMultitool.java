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
        CsvStackCommand.class
    }
)
public class CsvMultitool {

    /**
     * 64KB buffer size chosen to optimize for typical SSD block sizes
     * and maximize I/O throughput when reading and writing large CSV/Excel files.
     */
    public static final int BUFFER_SIZE = 65536;

    public static void main(String[] args) {
        int exitCode = new CommandLine(new CsvMultitool()).execute(args);
        System.exit(exitCode);
    }
}