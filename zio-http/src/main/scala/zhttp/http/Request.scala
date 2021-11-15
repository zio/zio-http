package zhttp.http

import zio.{Chunk, ZIO}

import java.net.InetAddress

trait Request extends HeaderExtension[Request] { self =>
  def isPreflight: Boolean = method == Method.OPTIONS

  def method: Method

  def url: URL

  def getHeaders: List[Header]

  def path: Path = url.path

  def decodeContent[R, B](
    decoder: ContentDecoder[R, Throwable, Chunk[Byte], B],
  ): ZIO[R, Throwable, B]

  def remoteAddress: Option[InetAddress]

  def copy(method: Method = self.method, url: URL = self.url, headers: List[Header] = self.getHeaders): Request = {
    val m = method
    val u = url
    val h = headers
    new Request {
      override def method: Method = m

      override def url: URL = u

      override def getHeaders: List[Header] = h

      override def remoteAddress: Option[InetAddress] =
        self.remoteAddress

      override def decodeContent[R, B](
        decoder: ContentDecoder[R, Throwable, Chunk[Byte], B],
      ): ZIO[R, Throwable, B] =
        self.decodeContent(decoder)
    }
  }

  /**
   * Updates the headers using the provided function
   */
  final override def updateHeaders(f: List[Header] => List[Header]): Request = self.copy(headers = f(self.getHeaders))
}

object Request {

  /**
   * Constructor for Request
   */
  def apply(
    method: Method = Method.GET,
    url: URL = URL.root,
    headers: List[Header] = Nil,
    remoteAddress: Option[InetAddress] = None,
    data: HttpData[Any, Throwable] = HttpData.Empty,
  ): Request = {
    val m  = method
    val u  = url
    val h  = headers
    val ra = remoteAddress
    new Request {
      override def method: Method                     = m
      override def url: URL                           = u
      override def getHeaders: List[Header]           = h
      override def remoteAddress: Option[InetAddress] = ra
      override def decodeContent[R, B](
        decoder: ContentDecoder[R, Throwable, Chunk[Byte], B],
      ): ZIO[R, Throwable, B] =
        decoder.decode(data, method, url, headers)
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
    content: HttpData[R, E] = HttpData.empty,
  ): ZIO[R, Nothing, Request] =
    for {
      r <- ZIO.environment[R]
      c = content.provide(r)
    } yield Request(method, url, headers, remoteAddress, c)

  /**
   * Lift request to TypedRequest with option to extract params
   */
  final class ParameterizedRequest[A](req: Request, val params: A) extends Request {
    override def method: Method                     = req.method
    override def url: URL                           = req.url
    override def getHeaders: List[Header]           = req.getHeaders
    override def remoteAddress: Option[InetAddress] = req.remoteAddress
    override def decodeContent[R, B](
      decoder: ContentDecoder[R, Throwable, Chunk[Byte], B],
    ): ZIO[R, Throwable, B] =
      req.decodeContent(decoder)
  }

  object ParameterizedRequest {
    def apply[A](req: Request, params: A): ParameterizedRequest[A] = new ParameterizedRequest(req, params)
  }
}
