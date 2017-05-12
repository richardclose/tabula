package org.phasanix.tabula

import java.io.{File, InputStream}
import java.nio.charset.Charset
import java.time.format.DateTimeFormatter

import reflect.runtime.universe._

/**
  * Presents tabular data as a table, with columns
  */
trait Tabular {

  /** Parent container */
  val parent: Tabular.Container

  /** Names of column headers */
  def columnNames: Seq[String]

  /** Iterator over rows */
  def rows: Iterator[Tabular.Row]

  def estimatedRowCount: Int

  private lazy val colIndexMap: Map[String, Int] = columnNames.zipWithIndex.toMap

  /** Index of given column header name */
  def indexOf(column: String): Option[Int] = colIndexMap.get(column)
}

object Tabular {

  /**
    * Configuration, controlling how values are converted
    */
  case class Config(dateFmtStr: String, dateTimeFmtStr: String, trimStrings: Boolean, charsetName: String) {
    /** format string for parsing LocalDate */
    val dateFmt: DateTimeFormatter = DateTimeFormatter.ofPattern(dateFmtStr)

    /** format string for parsing LocalDateTime */
    val dateTimeFmt: DateTimeFormatter = DateTimeFormatter.ofPattern(dateTimeFmtStr)

    /** Charset to read streams with */
    def charset: Charset = Charset.forName(charsetName)
  }

  object Config {
    def default: Config = Config("dd-MM-yyyy", "dd-MM-yyyy HH:mm:ss", trimStrings = true, charsetName = "UTF-8")
  }

  /**
    * A row of tabular data
    */
  trait Row {
    val parent: Tabular
    def columnCount: Int = parent.columnNames.length
    def get[D : TypeTag](index: Int): Option[D]
    def get[D : TypeTag](column: String): Option[D] = {
      for (i <- parent.indexOf(column); value <- get[D](i)) yield value
    }
  }

  /**
    * A container (either an InputStream or a File), which
    * provides one or more <code>Tabular</code>s.
    */
  trait Container {

    /**
      * Get tabular data at the given address.
      * @param address Parsed Address, indicating where in the container
      *                to find the data.
      * @return a Tabular, if found.
      */
    def get(address: Address): Option[Tabular]

    /**
      * Close this container.
      */
    def close(): Unit

    /**
      * Size of source file, if known
      */
    def fileSize: Option[Long]
  }

  /**
    * Object that knows how to read files (or streams) of a particular type,
    * and return Containers.
    */
  trait ContainerSource {

    /**
      * List of file extensions that this ContainerSource can open
      */
    val exts: Seq[String]

    /**
      * Open the given InputStream. This may cause the whole InputStream
      * to be read into memory (e.g. with Excel files).
      */
    def open(config: Config, is: InputStream, ext: String, maybeSizeHint: Option[Long]): Option[Container]

    /**
      * Open the given File.
      */
    def open(config: Config, file: File): Option[Container]
    def parseAddress(addr: String): Address = NilAddress
  }

  // Not pluggable, add additional ContainerSources here
  private val containerSources: Seq[ContainerSource] =
    Csv.ContainerSource ::
    Excel.ContainerSource ::
    Nil

  /**
    * Extension of the given filename, if any.
    */
  def extensionOf(filename: String): Option[String] = {
    val i = filename.lastIndexOf('.')
    if (i == -1)
      None
    else
      Some(filename.substring(i+1))
  }

  /**
    * Open a Container from an input stream
 *
    * @param config configuration
    * @param is input stream
    * @param ext file extension
    * @param maybeSizeHint Some(streamlength) in bytes, if the length is known, else None
    * @return opened source.
    */
  def open(config: Config, is: InputStream, ext: String, maybeSizeHint: Option[Long] = None): Option[Container] = {
    for {
      ss <- containerSources.find(_.exts.contains(ext))
      src <- ss.open(config, is, ext, maybeSizeHint)
    } yield src
  }

  /**
    * Open a Container from a file.
    */
  def open(config: Config, file: File): Option[Container] = {
    for {
      ext <- extensionOf(file.getName)
      ss <- containerSources.find(_.exts.contains(ext))
      src <- ss.open(config, file)
    } yield src
  }

  /**
    * Open a container from a file, given a filename.
    */
  def open(config: Config, filename: String): Option[Container] =
    open(config, new File(filename))

  /**
    * Maybe invoke block with container, then close
    */
  def withContainer[A](config: Config, file: File)(block: Container => A): Option[A] = {
    open(config, file).map { cont =>
      val ret = block(cont)
      cont.close()
      ret
    }
  }

  /**
    * Parse the given address string specifically for
    * the given extension.
    */
  def parseAddress(address: String, ext: String): Address = {
    containerSources.find(_.exts.contains(ext))
      .map(_.parseAddress(address))
      .getOrElse(NilAddress)
  }

}
