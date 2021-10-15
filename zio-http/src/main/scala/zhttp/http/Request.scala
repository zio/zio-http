package zhttp.http

import io.netty.buffer.{ByteBufUtil, Unpooled}
import zhttp.experiment.ContentDecoder.Text
import zhttp.experiment.{ContentDecoder, HttpData}
import zio.{Chunk, Task, ZIO}

import java.net.InetAddress

trait Request extends HeadersHelpers { self =>
  def method: Method
  def url: URL
  def headers: List[Header]
  def path: Path                                = url.path
  def decodeContent[R, B](decoder: ContentDecoder[R, Throwable, Chunk[Byte], B]): ZIO[R, Throwable, B]
  def remoteAddress: Option[InetAddress]
  def addHeader(header: Header): Request        = self.copy(headers = header :: self.headers)
  def removeHeader(name: CharSequence): Request = self.copy(headers = self.headers.filter(_.name != name))
  def copy(method: Method = self.method, url: URL = self.url, headers: List[Header] = self.headers): Request = {
    val m = method
    val u = url
    val h = headers
    new Request {
      override def method: Method                     = m
      override def url: URL                           = u
      override def headers: List[Header]              = h
      override def remoteAddress: Option[InetAddress] =
        self.remoteAddress
      override def decodeContent[R, B](
        decoder: ContentDecoder[R, Throwable, Chunk[Byte], B],
      ): ZIO[R, Throwable, B]                         =
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
    content: HttpData[Any, Throwable] = HttpData.Empty,
  ): Request = {
    val m  = method
    val u  = url
    val h  = headers
    val ra = remoteAddress
    new Request {
      override def method: Method                                                                                   = m
      override def url: URL                                                                                         = u
      override def headers: List[Header]                                                                            = h
      override def remoteAddress: Option[InetAddress]                                                               = ra
      override def decodeContent[R, B](decoder: ContentDecoder[R, Throwable, Chunk[Byte], B]): ZIO[R, Throwable, B] =
        content match {
          case HttpData.Empty                => ZIO.fail(ContentDecoder.Error.DecodeEmptyContent)
          case HttpData.Text(data, charset)  =>
            for {
              a   <- decoder match {
                case Text                                     => ZIO(Option(data.asInstanceOf[B]))
                case step: ContentDecoder.Step[_, _, _, _, _] =>
                  step
                    .asInstanceOf[ContentDecoder.Step[R, Throwable, Any, Chunk[Byte], B]]
                    .next(Chunk.fromArray(data.getBytes(charset)), step.state, true)
                    .map(a => a._1)
              }
              res <- contentFromOption(a)
            } yield res
          case HttpData.BinaryStream(stream) =>
            for {
              a   <- decoder match {
                case Text =>
                  stream
                    .fold(Unpooled.compositeBuffer())((s, b) => s.writeBytes(Array(b)))
                    .map(b => Option(b.toString(HTTP_CHARSET).asInstanceOf[B]))

                case step: ContentDecoder.Step[_, _, _, _, _] =>
                  stream
                    .fold(Unpooled.compositeBuffer())((s, b) => s.writeBytes(Array(b)))
                    .map(_.array())
                    .map(Chunk.fromArray(_))
                    .flatMap(
                      step
                        .asInstanceOf[ContentDecoder.Step[R, Throwable, Any, Chunk[Byte], B]]
                        .next(_, step.state, true)
                        .map(a => a._1),
                    )
              }
              res <- contentFromOption(a)
            } yield res
          case HttpData.Binary(data)         =>
            for {
              a   <- decoder match {
                case Text => ZIO(Some((new String(data.toArray, HTTP_CHARSET)).asInstanceOf[B]))
                case step: ContentDecoder.Step[_, _, _, _, _] =>
                  step
                    .asInstanceOf[ContentDecoder.Step[R, Throwable, Any, Chunk[Byte], B]]
                    .next(data, step.state, true)
                    .map(a => a._1)
              }
              res <- contentFromOption(a)
            } yield res
          case HttpData.BinaryN(data)        =>
            for {
              a   <- decoder match {
                case Text                                     => ZIO(Some(data.toString(HTTP_CHARSET).asInstanceOf[B]))
                case step: ContentDecoder.Step[_, _, _, _, _] =>
                  step
                    .asInstanceOf[ContentDecoder.Step[R, Throwable, Any, Chunk[Byte], B]]
                    .next(Chunk.fromArray(ByteBufUtil.getBytes(data)), step.state, true)
                    .map(a => a._1)
              }
              res <- contentFromOption(a)
            } yield res
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
    content: HttpData[R, E] = HttpData.empty,
  ): ZIO[R, Nothing, Request] =
    for {
      r <- ZIO.environment[R]
      c = content.provide(r)
    } yield Request(method, url, headers, remoteAddress, c)

  private def contentFromOption[B](a: Option[B]): Task[B] = {
    a match {
      case Some(value) => ZIO(value)
      case None        => ZIO.fail(ContentDecoder.Error.DecodeEmptyContent)
    }
  }
}
