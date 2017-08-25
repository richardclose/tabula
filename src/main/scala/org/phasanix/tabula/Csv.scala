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
    * before that address are added to a mutable buffer. Rows up to and including
    * the highest specified row are buffered. Rows after that point are read directly
    * from the forward-only CSVParser iterator. This scheme will fail if get is called
    * with an address referring to a row greater than rows that are read unbuffered.
    *
    * @param config config
    * @param parser parser
    */
  class CsvContainer(config: Tabular.Config, parser: CSVParser, maybeSz: Option[Long])
    extends Tabular.Container {
    val conv: Converter[String] = Converter.makeStringConverter(config)

    private val buffer = collection.mutable.ArrayBuffer.empty[CSVRecord]

    private var iteratorUsed: Boolean = false

    // 1) Selects buffered vs direct iteration
    // 2) Terminates at completely blank row
    private class It(row: Int) extends Iterator[CSVRecord] {

      private val usingBuffer = row < buffer.length

      private lazy val resolvedIterator = (if (row < buffer.length) {
        buffer.iterator.drop(row)
      } else {
        if (row != parser.getCurrentLineNumber)
          throw new Exception(s"iterator out of position for row=$row, currentLine=${parser.getCurrentLineNumber}")
        parser.iterator().asScala
      }).buffered

      def hasNext: Boolean = {
        if (resolvedIterator.hasNext) {
          val row = resolvedIterator.head
          val isRowEmpty = row.iterator().asScala.forall(_.trim.isEmpty)
          !isRowEmpty
        } else {
          false
        }
      }

      def next(): CSVRecord = {
        iteratorUsed ||= !usingBuffer
        resolvedIterator.next()
      }
    }

    def get(address: Address): Option[Tabular] = {
      address match {
        case CsvAddress(row, col) =>

          if (iteratorUsed && row >= buffer.length)
            throw new Exception(s"Illegal call to get($address) after direct iteration of rows")

          val (it, maybeRec) = if (row < buffer.length) {
            val i = buffer.iterator.drop(row)
            val rec = if (i.hasNext) Some(i.next()) else None
            (i, rec)
          } else {
            val i = parser.iterator().asScala
            buffer.appendAll(i.take(row - buffer.length))
            val rec = if (i.hasNext) Some(i.next()) else None
            rec.foreach(buffer.append(_))
            (i, rec)
          }

          val cols = maybeRec
            .map(_.iterator().asScala.toIndexedSeq)
            .getOrElse(Seq.empty)

          if (col > cols.length) {
            None
          } else {
            Some(new Csv.CsvTabular(this, conv, new It(row + 1), cols.drop(col), col))
          }

        case NilAddress =>
          get(CsvAddress(0, 0))

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