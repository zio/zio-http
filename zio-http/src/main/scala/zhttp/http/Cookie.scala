package zhttp.http

import zhttp.http.Cookie.validateCookie

import scala.util.{Failure, Success, Try}

final case class Cookie[M <: Meta](name: String, content: String, meta: Option[M] = None) { self =>
  def clearCookie: Cookie[Nothing] =
    copy(content = "", meta = None)

  def validate(value: String): String = validateCookie(value) match {
    case Some(_) => throw new Exception()
    case None    => value
  }
  override def toString: String       = Try {
    s"${validate(self.name)}=${self.content}${self.meta match {
      case Some(value) => "; " + value.toString
      case None        => ""
    }}"
  } match {
    case Failure(_)     => "invalid cookie: cannot use Separators or control characters"
    case Success(value) => value
  }
}

object Cookie {

  val ControlCharactersRegex = "\\x00-\\x1F\\x7F"
  val Separators             = "()<>@,;:\\\\\"/\\[\\]?={} \\x09"
  private val regex          = s"[^$Separators$ControlCharactersRegex]*".r

  def validateCookie(v: String): Option[String] = {
    if (regex.unapplySeq(v).isEmpty) {
      Some("invalid cookie: cannot use Separators or control characters")
    } else None
  }

  /**
   * Parse the cookie
   */
  def toCookie(headerValue: String): Option[Cookie[Nothing]] = {
    def splitNameContent(kv: String): (String, Option[String]) =
      (kv.split("=", 2).map(_.trim): @unchecked) match {
        case Array(v1)     => (v1, None)
        case Array(v1, v2) => (v1, Some(v2))
      }

    val cookie          = headerValue.split(";").map(_.trim)
    val (first, _)      = (cookie.head, cookie.tail)
    val (name, content) = splitNameContent(first)
    validateCookie(name) match {
      case Some(_) => None
      case None    => Some(Cookie(name, content.getOrElse("")))
    }
  }
}
