import java.io.ByteArrayInputStream
import java.nio.charset.Charset
import java.time.LocalDate
import java.time.format.DateTimeFormatter

import org.scalatest._
import org.phasanix.tabula._

class TabularSpec extends FlatSpec with Matchers {

  val config = Tabular.Config.default.copy(dateFmtStr = "dd-MMM-yyyy")
  val dateFmt = DateTimeFormatter.ofPattern("dd/MM/yyyy")

  def testValidConversions(name: String): Unit = {

    for {
      ext <- Tabular.extensionOf(name)
      container <- Tabular.open(config, getClass.getResourceAsStream(name), ext)
      tab <- container.get(NilAddress)
    } {
      for ((row, i) <- tab.rows.zipWithIndex) {
        if (i == 0) {
          val d = row.get[LocalDate]("a date")
          row.get[LocalDate]("a date").get.format(dateFmt) shouldEqual "12/05/2016"
          row.get[Int]("an int").get shouldEqual 42
          row.get[Long]("an int").get shouldEqual 42L
          row.get[Double]("a double").get shouldEqual 14.55
          row.get[Boolean]("a bool").get shouldBe true
          row.get[String]("a string").get shouldEqual "now is the winter of our discontent"
        }
      }
      container.close()
    }
  }

  def testInvalidConversions(name: String): Unit = {
    for {
      ext <- Tabular.extensionOf(name)
      container <- Tabular.open(config, getClass.getResourceAsStream(name), ext)
      tab <- container.get(NilAddress)
    } {
      for ((row, i) <- tab.rows.zipWithIndex) {
        if (i == 1) {
          row.get[LocalDate]("a date") shouldBe empty
          row.get[Double]("a double") shouldBe empty
          row.get[Boolean]("a bool") shouldBe empty
        }
      }

      container.close()
    }
  }

  def testHeaders(name: String): Unit = {
    for {
      ext <- Tabular.extensionOf(name)
      src <- Tabular.open(config, getClass.getResourceAsStream(name), ext)
      tab <- src.get(NilAddress)
    } {
      tab.columnNames.mkString(",") shouldEqual "a date,an int,a double,a bool,a string"
      src.close()
    }
  }

  "header parsing" should "work for CSV" in {
    testHeaders("/testdata.csv")
  }

  it should "work for XLS" in {
    testHeaders("/testdata.xls")
  }

  it should "work for XLSX" in {
    testHeaders("/testdata.xlsx")
  }

  "check valid conversions" should "work for CSV" in {
    testValidConversions("/testdata.csv")
  }

  it should "work for XLS" in {
    testValidConversions("/testdata.xls")
  }

  it should "work for XLSX" in {
    testValidConversions("/testdata.xlsx")
  }

  "check invalid conversions" should "work for CSV" in {
    testInvalidConversions("/testdata.csv")
  }

  it should "work for XLS" in {
    testInvalidConversions("/testdata.xls")
  }

  it should "work for XLSX" in {
    testInvalidConversions("/testdata.xlsx")
  }

  "charsets" should "load correctly" in {

    val csvStr =
      """First,Second,Third
£10,Crêpe,10¢
"""

    val charsets = "UTF-8" :: "windows-1252" :: "ISO-8859-1" :: Nil
    val values = csvStr.split("\n")(1).split(",")

    val passes = for {
      charset <- charsets.map(Charset.forName)
    } yield {
      val bytes = csvStr.getBytes(charset)
      val is = new ByteArrayInputStream(bytes)
      val conf = Tabular.Config.default.copy(charsetName = charset.name())

      val maybePassed = for {
        cont <- Tabular.open(conf, is, "csv")
        tab <- cont.get(NilAddress)
      } yield {
        val row = tab.rows.toSeq.head
        val cells = (0 until row.columnCount).flatMap(i => row.get[String](i))
        values.sameElements(cells)
      }

      maybePassed.get
    }

    passes.count(_ == true) shouldBe charsets.length
  }

}
