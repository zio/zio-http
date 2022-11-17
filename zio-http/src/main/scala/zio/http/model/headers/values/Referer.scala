package zio.http.model.headers.values

import zio.http.URL


/** Referer header value. */
sealed trait Referer

object Referer {
  case class RefererValue(url: URL) extends Referer
  /** Invalid Referer value*/
  case object InvalidRefererValue extends Referer

  def toReferer(string: String): Referer = {
    URL.fromString(string) match {
      case Right(value) => RefererValue(value)
      case Left(_) => InvalidRefererValue
    }
  }

  def fromReferer(referer: Referer): String = {
    referer match {
      case RefererValue(value) => value.encode
      case InvalidRefererValue => ""
    }
  }
}