package zhttp.http

import io.netty.handler.codec.http.{HttpHeaderNames => JHttpHeaderNames}

// REQUEST
final case class Request(
  endpoint: Endpoint,
  headers: List[Header] = List.empty,
  content: HttpData[Any, Nothing] = HttpData.empty,
) extends HasHeaders
    with HeadersHelpers { self =>
  val method: Method = endpoint._1
  val url: URL       = endpoint._2
  val route: Route   = method -> url.path

  def getBodyAsString: Option[String] = content match {
    case HttpData.CompleteData(data) => Option(data.map(_.toChar).mkString)
    case _                           => Option.empty
  }

  def cookies: List[Cookie[Nothing]] = {
    val h: Header = headers
      .filter(x => x.name.toString.equalsIgnoreCase(JHttpHeaderNames.COOKIE.toString))
      .head
    Cookie.toCookieList(h)
  }

}

object Request {}
