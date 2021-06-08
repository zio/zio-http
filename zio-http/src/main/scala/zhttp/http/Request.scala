package zhttp.http

import io.netty.handler.codec.http.{HttpRequest => JHttpRequest}

// REQUEST
/*final case class Request(
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

}

object Request {}*/

sealed trait Request[-R, +E, +A] { self =>
  def method: Method
  def url: URL
  def headers: List[Header]
  def getHeader(name: String): Option[Header]                    = self.headers.find(h => h.name == name)
  def content(implicit ev: HasContent[A]): Content[R, E, ev.Out] = ev.content(self)
  def copy[R1, E1, A1](
    method: Method = self.method,
    url: URL = self.url,
    headers: List[Header] = self.headers,
    content: Content[R1, E1, A1],
  ): Request[R1, E1, A1]                                         =
    Request.Default(method, url, headers, content)
}
object Request                   {
  final case class Default[R, E, A](method: Method, url: URL, headers: List[Header], dContent: Content[R, E, A])
      extends Request[R, E, A]
  final case class FromJHttpRequest(jReq: JHttpRequest) extends Request[Any, Nothing, Nothing] {
    override def method: Method        = Method.fromJHttpMethod(jReq.method())
    override def url: URL              = URL.fromString(jReq.uri()).fold(_ => URL(Path("/")), u => u)
    override def headers: List[Header] = Header.make(jReq.headers())
  }
  def apply[R, E, A](method: Method, url: URL, headers: List[Header], content: Content[R, E, A]): Request[R, E, A] =
    Default[R, E, A](method, url, headers, content)
  def apply[R, E, A](endpoint: Endpoint, headers: List[Header], content: Content[R, E, A]): Request[R, E, A] =
    Default[R, E, A](endpoint._1, endpoint._2, headers, content)
}
