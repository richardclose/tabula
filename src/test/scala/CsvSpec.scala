import org.phasanix.tabula.{Csv, Tabular}
import org.scalatest.{FlatSpec, Matchers}

class CsvSpec extends FlatSpec with Matchers {

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
}
