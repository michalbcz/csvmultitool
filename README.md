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

Let's list all the sheet names:
```
$ time in2csv -n cetnost-prijmeni.xls
c_po_1-PŘÍJM
c_po_2-GELTN
c_po_3-KUBÁN
c_po_4-PREKS
c_po_5-VALAC

real    2m13.304s
user    0m0.000s
sys     0m0.000s
```

You see? It works but merely listing of sheet names takes slightly more than 2 minutes.

So then I started to be curious. csvkit is written in Python so as a Java Developer by daylight I thought:
"What about to use Java libraries for that. Will it be faster? How faster?"

