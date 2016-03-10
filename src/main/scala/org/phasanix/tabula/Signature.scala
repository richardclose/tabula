package org.phasanix.tabula

/**
  * Signature abstracts identification of a Tabular.Source
  * Use case: sniffing the structure of a file
  * to determine what to do with it.
  */
trait Signature {
  def matches(tabSource: Tabular.Container): Boolean
}
