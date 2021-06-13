package zhttp.http

import io.netty.handler.codec.http.{HttpRequest => JHttpRequest}

sealed trait Request[-R, +E, +A] { self =>
  def method: Method
  def url: URL
  def headers: List[Header]
  def getHeader(name: String): Option[Header]                     = self.headers.find(h => h.name == name)
  def content(implicit ev: HasContent[A]): HttpData[R, E, ev.Out] = ev.content(self)
  def update[R1, E1, A1](
    method: Method = self.method,
    url: URL = self.url,
    headers: List[Header] = self.headers,
    content: HttpData[R1, E1, A1],
  ): Request[R1, E1, A1]                                          =
    Request.Default(method, url, headers, content)
}
object Request                   {
  final case class Default[R, E, A](method: Method, url: URL, headers: List[Header], dContent: HttpData[R, E, A])
      extends Request[R, E, A]
  final case class FromJHttpRequest(jReq: JHttpRequest) extends Request[Any, Nothing, Nothing] {
    override def method: Method        = Method.fromJHttpMethod(jReq.method())
    override def url: URL              = URL(Path(jReq.uri()))
    override def headers: List[Header] = Header.make(jReq.headers())
  }
  def apply[R, E, A](method: Method, url: URL, headers: List[Header], content: HttpData[R, E, A]): Request[R, E, A] =
    Default[R, E, A](method, url, headers, content)
  def apply[R, E, A](endpoint: Endpoint, headers: List[Header], content: HttpData[R, E, A]): Request[R, E, A] =
    Default[R, E, A](endpoint._1, endpoint._2, headers, content)
  def apply(endpoint: Endpoint, headers: List[Header]): Request[Any, Nothing, Opaque]                         =
    Default(endpoint._1, endpoint._2, headers, HttpData.empty)
}
