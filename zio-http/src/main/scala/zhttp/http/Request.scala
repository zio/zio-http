package zhttp.http

import io.netty.buffer.ByteBuf
import zhttp.http.headers.HeaderExtension
import zio._
import zio.stream.ZStream

import java.net.InetAddress

trait Request extends HeaderExtension[Request] { self =>

  /**
   * Updates the headers using the provided function
   */
  final override def updateHeaders(update: Headers => Headers): Request = self.copy(headers = update(self.headers))

  def copy(method: Method = self.method, url: URL = self.url, headers: Headers = self.headers): Request = {
    val m = method
    val u = url
    val h = headers
    new Request {
      override def method: Method                     = m
      override def url: URL                           = u
      override def headers: Headers                   = h
      override def remoteAddress: Option[InetAddress] = self.remoteAddress
      override def data: HttpData                     = self.data
    }
  }

  def data: HttpData
  def bodyAsString: Task[String]                               = data.asString
  def bodyAsBytes: Task[Chunk[Byte]]                           = data.asBytes
  def bodyAsStream: ZStream[Any, Throwable, ByteBuf]           = data.asStreamByteBuf
  def bodyAsByteBuf: Task[ByteBuf]                             = data.asByteBuf
  def bodyAsByteChunk: UIO[IO[Option[Throwable], Chunk[Byte]]] = data.asByteChunk

  /**
   * Gets all the headers in the Request
   */
  def headers: Headers

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
    data: HttpData = HttpData.empty,
  ): Request = {
    val m  = method
    val u  = url
    val h  = headers
    val ra = remoteAddress
    val d  = data
    new Request {
      override def method: Method = m

      override def url: URL = u

      override def headers: Headers = h

      override def remoteAddress: Option[InetAddress] = ra

      override def data: HttpData = d
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
    override def headers: Headers                   = req.headers
    override def method: Method                     = req.method
    override def remoteAddress: Option[InetAddress] = req.remoteAddress
    override def url: URL                           = req.url
    override def data: HttpData                     = req.data
  }

  object ParameterizedRequest {
    def apply[A](req: Request, params: A): ParameterizedRequest[A] = new ParameterizedRequest(req, params)
  }

}
