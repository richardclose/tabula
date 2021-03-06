import java.time.format.DateTimeFormatter
import org.scalatest._
import org.phasanix.tabula._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.must.Matchers
import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper

class ExcelSpec extends AnyFlatSpec with Matchers {

  val config: Tabular.Config = Tabular.Config.default // (dateFmtStr = "dd-MMM-yyyy", dateTimeFmtStr = "dd-MMM-yyyy HH:mm:ss", trimStrings = true)

  val dateFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

  "ExcelSource" should "load named range" in {

    val maybeTab = for {
      src <- Tabular.open(config, getClass.getResourceAsStream("/testdata.xlsx"), "xlsx")
      tab <- src.get(Tabular.parseAddress("TestRange", "xlsx"))
    } yield tab

    maybeTab.isDefined shouldBe true

    maybeTab.foreach { tab =>
      tab.columnNames.length shouldEqual 2
      val rows = tab.rows.toIndexedSeq
      rows.length shouldEqual 6
      tab.parent.close()
    }


  }

  it should "load named worksheet" in {
    val maybeTab = for {
      src <- Tabular.open(config, getClass.getResourceAsStream("/testdata.xls"), "xls")
      tab <- src.get(Tabular.parseAddress("Sheet2!", "xls"))
    } yield tab

    maybeTab.isDefined shouldBe true

    maybeTab.foreach { tab =>
      val rows = tab.rows.toIndexedSeq
      rows.head.get[Boolean](2).get shouldBe true
      rows.head.get[Boolean](3).get shouldBe false
      tab.parent.close()
    }
  }

  it should "load long digit string correctly" in {
    for {
      src <- Tabular.open(config, getClass.getResourceAsStream("testdata1.xlsx"), "xlsx")
      tab <- src.get(NilAddress)
    } {

      val rows = tab.rows.toSeq
      val row = rows.head
      val s = row.get[String]("longdigitstring").get
      src.close()
      s shouldEqual "123456789876"
    }

  }


}
