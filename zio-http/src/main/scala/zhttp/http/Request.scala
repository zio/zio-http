package zhttp.http

import zhttp.experiment.ContentDecoder
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

      override def decodeContent[R1, B](
        decoder: ContentDecoder[R1, Throwable, Chunk[Byte], B],
      ): ZIO[R1, Throwable, B] =
        for {
          a   <- ContentDecoder.decodeContent(decoder, c)
          res <- a match {
            case Some(value) => ZIO(value)
            case None        => ZIO.fail(ContentDecoder.Error.DecodeEmptyContent)
          }
        } yield res
    }
  }
}
