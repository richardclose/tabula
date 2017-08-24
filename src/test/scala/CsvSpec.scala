import java.io.ByteArrayInputStream
import java.nio.charset.Charset

import org.phasanix.tabula.{Csv, Tabular}
import org.scalatest.{FlatSpec, Matchers}

class CsvSpec extends FlatSpec with Matchers {

  val testTwoRanges =
"""H1,H2,H3,H4
1,2,3,4
1a,2a,3a,4a
,,,
,,,
,,A1,A2,A3
,,a1,b1,c1
,,a2,b2,c2
"""

  val config = Tabular.Config.default

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

      val row = tab.rows.toSeq.head

      row.columnCount shouldEqual 6
      row.get[Int]("the").get shouldEqual 1
      row.get[String]("columns").get shouldEqual "four"
    }
  }

  "CSV Parser" should "access multiple ranges" in {
    val is = new ByteArrayInputStream(testTwoRanges.getBytes(Charset.forName("UTF-8")))

    for {
      src <- Tabular.open(config, is, "csv")
      tab1 <- src.get(Tabular.parseAddress("C6", "csv"))
      tab2 <- src.get(Tabular.parseAddress("A1", "csv"))
    } {
      val row1 = tab1.rows.next()
      val row2 = tab2.rows.next()

      row1.get[String]("A3").get shouldEqual "c1"
      row2.get[Int]("H2").get shouldEqual 2
    }

  }
}
