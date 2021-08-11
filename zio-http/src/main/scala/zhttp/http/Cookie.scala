package zhttp.http

final case class Cookie[M <: Meta](name: String, content: String, meta: Option[M] = None) { self =>
  def clearCookie: Cookie[Nothing] =
    copy(content = "", meta = None)

  override def toString: String = s"${self.name}=${self.content}${self.meta match {
    case Some(value) => "; " + value.toString
    case None        => ""
  }}"
}

object Cookie {

  /**
   * Parse the cookie
   */
  def toCookie(headerValue: String): Cookie[Nothing] = {
    def splitNameContent(kv: String): (String, Option[String]) =
      (kv.split("=", 2).map(_.trim): @unchecked) match {
        case Array(v1)     => (v1, None)
        case Array(v1, v2) => (v1, Some(v2))
      }

    val cookie          = headerValue.split(";").map(_.trim)
    val (first, _)      = (cookie.head, cookie.tail)
    val (name, content) = splitNameContent(first)
    Cookie(name, content.getOrElse(""))
  }
}
