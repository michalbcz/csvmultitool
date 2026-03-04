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
--- Summary ---
Python (csvkit): 155.851s
Java (csvmultitool): 8.207s
Java is 18.99x faster
---------------
```

You see? The Java implementation using Apache POI is significantly faster, doing the job in a fraction of the time.

