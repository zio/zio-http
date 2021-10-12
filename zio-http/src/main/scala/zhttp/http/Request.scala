package zhttp.http

import io.netty.buffer.Unpooled
import zhttp.experiment.ContentDecoder
import zhttp.experiment.ContentDecoder.Text
import zio.stream.ZStream
import zio.{Chunk, ZIO}

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
  def apply(method: Method = Method.GET, url: URL = URL.root, headers: List[Header] = Nil): Request = {
    val m = method
    val u = url
    val h = headers
    new Request {
      override def method: Method                     = m
      override def url: URL                           = u
      override def headers: List[Header]              = h
      override def remoteAddress: Option[InetAddress] = None
      override def decodeContent[R, B](decoder: ContentDecoder[R, Throwable, Chunk[Byte], B]): ZIO[R, Throwable, B] =
        ZIO.fail(ContentDecoder.Error.DecodeEmptyContent)
    }
  }

  def make[R, E <: Throwable](
    method: Method,
    url: URL,
    headers: List[Header],
    content: String,
  ): ZIO[R, Nothing, Request] = {
    val m = method
    val u = url
    val h = headers
    ZIO.succeed {
      new Request {
        override def method: Method                     = m
        override def url: URL                           = u
        override def headers: List[Header]              = h
        override def remoteAddress: Option[InetAddress] = None
        override def decodeContent[R1, B](
          decoder: ContentDecoder[R1, Throwable, Chunk[Byte], B],
        ): ZIO[R1, Throwable, B]                        =
          for {
            a   <- decoder match {
              case Text                                     => ZIO(Option(content.asInstanceOf[B]))
              case step: ContentDecoder.Step[_, _, _, _, _] =>
                step
                  .asInstanceOf[ContentDecoder.Step[R1, Throwable, Any, Chunk[Byte], B]]
                  .next(Chunk.fromArray(content.getBytes(HTTP_CHARSET)), step.state, true)
                  .map(a => a._1)
            }
            res <- a match {
              case Some(value) => ZIO(value)
              case None        => ZIO.fail(ContentDecoder.Error.DecodeEmptyContent)
            }
          } yield res
      }
    }
  }

  def make[R, E <: Throwable](
    method: Method,
    url: URL,
    headers: List[Header],
    content: Chunk[Byte],
  ): ZIO[R, Nothing, Request] = {
    val m = method
    val u = url
    val h = headers
    ZIO.succeed {
      new Request {
        override def method: Method                     = m
        override def url: URL                           = u
        override def headers: List[Header]              = h
        override def remoteAddress: Option[InetAddress] = None
        override def decodeContent[R1, B](
          decoder: ContentDecoder[R1, Throwable, Chunk[Byte], B],
        ): ZIO[R1, Throwable, B]                        =
          for {
            a   <- decoder match {
              case Text => ZIO(Some((new String(content.toArray, HTTP_CHARSET)).asInstanceOf[B]))
              case step: ContentDecoder.Step[_, _, _, _, _] =>
                step
                  .asInstanceOf[ContentDecoder.Step[R1, Throwable, Any, Chunk[Byte], B]]
                  .next(content, step.state, true)
                  .map(a => a._1)
            }
            res <- a match {
              case Some(value) => ZIO(value)
              case None        => ZIO.fail(ContentDecoder.Error.DecodeEmptyContent)
            }
          } yield res
      }
    }
  }

  def make[R, E <: Throwable](
    method: Method,
    url: URL,
    headers: List[Header],
    content: ZStream[R, E, Byte],
  ): ZIO[R, Nothing, Request] = {
    val m = method
    val u = url
    val h = headers
    for {
      r <- ZIO.environment[R]
      c = content.provide(r)
    } yield new Request {
      override def method: Method                     = m
      override def url: URL                           = u
      override def headers: List[Header]              = h
      override def remoteAddress: Option[InetAddress] = None
      override def decodeContent[R1, B](
        decoder: ContentDecoder[R1, Throwable, Chunk[Byte], B],
      ): ZIO[R1, Throwable, B]                        =
        for {
          a   <- decoder match {
            case Text =>
              c.fold(Unpooled.compositeBuffer())((s, b) => s.writeBytes(Array(b)))
                .map(b => Option(b.toString(HTTP_CHARSET).asInstanceOf[B]))

            case step: ContentDecoder.Step[_, _, _, _, _] =>
              c.fold(Unpooled.compositeBuffer())((s, b) => s.writeBytes(Array(b)))
                .map(_.array())
                .map(Chunk.fromArray(_))
                .flatMap(
                  step
                    .asInstanceOf[ContentDecoder.Step[R1, Throwable, Any, Chunk[Byte], B]]
                    .next(_, step.state, true)
                    .map(a => a._1),
                )
          }
          res <- a match {
            case Some(value) => ZIO(value)
            case None        => ZIO.fail(ContentDecoder.Error.DecodeEmptyContent)
          }
        } yield res
    }
  }
}
