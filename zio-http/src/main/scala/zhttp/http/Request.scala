package zhttp.http

import io.netty.buffer.ByteBuf
import zhttp.http.headers.HeaderExtension
import zio._
import zio.stream.ZStream

import java.net.InetAddress

case class Request(
  method: Method = Method.GET,
  url: URL = URL.empty,
  headers: Headers = Headers.empty,
  remoteAddress: Option[InetAddress] = None,
  data: HttpData = HttpData.Incoming(unsafeRun = _ => ()),
) extends HeaderExtension[Request] { self =>

  def bodyAsString: Task[String]                          = data.asString
  def bodyAsBytes: Task[Chunk[Byte]]                      = data.asBytes
  def bodyAsStream: ZStream[Any, Throwable, ByteBuf]      = data.asStreamByteBuf
  def bodyAsByteBuf: Task[ByteBuf]                        = data.asByteBuf
  def bodyAsByteChunk: IO[Option[Throwable], Chunk[Byte]] = data.asByteChunk

  /**
   * Updates the headers using the provided function
   */
  final override def updateHeaders(update: Headers => Headers): Request = self.copy(headers = update(self.headers))

  /**
   * Checks is the request is a pre-flight request or not
   */
  def isPreflight: Boolean = self.method == Method.OPTIONS

  /**
   * Gets the request's path
   */
  def path: Path = self.url.path

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
}

object Request {

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
  final class ParameterizedRequest[A](req: Request, val params: A) {
    def headers: Headers                   = req.headers
    def method: Method                     = req.method
    def remoteAddress: Option[InetAddress] = req.remoteAddress
    def url: URL                           = req.url
    def data: HttpData                     = req.data
  }

  object ParameterizedRequest {
    def apply[A](req: Request, params: A): ParameterizedRequest[A] = new ParameterizedRequest(req, params)
  }
}
