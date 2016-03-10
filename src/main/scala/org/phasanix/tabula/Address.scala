package org.phasanix.tabula

/**
  * Represents a location within a file, for example an entire CSV file,
  * a named range or a sheet in an Excel workbook
  */
trait Address

case object NilAddress extends Address
