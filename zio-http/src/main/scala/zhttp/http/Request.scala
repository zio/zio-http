package zhttp.http

import zio.ZIO

import java.net.InetAddress

trait Request extends HeaderExtension[Request] { self =>
  def copy[R, E](
    method: Method = self.method,
    url: URL = self.url,
    headers: List[Header] = self.getHeaders,
    data: HttpData[Any, Nothing] = self.data,
  ): Request = {
    val m = method
    val u = url
    val h = headers
    val d = data
    new Request {
      override def method: Method = m

      override def url: URL = u

      override def getHeaders: List[Header]     = h
      override def data: HttpData[Any, Nothing] = d

      override def remoteAddress: Option[InetAddress] = self.remoteAddress

      override def getBodyAsString: Option[String] = d match {
        case HttpData.Text(text, _)       => Option(text)
        case HttpData.BinaryChunk(data)   => Option(new String(data.toArray, getCharset.getOrElse(HTTP_CHARSET)))
        case HttpData.BinaryByteBuf(data) => Option(data.toString(getCharset.getOrElse(HTTP_CHARSET)))
        case HttpData.BinaryStream(_)     => Option.empty
        case HttpData.Empty               => Option.empty
      }
    }
  }

  /**
   * Decodes the content of request as string
   */
  def getBodyAsString: Option[String]

  /**
   * Gets all the headers in the Request
   */
  def getHeaders: List[Header]

  /**
   * Checks is the request is a pre-flight request or not
   */
  def isPreflight: Boolean = method == Method.OPTIONS

  /**
   * Gets the request's method
   */
  def method: Method

  /**
   * Gets the request's path
   */
  def path: Path = url.path

  /**
   * Gets the request's content
   */
  def data: HttpData[Any, Nothing]

  /**
   * Gets the remote address if available
   */
  def remoteAddress: Option[InetAddress]

  /**
   * Gets the complete url
   */
  def url: URL

  /**
   * Updates the headers using the provided function
   */
  final override def updateHeaders(f: List[Header] => List[Header]): Request = self.copy(headers = f(self.getHeaders))
}

object Request {

  /**
   * Constructor for Request
   */
  def apply[R, E](
    method: Method = Method.GET,
    url: URL = URL.root,
    headers: List[Header] = Nil,
    remoteAddress: Option[InetAddress] = None,
    data: HttpData[Any, Nothing] = HttpData.Empty,
  ): Request = {
    val m  = method
    val u  = url
    val h  = headers
    val ra = remoteAddress
    val d  = data
    new Request {
      override def method: Method                     = m
      override def url: URL                           = u
      override def getHeaders: List[Header]           = h
      override def remoteAddress: Option[InetAddress] = ra
      override def data: HttpData[Any, Nothing]       = d
      override def getBodyAsString: Option[String]    = d match {
        case HttpData.Text(text, _)       => Some(text)
        case HttpData.BinaryChunk(data)   => Some((new String(data.toArray, HTTP_CHARSET)))
        case HttpData.BinaryByteBuf(data) => Some(data.toString(HTTP_CHARSET))
        case _                            => Option.empty
      }
    }
  }

  /**
   * Effectfully create a new Request object
   */
  def make[R, E <: Throwable](
    method: Method = Method.GET,
    url: URL = URL.root,
    headers: List[Header] = Nil,
    remoteAddress: Option[InetAddress],
    content: HttpData[R, Nothing] = HttpData.empty,
  ): ZIO[R, Nothing, Request] =
    for {
      r <- ZIO.environment[R]
      c = content.provide(r)
    } yield Request(method, url, headers, remoteAddress, c)

  /**
   * Lift request to TypedRequest with option to extract params
   */
  final class ParameterizedRequest[A](req: Request, val params: A) extends Request {
    override def data: HttpData[Any, Nothing] = req.data

    override def getHeaders: List[Header] = req.getHeaders

    override def getBodyAsString: Option[String] = req.getBodyAsString

    override def method: Method = req.method

    override def remoteAddress: Option[InetAddress] = req.remoteAddress

    override def url: URL = req.url
  }

  object ParameterizedRequest {
    def apply[A](req: Request, params: A): ParameterizedRequest[A] = new ParameterizedRequest(req, params)
  }
}
