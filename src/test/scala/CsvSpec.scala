import java.io.ByteArrayInputStream
import java.nio.charset.Charset
import org.phasanix.tabula.{Csv, Tabular}
import org.scalatest.matchers.should.Matchers
import org.scalatest.flatspec.AnyFlatSpec

class CsvSpec extends AnyFlatSpec with Matchers {

  val testTwoRanges =
"""H1,H2,H3,H4
1,2,3,4
1a,2a,3a,4a
,,,
,,,
,,HA1,HA2,HA3
,,a1,b1,c1
,,a2,b2,c2
,,,,
,,,,
HB1,HB2,HB3
a1,b1,c1
a2,c2,c3
"""

  val config: Tabular.Config = Tabular.Config.default

  "CSV addresses" should "parse correctly" in {

    Tabular.parseAddress("D5", "csv") match {
      case Csv.CsvAddress(row, col) =>
        row shouldEqual 4
        col shouldEqual 3

      case _ =>
        assert(false, "CSV address parse failed")
    }
  }

  it should "load a data range" in {

    val fn = "/offset.csv"

    for {
      ext <- Tabular.extensionOf(fn)
      src <- Tabular.open(config, getClass.getResourceAsStream(fn), ext)
    } {

      val addr = Tabular.parseAddress("D5", ext)

      val tab = src.get(addr).get

      val rows = tab.rows.toIndexedSeq

      val row = rows.head

      rows.length shouldEqual 2
      row.columnCount shouldEqual 6
      row.get[Int]("the").get shouldEqual 1
      row.get[String]("columns").get shouldEqual "four"
    }
  }

  "CSV Parser" should "access multiple ranges in reverse order" in {
    val is = new ByteArrayInputStream(testTwoRanges.getBytes(Charset.forName("UTF-8")))

    for {
      src <- Tabular.open(config, is, "csv")
      tab1 <- src.get(Tabular.parseAddress("C6", "csv"))
      tab2 <- src.get(Tabular.parseAddress("A1", "csv"))
    } {
      val row1 = tab1.rows.next()
      val row2 = tab2.rows.next()

      row1.get[String]("HA3").get shouldEqual "c1"
      row2.get[Int]("H2").get shouldEqual 2
    }
  }

  "CSV Parser" should "access multiple ranges in order" in {
    val is = new ByteArrayInputStream(testTwoRanges.getBytes(Charset.forName("UTF-8")))
    val src =  Tabular.open(config, is, "csv").get

    for {
      tab0 <- src.get(Tabular.parseAddress("A1", "csv"))
      tab1 <- src.get(Tabular.parseAddress("C6", "csv"))
    } {
      val row0 = tab0.rows.next()
      val row1 = tab1.rows.next()

      row0.get[Int]("H2").get shouldEqual 2
      row1.get[String]("HA3").get shouldEqual "c1"
    }

    src.close()

  }

  it should "error when calling get() after direct iteration" in {
    val is = new ByteArrayInputStream(testTwoRanges.getBytes(Charset.forName("UTF-8")))

    val src = Tabular.open(config, is, "csv").get
    val tab0 = src.get(Tabular.parseAddress("A1", "csv")).get
    val tab1 = src.get(Tabular.parseAddress("C6", "csv")).get
    val row1 = tab1.rows.next()

    val err = try {
      val tab2 = src.get(Tabular.parseAddress("A11", "csv")).get
      "OK"
    } catch {
      case ex: Exception => ex.getMessage
    }

    err.startsWith("Illegal call") shouldBe true

    src.close()

  }

  it should "stop at a blank row" in {
    val is = new ByteArrayInputStream(testTwoRanges.getBytes(Charset.forName("UTF-8")))
    val ret = Tabular.withContainer(config, is, "csv") { src =>
      val tab = src.get(Tabular.parseAddress("A1", "csv")).get
      val rows = tab.rows.toIndexedSeq
      rows.length shouldBe 2
    }

    ret.isDefined shouldBe true

  }

}
