package zhttp.http

import zhttp.http.Meta.getMeta

final case class Cookie[M <: Meta](name: String, content: String, meta: Option[M] = None) { self =>
  def clearCookie: Cookie[Nothing] =
    copy(content = "", meta = None)

  def fromCookie: String = s"${self.name}=${self.content}${getMeta(self.meta)}"
}

object Cookie {
  def toCookie(header: Header): Cookie[Nothing] = {
    val list    = header.value.toString.split(";").toList.head.split("=")
    val name    = list(0)
    val content = list(1)
    Cookie(name, content)
  }

  def toCookieList(header: Header): List[Cookie[Nothing]] = {
    val list: List[String] = header.value.toString.split(";").toList
    list.map(a => {
      val list = a.split("=").toList
      Cookie(list.head, list.tail.head)
    })
  }
}
