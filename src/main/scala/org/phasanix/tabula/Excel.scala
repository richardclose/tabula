package org.phasanix.tabula

import java.io.{File, FileInputStream, InputStream}
import java.nio.file.Files
import java.time.{LocalDate, LocalDateTime, ZoneId, ZonedDateTime}

import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.openxml4j.opc.OPCPackage
import org.apache.poi.ss.usermodel._
import org.apache.poi.xssf.usermodel.XSSFWorkbook

import scala.collection.mutable
import scala.reflect.runtime.universe._

object Excel {

  class ExcelTabular(val parent: Tabular.Container, val conv: ExcelConverter, headers: Seq[String], startCell: Cell)
    extends Tabular {

    def columnNames: Seq[String] = headers

    val rows = new Iterator[Tabular.Row] {

      val startCol = startCell.getColumnIndex
      val endCol = startCol + headers.length
      val sheet = startCell.getSheet
      var currentRow = startCell.getRowIndex + 1

      def hasNext: Boolean = !isRowBlank(sheet, currentRow, startCol, endCol)

      def next(): Tabular.Row = {
        val r = new ExcelRow(ExcelTabular.this, sheet.getRow(currentRow), startCol)
        currentRow += 1
        r
      }
    }

    def estimatedRowCount: Int = {
      var ret = 0
      var row = startCell.getRowIndex + 1
      val col = startCell.getColumnIndex
      val endCol = col + headers.length
      var sheet = startCell.getSheet
      var done = false
      while (!done) {
        if (isRowBlank(sheet, row, col, endCol)) {
          done = true
        } else {
          ret += 1
          row += 1
        }
      }

      ret
    }

  }

  class ExcelRow(val parent: ExcelTabular, row: Row, startCol: Int) extends Tabular.Row {
    def get[A: TypeTag](index: Int): Option[A] = {
      parent.conv.convert[A](row.getCell(index))
    }

  }

  abstract class ExcelContainer(config: Tabular.Config, maybeSz: Option[Long])
    extends Tabular.Container {

    private val openTabs: mutable.Set[ExcelTabular] = mutable.Set.empty[ExcelTabular]
    protected val conv = new ExcelConverter(config)

    def close(): Unit = workbook.close()

    def removeTab(tab: ExcelTabular): Unit = {}

    val workbook: Workbook

    def sheetNames: Seq[String] = {
      (0 until workbook.getNumberOfSheets).map(i => workbook.getSheetName(i))
    }

    def fileSize: Option[Long] = maybeSz
  }

  class XlsContainer(config: Tabular.Config, input: Either[File, InputStream], maybeSz: Option[Long])
    extends ExcelContainer(config, maybeSz) {

    val workbook: Workbook = input.fold (
      file => new HSSFWorkbook(new FileInputStream(file)),
      is => new HSSFWorkbook(is)
    )

    def get(address: Address): Option[Tabular] = {

      for {
        cell <- cellFromAddr(address, workbook)
      } yield new ExcelTabular(this, conv, headersFromCell(cell), cell)
    }
  }

  class XlsxContainer(config: Tabular.Config, pkg: OPCPackage, maybeSz: Option[Long])
    extends ExcelContainer(config, maybeSz) {

    val workbook: Workbook = new XSSFWorkbook(pkg)

    def get(address: Address): Option[Tabular] = {

      for {
        cell <- cellFromAddr(address, workbook)
      } yield new ExcelTabular(this, conv, headersFromCell(cell), cell)

    }

  }

  object ContainerSource extends Tabular.ContainerSource {
    val exts: Seq[String] = "xlsx" :: "xls" :: Nil

    def open(config: Tabular.Config, is: InputStream, ext: String, maybeSizeHint: Option[Long]): Option[Tabular.Container] = {
      if (is == null) {
        None
      } else {
        val src = ext match {
          case "xlsx" =>
            new XlsxContainer(config, OPCPackage.open(is), maybeSizeHint)

          case "xls" =>
            new XlsContainer(config, Right(is), maybeSizeHint)
        }

        Some(src)
      }
    }

