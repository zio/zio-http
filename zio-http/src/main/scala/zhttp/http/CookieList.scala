package zhttp.http

case class CookieList(toList: List[Cookie]) { self =>
  def isEmpty: Boolean                   = toList.isEmpty
  def get(name: String): Option[Cookie]  = toList.find(_.name == name)
  def getAll(name: String): List[Cookie] = toList.filter(_.name == name)
}

object CookieList {
  val empty: CookieList = CookieList(Nil)

  def encode(cookieList: CookieList): String     = ???
  def decode(string: String): Option[CookieList] = ???
}
