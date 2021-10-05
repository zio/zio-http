package zhttp.http

import zhttp.experiment.ContentDecoder
import zio.{Chunk, ZIO}

import java.net.InetAddress

trait Request[-R, +E] extends HeadersHelpers { self =>
  def method: Method

  def url: URL

  def headers: List[Header]

  def path: Path = url.path

  def decodeContent[R1 <: R, B](decoder: ContentDecoder[R1, Throwable, Chunk[Byte], B]): ZIO[R1, Throwable, B]

  def remoteAddress: Option[InetAddress]

  def addHeader(header: Header): Request[R, E] = self.copy(headers = header :: self.headers)

  def removeHeader(name: CharSequence): Request[R, E] = self.copy(headers = self.headers.filter(_.name != name))

  def copy(method: Method = self.method, url: URL = self.url, headers: List[Header] = self.headers): Request[R, E] = {
    val m = method
    val u = url
    val h = headers
    new Request[R, E] {
      override def method: Method = m

      override def url: URL = u

      override def headers: List[Header] = h

      override def remoteAddress: Option[InetAddress] =
        self.remoteAddress

      override def decodeContent[R1 <: R, B](
        decoder: ContentDecoder[R1, Throwable, Chunk[Byte], B],
      ): ZIO[R1, Throwable, B] =
        self.decodeContent(decoder)
    }
  }
}

object Request {
  def apply[R, E](
    method: Method = Method.GET,
    url: URL = URL.root,
    headers: List[Header] = Nil,
    content: HttpData[R, E] = HttpData.Empty,
  ): Request[R, E] = {
    val m = method
    val u = url
    val h = headers
    new Request[R, E] {
      override def method: Method = m

      override def url: URL = u

      override def headers: List[Header] = h

      override def remoteAddress: Option[InetAddress] = None

      override def decodeContent[R1 <: R, B](
        decoder: ContentDecoder[R1, Throwable, Chunk[Byte], B],
      ): ZIO[R1, Throwable, B] =
        for {
          a   <- ContentDecoder.decodeContent(decoder, content)
          res <- a match {
            case Some(value) => ZIO(value)
            case None        => ZIO.fail(ContentDecoder.Error.DecodeEmptyContent)
          }
        } yield res
    }
  }
}