    def open(config: Tabular.Config, file: File): Option[Tabular.Container] = {
      if (file.exists()) {
        val maybeSz = Some(Files.size(file.toPath))
        Tabular.extensionOf(file.getName).map {
          case "xlsx" =>
            new XlsxContainer(config, OPCPackage.open(file), maybeSz)

          case "xls" =>
            new XlsContainer(config, Left(file), maybeSz)
        }
      } else {
        None
      }
    }

    /**
      * Address string is of the form [sheetname]![rangename]
      * If there is no '!' separator, the address is assumed to be a range.
      * range can be a named range or range address (e.g. B1:D4).
      * If a range address is given and there is no sheetname, the first sheet
      * is assumed.
      *
      * @param addr address string
      * @return address
      */
    override def parseAddress(addr: String): Address = {
      if (addr.length == 0) {
        NilAddress
      } else {
        val pos = addr.indexOf('!')
        if (pos == -1) {
          ExcelAddress(None, addr)
        } else {
          val s = addr.substring(0, pos)
          val s1 = if (s.startsWith("'") && s.endsWith("'")) s.substring(1, s.length - 1) else s
          ExcelAddress(Some(addr.substring(0, pos)), addr.substring(pos+1))
        }
      }
    }
  }

  case class ExcelAddress(sheet: Option[String], range: String) extends Address

  private def cellFromAddr(addr: Address, workbook: Workbook): Option[Cell] = {

    import org.apache.poi.ss.SpreadsheetVersion.{EXCEL97, EXCEL2007}
    import org.apache.poi.ss.util.{AreaReference, CellReference}
    import CellReference.NameType

    def findSheet(maybeSheet: Option[String]): Sheet = {
      maybeSheet.fold
      { workbook.getSheetAt(0) }
      { n => workbook.getSheet(n) }
    }

    val ret: Option[Cell] = {

      addr match {
        case NilAddress =>
          val sheet = findSheet(None)
          Some(sheet.getRow(0).getCell(0))

        case ExcelAddress(sheetName, rawRange) =>
          val sheet = findSheet(sheetName)

          // Convert blank range to cell at origin
          val range = if (rawRange.trim.length == 0) "A1" else rawRange

          CellReference.classifyCellReference(range, EXCEL97) match {

            case NameType.CELL =>
              val cr = new CellReference(range)
              Some(sheet.getRow(cr.getRow).getCell(cr.getCol))

            case NameType.NAMED_RANGE =>
              val x = for {
                name <- Option(workbook.getName(range))
                nameRef <- Option(name.getRefersToFormula)
              } yield {
                ContainerSource.parseAddress(nameRef) match {
                  case ExcelAddress(maybeSheet, r) =>
                    val ar = new AreaReference(r, EXCEL2007)
                    val cr = ar.getFirstCell
                    val sheet1 = findSheet(maybeSheet)
                    Some(sheet1.getRow(cr.getRow).getCell(cr.getCol))

                  case _ =>
                    None
                }
              }

              x.flatten

            case _ => None
          }
      }
    }

    /*
    if (ret.isEmpty) {
      // Interpret the address as a worksheet name
      val maybeIndex = (0 until workbook.getNumberOfSheets).find { i =>
        val sheet = workbook.getSheetAt(i)
        sheet != null && addr.sheet.contains(sheet.getSheetName)
      }

      maybeIndex.map { i =>
        val ws = workbook.getSheetAt(i)
        val row = ws.getRow(ws.getFirstRowNum)
        row.getCell(row.getFirstCellNum)
      }
    } */

    ret
  }

  /**
    * Get a contiguous range of string values including
    * the given cell and to the right.
    */
  private def headersFromCell(cell: Cell): Seq[String] = {

    val arr = mutable.ArrayBuffer.empty[String]
    val row = cell.getRow

    var index = cell.getColumnIndex
    val last = row.getLastCellNum

    while (index < last) {
      val c = row.getCell(index)
      if (c != null && c.getCellTypeEnum == CellType.STRING) {
        arr += c.getStringCellValue
        index += 1
      } else {
        index = last
      }
    }

    arr.toSeq
  }

