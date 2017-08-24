package org.phasanix.tabula

/**
  * Represents a location within a file, for example an entire CSV file,
  * a named range or a sheet in an Excel workbook
  */
trait Address

/**
  * No address.
  * Excel will interpret this as the first possible address in a source:
  * top left cell of first worksheet in workbook.
  * CSV addressing is not implemented yet.
  */
case object NilAddress extends Address {
  override def toString: String = "nil"
}

