package zhttp.http

import io.netty.handler.codec.http.{HttpRequest => JHttpRequest}

sealed trait Request extends Product with Serializable with HasHeaders with HeadersHelpers { self =>
  def method: Method
  def url: URL
  def headers: List[Header]

  def route: Route            = method -> url.path
  def endpoint: (Method, URL) = method -> url

  def getBodyAsString: Option[String] = ???
}

object Request {
  def apply(jReq: JHttpRequest): Request = FromJHttpRequest(jReq)
  def apply(endpoint: Endpoint): Request = Request(method = endpoint._1, url = endpoint._2, Nil, HttpData.Empty)
  def apply(
    method: Method = Method.GET,
    url: URL = URL.empty,
    headers: List[Header] = Nil,
    data: HttpData[Any, Nothing] = HttpData.empty,
  ): Request                             =
    Complete(method, url, headers, data)

  private[zhttp] final case class Complete(
    method: Method,
    url: URL,
    headers: List[Header] = Nil,
    body: HttpData[Any, Nothing],
  )                                                                    extends Request
  private[zhttp] final case class FromJHttpRequest(jReq: JHttpRequest) extends Request {
    override def method: Method        = Method.fromJHttpMethod(jReq.method())
    override def url: URL              = URL.fromString(jReq.uri()).getOrElse(URL.empty)
    override def headers: List[Header] = Header.make(jReq.headers())
  }
}
