package zhttp.http

import io.netty.buffer.{ByteBufUtil, Unpooled}
import zhttp.experiment.ContentDecoder
import zhttp.experiment.ContentDecoder.Text
import zio.{Chunk, ZIO}

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

      override def decodeContent[R, B](
        decoder: ContentDecoder[R, Throwable, Chunk[Byte], B],
      ): ZIO[R, Throwable, B] =
        self.decodeContent(decoder)
    }
  }
}

object Request {
  def apply[R, E <: Throwable](
    method: Method = Method.GET,
    url: URL = URL.root,
    headers: List[Header] = Nil,
    content: RequestContent[R, E] = RequestContent.empty,
  ): ZIO[R, Nothing, Request] = {
    val m = method
    val u = url
    val h = headers

    for {
      r <- ZIO.environment[R]
      c = RequestContent.provide(r, content)

    } yield new Request {
      override def method: Method = m

      override def url: URL = u

      override def headers: List[Header] = h

      override def remoteAddress: Option[InetAddress] = None

      private def decodeContent0[R2, E2 <: Throwable, B](
        decoder: ContentDecoder[R2, Throwable, Chunk[Byte], B],
        content: RequestContent[Any, E2],
      ): ZIO[R2, Throwable, Option[B]] =
        decoder match {
          case Text                                     =>
            content match {
              case RequestContent.Empty           => ZIO.fail(ContentDecoder.Error.DecodeEmptyContent)
              case RequestContent.Text(text, _)   => ZIO(Option(text))
              case RequestContent.Binary(data)    => ZIO(Some(new String(data.toArray, HTTP_CHARSET)))
              case RequestContent.BinaryN(data)   => ZIO(Option(data.toString(HTTP_CHARSET)))
              case RequestContent.BinaryStream(s) =>
                s.fold(Unpooled.compositeBuffer())((s, b) => s.writeBytes(Array(b)))
                  .map(b => Option(b.toString(HTTP_CHARSET)))

            }
          case step: ContentDecoder.Step[_, _, _, _, _] =>
            content match {
              case RequestContent.Empty               => ZIO.fail(ContentDecoder.Error.DecodeEmptyContent)
              case RequestContent.Text(data, charset) =>
                step
                  .asInstanceOf[ContentDecoder.Step[R2, Throwable, Any, Chunk[Byte], B]]
                  .next(Chunk.fromArray(data.getBytes(charset)), step.state, true)
                  .map(a => a._1)
              case RequestContent.Binary(data)        =>
                step
                  .asInstanceOf[ContentDecoder.Step[R2, Throwable, Any, Chunk[Byte], B]]
                  .next(data, step.state, true)
                  .map(a => a._1)
              case RequestContent.BinaryN(data)       =>
                step
                  .asInstanceOf[ContentDecoder.Step[R2, Throwable, Any, Chunk[Byte], B]]
                  .next(Chunk.fromArray(ByteBufUtil.getBytes(data)), step.state, true)
                  .map(a => a._1)
              case RequestContent.BinaryStream(s)     =>
                s.fold(Unpooled.compositeBuffer())((s, b) => s.writeBytes(Array(b)))
                  .map(_.array())
                  .map(Chunk.fromArray(_))
                  .flatMap(
                    step
                      .asInstanceOf[ContentDecoder.Step[R2, Throwable, Any, Chunk[Byte], B]]
                      .next(_, step.state, true)
                      .map(a => a._1),
                  )
            }
        }

      override def decodeContent[R1, B](
        decoder: ContentDecoder[R1, Throwable, Chunk[Byte], B],
      ): ZIO[R1, Throwable, B] =
        for {
          a   <- decodeContent0(decoder, c)
          res <- a match {
            case Some(value) => ZIO(value)
            case None        => ZIO.fail(ContentDecoder.Error.DecodeEmptyContent)
          }
        } yield res
    }
  }
}
