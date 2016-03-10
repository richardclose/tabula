package org.phasanix.tabula

import java.io.{InputStream, File, InputStreamReader}
import java.nio.charset.Charset

import org.apache.commons.csv.{CSVFormat, CSVRecord, CSVParser}
import collection.JavaConverters._

import reflect.runtime.universe._

/**
  * Source of tabular data from CSV files.
  */

object Csv {

  class CsvTabular(val parent: Tabular.Container, val conv: Converter[String], parser: CSVParser) extends Tabular {

    private val it = parser.iterator().asScala
    private val cols: Seq[String] = if (it.hasNext) {
      val row = it.next()
      Array.tabulate(row.size()){row.get}
    } else {
      Seq.empty
    }

    def columnNames: Seq[String] = cols

    def rows: Iterator[Tabular.Row] = it.map(new CsvRow(this, _))
  }

  class CsvRow(val parent: CsvTabular, rec: CSVRecord) extends Tabular.Row {

    def get[D: TypeTag](index: Int): Option[D] = {
      if (index < 0 || index >= columnCount) {
        None
      } else {
        parent.conv.convert[D](rec.get(index))
      }
    }

  }

  class CsvContainer(config: Tabular.Config, parser: CSVParser) extends Tabular.Container {
    val conv: Converter[String] = Converter.makeStringConverter(config)

    def get(address: Address): Option[Tabular] = {
      Some(new Csv.CsvTabular(this, conv, parser))
    }

    def close(): Unit = parser.close()
  }

  object ContainerSource extends Tabular.ContainerSource {

    val exts: Seq[String] = Seq("csv")

    def open(config: Tabular.Config, is: InputStream, ext: String): Option[Tabular.Container] = {
      if (is == null) {
        None
      } else {
        val reader = new InputStreamReader(is)
        val parser = new CSVParser(reader, CSVFormat.DEFAULT)
        Some(new CsvContainer(config, parser))
      }

    }

    def open(config: Tabular.Config, file: File): Option[Tabular.Container] = {
      if (file.exists()) {
        val parser = CSVParser.parse(file, Charset.forName("UTF-8"), CSVFormat.DEFAULT.withHeader())
        Some(new CsvContainer(config, parser))
      } else {
        None
      }
    }
  }

}