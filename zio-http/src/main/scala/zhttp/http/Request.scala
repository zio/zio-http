package zhttp.http

import io.netty.buffer.{ByteBufUtil, Unpooled}
import zio.{Chunk, Task, ZIO}

import java.net.InetAddress

trait Request extends HeadersHelpers { self =>
  def method: Method

  def url: URL

  def headers: List[Header]

  def path: Path = url.path

  def decodeContent[R, B](decoder: ContentDecoder[R, Throwable, Chunk[Byte], B]): ZIO[R, Throwable, B]

  def remoteAddress: Option[InetAddress]

  def addHeader(header: Header): Request = self.copy(headers = header :: self.headers)

  def removeHeader(name: CharSequence): Request = self.copy(headers = self.headers.filter(_.name != name))

  def copy(method: Method = self.method, url: URL = self.url, headers: List[Header] = self.headers): Request = {
    val m = method
    val u = url
    val h = headers
    new Request {
      override def method: Method = m

      override def url: URL = u

      override def headers: List[Header] = h

      override def remoteAddress: Option[InetAddress] =
        self.remoteAddress

      override def decodeContent[R, B](decoder: ContentDecoder[R, Throwable, Chunk[Byte], B]): ZIO[R, Throwable, B] =
        self.decodeContent(decoder)
    }
  }
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
      override def method: Method = m

      override def url: URL = u

      override def headers: List[Header] = h

      override def remoteAddress: Option[InetAddress] = ra

      override def decodeContent[R, B](decoder: ContentDecoder[R, Throwable, Chunk[Byte], B]): ZIO[R, Throwable, B] =
        toChunk(data).flatMap { zoc =>
          decoder
            .decode(zoc)
            .mapError(e =>
              e match {
                case Some(value) => value
                case None        => ContentDecoder.Error.DecodeEmptyContent
              },
            )
        }
    }
  }

  def toChunk(data: HttpData[Any, Throwable]): Task[Chunk[Byte]] =
    data match {
      case HttpData.Empty                => ZIO.succeed(Chunk.empty)
      case HttpData.Text(text, charset)  => ZIO.succeed(Chunk.fromArray(text.getBytes(charset)))
      case HttpData.Binary(data)         => ZIO.succeed(data)
      case HttpData.BinaryN(data)        => ZIO.succeed(Chunk.fromArray(ByteBufUtil.getBytes(data)))
      case HttpData.BinaryStream(stream) =>
        stream
          .fold(Unpooled.compositeBuffer())((s, b) => s.writeBytes(Array(b)))
          .map(a => a.array().take(a.writerIndex()))
          .map(Chunk.fromArray(_))
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
}
