# CSV Multitool

[![Java](https://img.shields.io/badge/java-17%2B-blue)](https://adoptium.net/)
[![Build](https://img.shields.io/badge/build-maven-orange)](https://maven.apache.org/)

A high-performance Java implementation of [csvkit](https://csvkit.readthedocs.io/)'s most popular features. Provides fast command-line utilities for working with CSV and Excel files.

## Table of Contents

- [Features](#features)
- [Requirements](#requirements)
- [Installation](#installation)
- [Usage](#usage)
  - [in2csv – Excel to CSV](#in2csv--excel-to-csv)
  - [csvcut – Column Selection](#csvcut--column-selection)
  - [csvlook – Pretty Print](#csvlook--pretty-print)
  - [csvsort – Sort Data](#csvsort--sort-data)
  - [csvgrep – Filter Rows](#csvgrep--filter-rows)
  - [csvstat – Statistics](#csvstat--statistics)
  - [csvstack – Combine Files](#csvstack--combine-files)
  - [Composing Commands](#composing-commands)
- [Known Limitations](#known-limitations)
- [Performance](#performance)
- [Background Story](#background-story)
- [Contributing](#contributing)

## Features

- Convert Excel (`.xls` / `.xlsx`) files to CSV
- Select, reorder, or exclude columns
- Pretty-print CSV as a table
- Sort rows by one or more columns
- Filter rows by exact match or regular expression
- Generate per-column summary statistics
- Stack (vertically merge) multiple CSV files
- Reads from files or standard input; writes to standard output — pipes naturally into other tools

## Requirements

- Java 17 or higher
- Maven 3.6 or higher (to build from source)

## Installation

Clone the repository and build a self-contained fat JAR:

```bash
git clone https://github.com/michalbcz/csvmultitool.git
cd csvmultitool
mvn clean package
```

The JAR is produced at `target/csvmultitool-1.0-SNAPSHOT.jar`.

A convenience wrapper script is included at the repository root:

```bash
# Make it executable (once)
chmod +x csvmultitool

# Run any command through the wrapper
./csvmultitool in2csv -n file.xlsx
```

Alternatively, invoke the JAR directly:

```bash
java -jar target/csvmultitool-1.0-SNAPSHOT.jar <command> [options]
```

## Usage

All examples below use the `./csvmultitool` wrapper. Replace it with
`java -jar target/csvmultitool-1.0-SNAPSHOT.jar` if you prefer.

### in2csv – Excel to CSV

Convert Excel files (`.xlsx` fully supported; `.xls` sheet listing only — see [Known Limitations](#known-limitations)).

```bash
# List sheet names
./csvmultitool in2csv -n file.xlsx

# Convert first sheet to CSV
./csvmultitool in2csv file.xlsx > output.csv

# Convert a specific sheet by name
./csvmultitool in2csv -s "Sheet2" file.xlsx > output.csv

# Convert a specific sheet by 0-based index
./csvmultitool in2csv -s 0 file.xlsx > output.csv
```

### csvcut – Column Selection

Select, reorder, or exclude columns from CSV files.

```bash
# Display column names and indices
./csvmultitool csvcut -n data.csv

# Select specific columns by name
./csvmultitool csvcut -c name,age data.csv

# Select columns by 1-based index
./csvmultitool csvcut -c 1,3,5 data.csv

# Exclude columns
./csvmultitool csvcut -C city data.csv
```

### csvlook – Pretty Print

Display CSV data in a readable table format.

```bash
./csvmultitool csvlook data.csv
```

### csvsort – Sort Data

Sort CSV data by one or more columns.

```bash
# Sort by a single column
./csvmultitool csvsort -c age data.csv

# Sort by multiple columns
./csvmultitool csvsort -c age,name data.csv

# Sort in descending order
./csvmultitool csvsort -c salary -r data.csv
```

### csvgrep – Filter Rows

Filter rows based on pattern matching.

```bash
# Match an exact string
./csvmultitool csvgrep -c city -m "NYC" data.csv

# Match a regular expression
./csvmultitool csvgrep -c name -r "^J" data.csv

# Invert match (select non-matching rows)
./csvmultitool csvgrep -c status -m "active" -i data.csv
```

### csvstat – Statistics

Generate summary statistics for each column.

```bash
./csvmultitool csvstat data.csv
```

Output per column:

| Field | Numeric columns | Text columns |
|---|---|---|
| Data type | Number | Text |
| Null count | ✓ | ✓ |
| Unique value count | ✓ | ✓ |
| Min / Max / Mean | ✓ | — |
| Max length | — | ✓ |
| Sample values | if ≤ 5 unique values | if ≤ 5 unique values |

### csvstack – Combine Files

Stack multiple CSV files vertically (append rows).

```bash
./csvmultitool csvstack file1.csv file2.csv file3.csv > combined.csv
```

### Composing Commands

All CSV commands accept `-` (or no file argument) to read from standard input, making them composable:

```bash
# Convert Excel, keep two columns, pretty-print result
./csvmultitool in2csv data.xlsx \
  | ./csvmultitool csvcut -c name,salary \
  | ./csvmultitool csvsort -c salary -r \
  | ./csvmultitool csvlook
```

## Known Limitations

- **`.xls` (Excel 97–2003) conversion to CSV is not yet implemented.** The `in2csv` command can list sheet names from `.xls` files (`-n` flag) but cannot convert them to CSV. Convert to `.xlsx` first, or use [csvkit](https://csvkit.readthedocs.io/) for `.xls` conversion.
- `csvsort` loads the entire file into memory; very large files may require increasing the JVM heap with `-Xmx`.

## Performance

This Java implementation is significantly faster than Python's csvkit for large Excel files, particularly for the sheet-listing operation on Excel 97 (`.xls`) files.

Benchmark on a Czech Ministry of Interior surname dataset (~450 MB unzipped `.xls`):

```
--- Summary ---
Python (csvkit): 155.851s
Java (csvmultitool): 8.207s
Java is 18.99x faster
---------------
```

**Hardware:** Linux · Intel(R) Xeon(R) @ 2.30 GHz · 7.77 GB RAM

To reproduce the benchmark:

```bash
pip install csvkit
cd benchmark_tool
python run_benchmark.py
```

## Background Story

It started with curiosity about surname counts by county in the Czech Republic.
The data came from the [Czech Ministry of Interior](https://web.archive.org/web/20180210214901/https://www.mvcr.cz/clanek/cetnost-jmen-a-prijmeni.aspx) — archived at archive.org after the original was taken down for GDPR reasons.

The dataset is a collection of zipped Excel 97 files (~450 MB unzipped). Each file uses multiple sheets because Excel 97 rows are capped at 2^16 (65 536) rows per sheet.

[csvkit](https://csvkit.readthedocs.io/) is the standard tool for this job, but it took over 2 minutes just to list the sheet names. As a Java developer, that raised an obvious question: *how much faster could a Java implementation be?*

The answer: about **19×** faster — which is how this project started.

## Contributing

Contributions are welcome! To get started:

1. Fork the repository and create a feature branch.
2. Make your changes and ensure `mvn clean package` succeeds.
3. Open a pull request with a clear description of the change.

Please report bugs and request features via [GitHub Issues](https://github.com/michalbcz/csvmultitool/issues).

