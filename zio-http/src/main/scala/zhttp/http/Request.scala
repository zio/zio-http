package zhttp.http

import io.netty.buffer.{ByteBuf, ByteBufUtil}
import io.netty.handler.codec.http.{DefaultFullHttpRequest, HttpRequest}
import zhttp.http.headers.HeaderExtension
import zio.stream.ZStream
import zio.{Chunk, Task, UIO}

import java.net.InetAddress

trait Request extends HeaderExtension[Request] { self =>

  /**
   * Updates the headers using the provided function
   */
  final override def updateHeaders(update: Headers => Headers): Request = self.copy(headers = update(self.headers))

  def copy(
    version: Version = self.version,
    method: Method = self.method,
    url: URL = self.url,
    headers: Headers = self.headers,
  ): Request = {
    val m = method
    val u = url
    val h = headers
    val v = version
    new Request {
      override def method: Method                     = m
      override def url: URL                           = u
      override def headers: Headers                   = h
      override def version: Version                   = v
      override def unsafeEncode: HttpRequest          = self.unsafeEncode
      override def remoteAddress: Option[InetAddress] = self.remoteAddress
      override def data: HttpData                     = self.data
    }
  }

  /**
   * Decodes the body as a HttpData
   */
  def data: HttpData

  final def bodyAsByteArray: Task[Array[Byte]] =
    bodyAsByteBuf.flatMap(buf => Task(ByteBufUtil.getBytes(buf)).ensuring(UIO(buf.release(buf.refCnt()))))

  /**
   * Decodes the content of request as a Chunk of Bytes
   */
  final def body: Task[Chunk[Byte]] =
    bodyAsByteArray.map(Chunk.fromArray)

  /**
   * Decodes the content of request as string
   */
  final def bodyAsString: Task[String] =
    bodyAsByteArray.map(new String(_, charset))

  /**
   * Decodes the content of request as stream of bytes
   */
  final def bodyAsStream: ZStream[Any, Throwable, Byte] = data.toByteBufStream
    .mapM[Any, Throwable, Chunk[Byte]] { buf =>
      Task {
        val bytes = Chunk.fromArray(ByteBufUtil.getBytes(buf))
        buf.release(buf.refCnt())
        bytes
      }
    }
    .flattenChunks

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
   * Gets the HttpRequest
   */
  private[zhttp] def unsafeEncode: HttpRequest

  /**
   * Gets the complete url
   */
  def url: URL

  def version: Version

  private[zhttp] final def bodyAsByteBuf: Task[ByteBuf] = data.toByteBuf
}

object Request {

  /**
   * Constructor for Request
   */
  def apply(
    version: Version = Version.`HTTP/1.1`,
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
    val d  = data
    val v  = version

    new Request {
      override def method: Method                     = m
      override def url: URL                           = u
      override def headers: Headers                   = h
      override def version: Version                   = v
      override def unsafeEncode: HttpRequest          = {
        val jVersion = v.toJava
        val path     = url.relative.encode
        new DefaultFullHttpRequest(jVersion, method.toJava, path)
      }
      override def remoteAddress: Option[InetAddress] = ra
      override def data: HttpData                     = d

    }
  }

  /**
   * Effectfully create a new Request object
   */
  def make[E <: Throwable](
    version: Version = Version.`HTTP/1.1`,
    method: Method = Method.GET,
    url: URL = URL.root,
    headers: Headers = Headers.empty,
    remoteAddress: Option[InetAddress],
    content: HttpData = HttpData.empty,
  ): UIO[Request] =
    UIO(Request(version, method, url, headers, remoteAddress, content))

  /**
   * Lift request to TypedRequest with option to extract params
   */
  final class ParameterizedRequest[A](req: Request, val params: A) extends Request {
    override def headers: Headers                   = req.headers
    override def method: Method                     = req.method
    override def remoteAddress: Option[InetAddress] = req.remoteAddress
    override def url: URL                           = req.url
    override def version: Version                   = req.version
    override def unsafeEncode: HttpRequest          = req.unsafeEncode
    override def data: HttpData                     = req.data
  }

  object ParameterizedRequest {
    def apply[A](req: Request, params: A): ParameterizedRequest[A] = new ParameterizedRequest(req, params)
  }
}
