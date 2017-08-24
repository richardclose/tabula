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
    * with the range at the top left.
    *
    * Implementation note: Apache CSVParser is necessarily forward only. To support
    * reading of multiple ranges, when get is called with a CsvAddress, all rows
    * before that address are added to a mutable buffer. For this scheme to work
    * correctly, calls to get(CsvAddress) should be made in reverse order of row
    * number, and before any rows are read, and ranges cannot overlap rows.
    * NilAddress and CsvAddress can't be used together.
    *
    * @param config config
    * @param parser parser
    */
  class CsvContainer(config: Tabular.Config, parser: CSVParser, maybeSz: Option[Long])
    extends Tabular.Container {
    val conv: Converter[String] = Converter.makeStringConverter(config)

    private val buffer = collection.mutable.ArrayBuffer.empty[CSVRecord]
    private val colsForAddress = collection.concurrent.TrieMap.empty[String, Seq[String]]

    private def getCols(address: Address, it: Iterator[CSVRecord]) = {
      colsForAddress.getOrElseUpdate(address.toString, {
        if (it.hasNext) {
          val rec = it.next()
          rec.iterator().asScala.toIndexedSeq
        } else {
          Seq.empty
        }
      })
    }

    def get(address: Address): Option[Tabular] = {
      address match {
        case CsvAddress(row, col) =>

          val it = if (row > buffer.length) {
            val i = parser.iterator().asScala
            buffer.appendAll(i.take(row - buffer.length))
            i
          } else {
            buffer.iterator.drop(row)
          }

          val cols = getCols(address, it)

          if (col > cols.length)
            None
          else {
            val c = cols.drop(col)
            Some(new Csv.CsvTabular(this, conv, it, cols.drop(col), col))
          }

        case NilAddress =>
          val it = parser.iterator().asScala
          Some(new Csv.CsvTabular(this, conv, it, getCols(address, it), 0))

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

  case class CsvAddress(row: Int, col: Int) extends Address {
    override def toString: String = {
      s"R${row+1}C${col+1}"
    }
  }

}