  private def isCellBlank(cell: Cell): Boolean =
    cell == null ||
      (cell.getCellTypeEnum == CellType.BLANK) ||
      (cell.getCellTypeEnum == CellType.STRING && cell.getStringCellValue == "")

  private def isRowBlank(sheet: Sheet, rowIndex: Int, from: Int, to: Int): Boolean =
    rowIndex > sheet.getLastRowNum || isRowBlank(sheet.getRow(rowIndex), from, to)

  private def isRowBlank(row: Row, from: Int, to: Int): Boolean = {
    row == null ||
      (from until to).forall(index => isCellBlank(row.getCell(index)))
  }

  /**
    * Type of cell value, whether literal or cached formula value
    */
  private def cellValueType(cell: Cell): CellType = {
    if (cell == null) {
      CellType.BLANK
    } else {
      cell.getCellTypeEnum match {
        case CellType.FORMULA => cell.getCachedFormulaResultTypeEnum
        case t @ _ => t
      }
    }
  }

  private def typeStr(cell: Cell): String = {
    val ct = cell.getCellTypeEnum
    if (ct == CellType.FORMULA)
      s"$ct[${cell.getCachedFormulaResultTypeEnum.toString}]"
    else
      ct.toString
  }

  private def dumpCell(cell: Cell) =
    s"""<Cell r=${cell.getRowIndex} c=${cell.getColumnIndex} type=${typeStr(cell)} value="$cell" />"""

  class ExcelConverter(config: Tabular.Config) extends Converter[Cell](config) {

    def asString(cell: Cell): Option[String] = {

      cellValueType(cell) match {
        case CellType.STRING =>
          val s = cell.getStringCellValue
          Some(if (config.trimStrings) s.trim else s)
        case CellType.NUMERIC =>
          Some(cell.getNumericCellValue.toInt.toString)
        case t@_ =>
          None
      }
    }

    def asInt(cell: Cell): Option[Int] = {
      cellValueType(cell) match {
        case CellType.NUMERIC => Some(cell.getNumericCellValue.toInt)
        case _ => None
      }
    }

    def asLong(cell: Cell): Option[Long] = {
      cellValueType(cell) match {
        case CellType.NUMERIC => Some(cell.getNumericCellValue.toLong)
        case _ => None
      }
    }

    def asDouble(cell: Cell): Option[Double] = {
      cellValueType(cell) match {
        case CellType.NUMERIC => Some(cell.getNumericCellValue)
        case _ => None
      }
    }

    def asLocalDate(cell: Cell): Option[LocalDate] = {
      cellValueType(cell) match {
        case CellType.NUMERIC =>
          Some(cell.getDateCellValue.toInstant.atZone(ZoneId.systemDefault()).toLocalDate)

        case CellType.STRING =>
          try {
            Some(LocalDate.from(config.dateFmt.parse(cell.getStringCellValue)))
          } catch {
            case ex: Exception =>
              None
          }

        case _ => None
      }
    }

    def asLocalDateTime(cell: Cell): Option[LocalDateTime] = {

      cellValueType(cell) match {
        case CellType.NUMERIC =>
          val date = DateUtil.getJavaDate(cell.getNumericCellValue)
          val zonedDateTime: ZonedDateTime = date.toInstant.atZone(ZoneId.systemDefault())
          Some(zonedDateTime.toLocalDateTime)

        case CellType.STRING =>
          try {
            Some(LocalDateTime.from(config.dateTimeFmt.parse(cell.getStringCellValue)))
          } catch {
            case ex: Exception =>
              None
          }

        case _ => None
      }
    }

    // Very lenient interpretation of bools here.
    def asBoolean(cell: Cell): Option[Boolean] = {
      cellValueType(cell) match {
        case CellType.BOOLEAN =>
          Some(cell.getBooleanCellValue)

        case CellType.NUMERIC =>
          asInt(cell).map (_ != 0)

        case _ =>
          asString(cell).flatMap { s =>
            s.toLowerCase match {
              case "true" | "y" | "1" => Some(true)
              case "false" | "n" | "0" => Some(false)
              case _ => None
            }
          }
      }
    }

  }

}


