package org.phasanix.tabula

import java.io.{File, InputStream, InputStreamReader}
import java.nio.charset.Charset
import java.nio.file.Files

import org.apache.commons.csv.{CSVFormat, CSVParser, CSVRecord}
import org.phasanix.tabula.Tabular.Container

import collection.JavaConverters._
import reflect.runtime.universe._

/**
  * Source of tabular data from CSV files.
  */

object Csv {

  class CsvTabular(val parent: Tabular.Container, val conv: Converter[String], it: Iterator[CSVRecord], cols: Seq[String]) extends Tabular {

    def columnNames: Seq[String] = cols

    def rows: Iterator[Tabular.Row] = it.map(new CsvRow(this, _))

    def estimatedRowCount: Int = parent.fileSize.map(sz => (sz / 80).toInt).getOrElse(100)
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

  /**
    * Container for CSV files.
    * Addressing is not implemented (yet?): all CSV files are expected to start
    * with the range at the top left
    * @param config config
    * @param parser parser
    */
  class CsvContainer(config: Tabular.Config, parser: CSVParser, maybeSz: Option[Long])
    extends Tabular.Container {
    val conv: Converter[String] = Converter.makeStringConverter(config)

    private val it = parser.iterator().asScala

    private val cols = if (it.hasNext) {
      val row = it.next()
      val ret = Seq.tabulate(row.size()){row.get}
      ret
    } else {
      Seq.empty[String]
    }

    def get(address: Address): Option[Tabular] = {
      Some(new Csv.CsvTabular(this, conv, it, cols))
    }

    def close(): Unit = parser.close()

    def fileSize: Option[Long] = maybeSz
  }

  object ContainerSource extends Tabular.ContainerSource {

    val exts: Seq[String] = Seq("csv")

    def open(config: Tabular.Config, is: InputStream, ext: String, maybeSizeHint: Option[Long]): Option[Tabular.Container] = {
      if (is == null) {
        None
      } else {
        val reader = new InputStreamReader(is, config.charset)
        val parser = new CSVParser(reader, CSVFormat.DEFAULT)
        Some(new CsvContainer(config, parser, maybeSizeHint))
      }

    }

    def open(config: Tabular.Config, file: File): Option[Tabular.Container] = {
      if (file.exists()) {
        val size = Some(Files.size(file.toPath))
        val parser = CSVParser.parse(file, config.charset, CSVFormat.DEFAULT)
        Some(new CsvContainer(config, parser, size))
      } else {
        None
      }
    }
  }

}