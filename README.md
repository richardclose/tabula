# tabula
Uniform access to, and addressing of, tabular data in spreadsheets. CSV, XLS, XLSX.

My use case is a system that receives uploaded data in several different formats. The data may
be CSV files, or it may be an Excel workbook containing several tables which are addressed by
sheet/row/column or a named range.

Where cell addresses are used in Excel, `Tabular` will take the top-left cell of the range
identified by the address. The contiguous range of non-blank cells to the right of this cell
are the headers, and the contiguous range of non-blank rows (of the same width as the headers)
are the rows.

## Examples

The following examples assume test files with appropriate contents.

### Read data from a CSV file

```scala
val config = Tabular.config(dateFmtStr = "dd/MMM/yyyy", dateTimeFmtStr = "dd/MMM/yyyy HH:mm:ss")

val maybeRows: Option[Seq[(Int, String)]] = for {
  container <- Tabular.open("/tmp/data.csv")
  tab <- src.get(NilAddress)
} yield {
    (for {
      row <- tab.rows
      col1 <- row.get[Int]("an int")
      col2 <- row.get[String]("a string")
    } yield (col1, col2)).toIndexedSeq
}

```

### Address a named range in an Excel workbook

```scala
for {
  container <- Tabular.open("/tmp/data.xlsx")
  tab <- src.get(Tabular.parseAddress("TheNamedRange", "xlsx"))
} {
  /// Do something with the range
}
```

### Address a cell by cell reference

```scala
for {
  container <- Tabular.open("/tmp/data.xlsx")
  tab <- src.get(Tabular.parseAddress("Sheet2!C5", "xlsx"))
} {
  /// Do something with the range
}

```

### Signatures

`Signature` abstracts the process of sniffing the content of a file to check if 
the content matches what is expected. My use case for this is a system that accepts
files with a set of possible formats, and uses the `Signature` to determine how 
to process them. Two implementations of `Signature` are provided: 
`Signature.MatchHeaders` has a list of column header names that must be present
for a match to succeed, and `Signature.MatchSheetNames` has a list of worksheet
names.

```scala
for {
  container <- Tabular.open("/tmp/data.csv")
} {
  if (Signature.matchHeaders("Latitude", "Longitude").matches(container)) {
    // It's the one!
  }
}
```