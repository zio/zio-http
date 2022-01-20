package zhttp.http

import io.netty.buffer.ByteBuf
import zhttp.http.headers.HeaderExtension
import zhttp.service.server.ContentDecoder
import zio.stream.ZStream
import zio.{Task, UIO, ZIO}

import java.net.InetAddress

trait Request extends HeaderExtension[Request] { self =>

  /**
   * Updates the headers using the provided function
   */
  final override def updateHeaders(update: Headers => Headers): Request = self.copy(headers = update(self.getHeaders))

  def copy(
    method: Method = self.method,
    url: URL = self.url,
    headers: Headers = self.getHeaders,
  ): Request = {
    val m = method
    val u = url
    val h = headers
    new Request {
      override def method: Method                     = m
      override def url: URL                           = u
      override def getHeaders: Headers                = h
      override def remoteAddress: Option[InetAddress] = self.remoteAddress
      override def decodeContent[R, B](
        decoder: ContentDecoder[R, Throwable, ByteBuf, B],
      ): ZIO[R, Throwable, B] =
        self.decodeContent(decoder)
    }
  }

  def decodeContent[R, B](decoder: ContentDecoder[R, Throwable, ByteBuf, B]): ZIO[R, Throwable, B]

  /**
   * Decodes the content of request as a Chunk of Bytes
   */
  def getBody[R]: ZStream[R, Throwable, ByteBuf] =
    for {
      raw    <- ZStream.fromEffect(decodeContent(ContentDecoder.backPressure))
      stream <- ZStream.fromQueue(raw)
    } yield stream

  /**
   * Decodes the content of request as string
   */
  def getBodyAsString: Task[String] =
    decodeContent(ContentDecoder.text)

  /**
   * Gets all the headers in the Request
   */
  def getHeaders: Headers

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
   * Gets the remote address if available
   */
  def remoteAddress: Option[InetAddress]

  /**
   * Overwrites the method in the request
   */
  def setMethod(method: Method): Request = self.copy(method = method)

  /**
   * Overwrites the path in the request
   */
  def setPath(path: Path): Request = self.copy(url = self.url.copy(path = path))

  /**
   * Overwrites the url in the request
   */
  def setUrl(url: URL): Request = self.copy(url = url)

  /**
   * Gets the complete url
   */
  def url: URL
}

object Request {

  /**
   * Constructor for Request
   */
  def apply(
    method: Method = Method.GET,
    url: URL = URL.root,
    headers: Headers = Headers.empty,
    remoteAddress: Option[InetAddress] = None,
    data: HttpData = HttpData.Empty,
  ): Request = {
    val m  = method
    val u  = url
    val h  = headers
    val ra = remoteAddress
    new Request {
      override def method: Method                     = m
      override def url: URL                           = u
      override def getHeaders: Headers                = h
      override def remoteAddress: Option[InetAddress] = ra
      override def decodeContent[R, B](
        decoder: ContentDecoder[R, Throwable, ByteBuf, B],
      ): ZIO[R, Throwable, B] =
        decoder.decode(data, method, url, headers)
    }
  }

  /**
   * Effectfully create a new Request object
   */
  def make[E <: Throwable](
    method: Method = Method.GET,
    url: URL = URL.root,
    headers: Headers = Headers.empty,
    remoteAddress: Option[InetAddress],
    content: HttpData = HttpData.empty,
  ): UIO[Request] =
    UIO(Request(method, url, headers, remoteAddress, content))

  /**
   * Lift request to TypedRequest with option to extract params
   */
  final class ParameterizedRequest[A](req: Request, val params: A) extends Request {
    override def getHeaders: Headers                = req.getHeaders
    override def method: Method                     = req.method
    override def remoteAddress: Option[InetAddress] = req.remoteAddress
    override def url: URL                           = req.url
    override def decodeContent[R, B](
      decoder: ContentDecoder[R, Throwable, ByteBuf, B],
    ): ZIO[R, Throwable, B] =
      req.decodeContent(decoder)
  }

  object ParameterizedRequest {
    def apply[A](req: Request, params: A): ParameterizedRequest[A] = new ParameterizedRequest(req, params)
  }
}
