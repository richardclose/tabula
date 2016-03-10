import java.time.LocalDate
import java.time.format.DateTimeFormatter

import org.scalatest._
import org.phasanix.tabula._

class TabularSpec extends FlatSpec with Matchers {

  val config = Tabular.Config(dateFmtStr = "dd-MMM-yyyy", dateTimeFmtStr = "dd-MMM-yyyy HH:mm:ss")

  val dateFmt = DateTimeFormatter.ofPattern("dd/MM/yyyy")

  def testValidConversions(name: String): Unit = {

    for {
      ext <- Tabular.extensionOf(name)
      container <- Tabular.open(config, getClass.getResourceAsStream(name), ext)
      tab <- container.get(NilAddress)
    } {
      for ((row, i) <- tab.rows.zipWithIndex) {
        if (i == 0) {
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
      tab.columnNames.length shouldEqual 5
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
  
}
