package org.phasanix.tabula

import java.io.{File, InputStream, InputStreamReader}
import java.nio.file.Files

import org.apache.commons.csv.{CSVFormat, CSVParser, CSVRecord}

import scala.collection.JavaConverters._
import scala.reflect.runtime.universe._

/**
  * Source of tabular data from CSV files.
  */

object Csv {

  class CsvTabular(val parent: Tabular.Container,
                   val conv: Converter[String],
                   it: Iterator[CSVRecord],
                   cols: Seq[String],
                   val colOffset: Int) extends Tabular {

    def columnNames: Seq[String] = cols

    def rows: Iterator[Tabular.Row] = it.map(new CsvRow(this, _))

    def estimatedRowCount: Int = parent.fileSize.map(sz => (sz / 80).toInt).getOrElse(100)
  }

  class CsvRow(val parent: CsvTabular, rec: CSVRecord) extends Tabular.Row {

    def get[D: TypeTag](index: Int): Option[D] = {
      if (index < 0 || index >= columnCount) {
        None
      } else {
        parent.conv.convert[D](rec.get(index + parent.colOffset))
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

    private lazy val cols = if (it.hasNext) {
      val row = it.next()
      val ret = Seq.tabulate(row.size()){row.get}
      ret
    } else {
      Seq.empty[String]
    }

    def get(address: Address): Option[Tabular] = {
      address match {
        case CsvAddress(row, col) =>
          val discarded = it.take(row).map(r => r.iterator().asScala.toIndexedSeq).toIndexedSeq
          val theCols = cols
          if (col > theCols.length)
            None
          else {
            val c = cols.drop(col)
            Some(new Csv.CsvTabular(this, conv, it, cols.drop(col), col))
          }

        case NilAddress =>
          Some(new Csv.CsvTabular(this, conv, it, cols, 0))

        case _ =>
          None
      }
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

    override def parseAddress(addr: String): Address = {
      val matchAddr = raw"([A-Z]{1,2})([0-9]{1,5})".r
      addr match  {
        case matchAddr(scol,srow) =>
          val col = scol.foldLeft(0) {(acc, ch) => acc * 26 + (1 + ch - 'A'.toInt) } - 1
          CsvAddress(srow.toInt - 1, col)

        case _ =>
          NilAddress
      }
    }
  }

  case class CsvAddress(row: Int, col: Int) extends Address

}