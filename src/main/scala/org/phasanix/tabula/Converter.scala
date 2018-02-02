package org.phasanix.tabula

import java.time.{ZoneId, LocalDate, LocalDateTime}
import java.util.Date


import scala.reflect.runtime.universe._

abstract class Converter[S : TypeTag](val config: Tabular.Config) {
  protected val srcType: Type = typeOf[S]

  def convert[D: TypeTag](a: S): Option[D] = {
    val destType = typeOf[D]

    val ret: Option[_] = if (a == null) {
      None
    } else if (destType =:= typeOf[String]) {
      asString(a)
    } else if (destType =:= typeOf[Int]) {
      asInt(a)
    } else if (destType =:= typeOf[Long]) {
      asLong(a)
    } else if (destType =:= typeOf[Double]) {
      asDouble(a)
    } else if (destType =:= typeOf[LocalDateTime]) {
      asLocalDateTime(a)
    } else if (destType =:= typeOf[LocalDate]) {
      asLocalDate(a)
    } else if (destType =:= typeOf[Boolean]) {
      asBoolean(a)
    } else {
      None
    }

    ret.asInstanceOf[Option[D]]
  }

  def asInt(s: S): Option[Int]
  def asLong(s: S): Option[Long]
  def asDouble(s: S): Option[Double]
  def asString(s: S): Option[String]
  def asLocalDate(s: S): Option[LocalDate]
  def asLocalDateTime(s: S): Option[LocalDateTime]
  def asBoolean(s: S): Option[Boolean]

  def asDate(s: S): Option[Date] = {
    val zone = ZoneId.systemDefault()
    asLocalDateTime(s).map { d =>
      val inst = d.toInstant(zone.getRules.getOffset(d))
      new Date(inst.toEpochMilli)
    }
  }

}

object Converter {

  def makeStringConverter(config: Tabular.Config): Converter[String] =
    new BasicStringConverter(config)

  class BasicStringConverter(config: Tabular.Config) extends Converter[String](config) {

    def asString(s: String): Option[String] = {
      val s1 = if (config.trimStrings) s.trim else s
      Some(s1)
    }

    def asInt(s: String): Option[Int] = {
      try {
        if (s.length == 0) None
        else Some(s.toInt)
      } catch {
        case ex: Exception =>
          None
      }
    }

    def asLong(s: String): Option[Long] = {
      try {
        if (s.length == 0) None
        else Some(s.toLong)
      } catch {
        case ex: Exception =>
          None
      }
    }

    def asDouble(s: String): Option[Double] = {
      try {
        Some(s.toDouble)
      } catch {
        case ex: Exception =>
          None
      }
    }

    def asLocalDateTime(s: String): Option[LocalDateTime] = {
      config.dateTimeFmts.map { fmt =>
        try {
          Some(LocalDateTime.parse(s, fmt))
        } catch {
          case ex: Exception =>
            None
        }
      }.find(_.isDefined)
        .flatten
    }

    def asLocalDate(s: String): Option[LocalDate] = {

      config.dateFmts.map { fmt =>
        try {
          Some(LocalDate.parse(s, fmt))
        } catch {
          case ex: Exception =>
            None
        }
      }.find(_.isDefined)
        .flatten
    }

    def asBoolean(s: String): Option[Boolean] = {
      s.toLowerCase match {
        case "1" | "true" | "t" | "y" => Some(true)
        case "0" | "false" | "f" | "n" => Some(false)
        case _ => None
      }
    }

  }

}