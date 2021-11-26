package zhttp.http

import io.netty.buffer.{ByteBuf, ByteBufUtil, Unpooled}
import zio.{Chunk, UIO}

import java.net.InetAddress

trait Request extends HeaderExtension[Request] { self =>
  def copy(
    method: Method = self.method,
    url: URL = self.url,
    headers: List[Header] = self.getHeaders,
    data: HttpData[Any,Nothing] = HttpData.empty,
  ): Request = {
    val m = method
    val u = url
    val h = headers
    val d = data
    new Request {
      override def method: Method = m

      override def url: URL = u

      override def getHeaders: List[Header] = h

      override def remoteAddress: Option[InetAddress] =
        self.remoteAddress

      override def getBody: UIO[Chunk[Byte]] = self.getBodyAsByteBuf.map{ a =>
        Chunk.fromArray(ByteBufUtil.getBytes(a))
      }

      private [zhttp] def getBodyAsByteBuf: UIO[ByteBuf] = d match {
        case HttpData.Text(text, charset) => UIO(Unpooled.copiedBuffer(text, charset))
        case HttpData.BinaryChunk(data) => UIO(Unpooled.wrappedBuffer(data.toArray))
        case HttpData.BinaryByteBuf(data) => UIO(data)
        case HttpData.BinaryStream(_) => ???
        case HttpData.Empty => ???
      }
    }
  }

  private [zhttp] def getBodyAsByteBuf: UIO[ByteBuf]

  /**
   * Decodes the content of the request using the provided ContentDecoder
   */
  def getBody: UIO[Chunk[Byte]] = self.getBodyAsByteBuf.map{ a =>
    Chunk.fromArray(ByteBufUtil.getBytes(a))
  }

  /**
   * Decodes the content of request as string
   */
  def getBodyAsString: UIO[String] = getBody.map(cb => new String(cb.toArray, getCharset.getOrElse(HTTP_CHARSET)))

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
  def apply(
    method: Method = Method.GET,
    url: URL = URL.root,
    headers: List[Header] = Nil,
    remoteAddress: Option[InetAddress] = None,
    data: HttpData[Any,Nothing] = HttpData.empty,
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
      override def getBodyAsByteBuf: UIO[ByteBuf] = d match {
        case HttpData.Text(_, _) => ???
        case HttpData.BinaryChunk(_) => ???
        case HttpData.BinaryByteBuf(data) => UIO(data)
        case HttpData.BinaryStream(_) => ???
        case HttpData.Empty => ???
      }
    }
  }

  /**
   * Lift request to TypedRequest with option to extract params
   */
  final class ParameterizedRequest[A](req: Request, val params: A) extends Request {
    override def getBodyAsByteBuf: UIO[ByteBuf] =
      req.getBodyAsByteBuf

    override def getHeaders: List[Header] = req.getHeaders

    override def method: Method = req.method

    override def remoteAddress: Option[InetAddress] = req.remoteAddress

    override def url: URL = req.url
  }

  object ParameterizedRequest {
    def apply[A](req: Request, params: A): ParameterizedRequest[A] = new ParameterizedRequest(req, params)
  }
}
