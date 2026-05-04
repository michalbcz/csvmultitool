# AGENTS.md

This file provides guidance for AI coding agents (GitHub Copilot, OpenAI Codex, etc.) working in this repository.

## Project Summary

**csvmultitool** is a Java 17 command-line application that reimplements the most popular [csvkit](https://csvkit.readthedocs.io/) commands with higher performance, especially for large Excel files. It uses [picocli](https://picocli.info/) for CLI argument parsing, [Apache POI](https://poi.apache.org/) for Excel reading, and [FastCSV](https://github.com/osiegmar/FastCSV) / [Apache Commons CSV](https://commons.apache.org/proper/commons-csv/) for CSV I/O.

## Repository Layout

```
csvmultitool/
├── pom.xml                          # Maven build (Java 17, fat JAR via maven-shade-plugin)
├── csvmultitool                     # Bash wrapper script
├── README.md                        # End-user documentation
├── AGENTS.md                        # This file
├── benchmark_tool/
│   └── run_benchmark.py             # Benchmarks this tool vs Python csvkit
└── src/main/java/org/example/
    ├── CsvMultitool.java            # Main entry point; picocli root @Command
    └── commands/
        ├── In2CsvCommand.java       # in2csv  – Excel → CSV
        ├── CsvCutCommand.java       # csvcut  – column selection
        ├── CsvLookCommand.java      # csvlook – pretty-print table
        ├── CsvSortCommand.java      # csvsort – sort rows
        ├── CsvGrepCommand.java      # csvgrep – filter rows
        ├── CsvStatCommand.java      # csvstat – summary statistics
        └── CsvStackCommand.java     # csvstack – merge CSV files
```

## Build

```bash
mvn clean package
```

Produces `target/csvmultitool-1.0-SNAPSHOT.jar` (fat JAR with all dependencies).

## Run

```bash
# Via wrapper script
./csvmultitool <command> [options]

# Or directly
java -jar target/csvmultitool-1.0-SNAPSHOT.jar <command> [options]
```

## Tests

There are **no automated tests** in this repository. When making changes, manually verify behaviour by running the tool against sample CSV or Excel files.

## Architecture Notes

- **Entry point:** `CsvMultitool.main()` delegates entirely to picocli's `CommandLine`. Each subcommand is a separate class implementing `Callable<Integer>`.
- **Subcommand pattern:** Every command class carries a `@Command` annotation. Declare new commands in `CsvMultitool.java`'s `subcommands` array.
- **Input handling:** All CSV commands accept a file path **or** `-` (stdin). Use a `getReader()` helper method (see existing commands for the pattern).
- **Output:** Commands write to `System.out` and errors to `System.err`. Return `0` on success, `1` on error.
- **Excel formats:**
  - `.xlsx` (OOXML) — full support via `xlsx-streamer` (streaming, memory-efficient).
  - `.xls` (OLE2 / Excel 97) — sheet listing only via POI event API. Full conversion is **not yet implemented**.
- **`CsvMultitool.java` contains legacy dead code** mixed with the current picocli version (leftover from an earlier standalone implementation). Do not rely on the legacy methods; use the `commands/` classes.
- **`IOUtils.setByteArrayMaxOverride(500_000_000)`** is set in `In2CsvCommand` to handle large Excel files with Apache POI.

## Key Conventions

- Java 17 features (switch expressions, text blocks) are acceptable.
- No external configuration files; all options are CLI flags.
- Column indices throughout the CLI are **1-based** (consistent with csvkit).
- Sheet indices for `in2csv -s` are **0-based**.
- The `split = ","` attribute on picocli `@Option` splits comma-separated values automatically (see `CsvCutCommand`).

## Known Issues / Limitations

- `.xls` → CSV conversion is not implemented. The `In2CsvCommand` returns exit code `1` with an error message if conversion of `.xls` is attempted.
- `CsvSortCommand` loads all rows into memory before sorting; not suitable for very large files without increasing JVM heap (`-Xmx`).
- `CsvMultitool.java` has duplicate/dead code from a previous iteration — avoid editing the legacy block.

## Adding a New Command

1. Create `src/main/java/org/example/commands/CsvXxxCommand.java` implementing `Callable<Integer>` with a `@Command(name = "csvxxx", ...)` annotation.
2. Add `CsvXxxCommand.class` to the `subcommands` array in `CsvMultitool.java`.
3. Follow the `getReader()` pattern for stdin/file input.
4. Write to `System.out`; errors to `System.err`; return `0`/`1`.
5. Update `README.md` with usage examples.
