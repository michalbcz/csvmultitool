# CSV Multitool

A high-performance Java implementation of csvkit's most popular features. Inspired by the Python csvkit library, this tool provides fast command-line utilities for working with CSV and Excel files.

## Features

This tool implements the most commonly used csvkit commands:

### in2csv - Excel to CSV Converter
Convert Excel files (both .xls and .xlsx) to CSV format.

```bash
# List sheet names
java -jar csvmultitool.jar in2csv -n file.xlsx

# Convert first sheet to CSV
java -jar csvmultitool.jar in2csv file.xlsx > output.csv

# Convert specific sheet by name
java -jar csvmultitool.jar in2csv -s "Sheet2" file.xlsx > output.csv
```

### csvcut - Column Selection
Select, reorder, or exclude columns from CSV files.

```bash
# Display column names and indices
java -jar csvmultitool.jar csvcut -n data.csv

# Select specific columns
java -jar csvmultitool.jar csvcut -c name,age data.csv

# Select columns by index (1-based)
java -jar csvmultitool.jar csvcut -c 1,3,5 data.csv

# Exclude columns
java -jar csvmultitool.jar csvcut -C city data.csv
```

### csvlook - Pretty Print
Display CSV data in a readable table format.

```bash
java -jar csvmultitool.jar csvlook data.csv
```

### csvsort - Sort Data
Sort CSV data by one or more columns.

```bash
# Sort by single column
java -jar csvmultitool.jar csvsort -c age data.csv

# Sort by multiple columns
java -jar csvmultitool.jar csvsort -c age,name data.csv

# Sort in descending order
java -jar csvmultitool.jar csvsort -c salary -r data.csv
```

### csvgrep - Filter Rows
Filter rows based on pattern matching.

```bash
# Match exact string
java -jar csvmultitool.jar csvgrep -c city -m "NYC" data.csv

# Match regular expression
java -jar csvmultitool.jar csvgrep -c name -r "^J" data.csv

# Invert match (select non-matching rows)
java -jar csvmultitool.jar csvgrep -c status -m "active" -i data.csv
```

### csvstat - Statistics
Generate summary statistics for each column.

```bash
java -jar csvmultitool.jar csvstat data.csv
```

Displays:
- Data type (Number/Text)
- Null count
- Unique value count
- Min/Max/Mean (for numeric columns)
- Max length (for text columns)
- Sample values (if unique count ≤ 5)

### csvstack - Combine Files
Stack multiple CSV files vertically (append rows).

```bash
java -jar csvmultitool.jar csvstack file1.csv file2.csv file3.csv > combined.csv
```

## Building

```bash
mvn clean package
```

This creates a fat JAR with all dependencies at `target/csvmultitool-1.0-SNAPSHOT.jar`.

## Requirements

- Java 17 or higher
- Maven 3.6 or higher (for building)

## Performance

This Java implementation provides significantly faster performance compared to the Python csvkit, especially for large Excel files. The original Python implementation took over 2 minutes to list sheet names from a large Excel file, while this Java version completes the same operation in seconds.

## Original Story

# How this starts?

It was typical case of falling through white rabbit hole.
First I was wondering about some surname count by counties in Czech Republic.

There exists two webs for that:
1. prijmeni.cz
2. kdejsme.cz

Then I was curious what is the source of the data and why there is stated that data are from 2016.

Data came from [Czech Minstry of Interior ](https://web.archive.org/web/20180210214901/https://www.mvcr.cz/clanek/cetnost-jmen-a-prijmeni.aspx).
Past tense because it was considered that such data are violation of privacy (GDPR).

Thanks god for archive.org which archives even files (together it has around 30MBs). Incredible, since then I am their supporter.

Data are zipped Excel97 (M$ binary proprietary format) files. Unzipped size is ~450 MB. Data are separated by sheets because in Excel97 sheets have limit of maximum number of rows
2^16 (65536) rows.

I wanted to just easily convert it to CSV. I found recommended tool for that - https://csvkit.readthedocs.io/en/latest/

So I started to be curious. csvkit is written in Python so as a Java Developer by daylight I thought:
"What about to use Java libraries for that. Will it be faster? How faster?"

To find out, I created a small benchmarking script (`benchmark_tool/run_benchmark.py`) that compares `csvkit` (`in2csv`) with this Java implementation on the `cetnost-prijmeni.xls` file.

**Hardware used for the benchmark:**
* **OS**: Linux
* **CPU Model**: Intel(R) Xeon(R) Processor @ 2.30GHz
* **RAM**: 7.77 GB

Here are the results of simply listing the sheet names:

```
--- Summary: Sheet Listing ---
Python (csvkit): 140.496s
Java (csvmultitool): 8.536s
Java is 16.46x faster
------------------------------

--- Summary: Full Extraction ---
Python (csvkit): 101.599s
Java (csvmultitool): 22.350s
Java is 4.55x faster
--------------------------------
```

You see? The Java implementation using Apache POI is significantly faster, doing the job in a fraction of the time.

