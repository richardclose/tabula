package org.phasanix.tabula

import org.phasanix.tabula.Tabular.Container

/**
  * Signature abstracts identification of a Tabular.Source
  * Use case: sniffing the structure of a file
  * to determine what to do with it.
  */
trait Signature {
  def matches(tabSource: Tabular.Container): Boolean
}


/**
  * Signature implementations
  */
object Signature {

    /**
      * Signature that matches a source by the headers at the start of the source
      * @param requiredHeaders List of headers that must be present for a match
      * @param ignoreCase If true, case is ignored when matching.
      */
    class MatchHeaders(requiredHeaders: Seq[String], ignoreCase: Boolean = true) extends Signature {
      def matches(tabSource: Container): Boolean = {
        tabSource.get(NilAddress) match {

          case Some(range) if ignoreCase =>
            requiredHeaders.forall(h => range.columnNames.exists(_.toLowerCase == h.toLowerCase))

          case Some(range) if !ignoreCase =>
            requiredHeaders.forall(range.columnNames.contains)

          case None =>
            false
        }
      }
    }

}