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

      override def decodeContent[R, B](decoder: ContentDecoder[R, Throwable, Chunk[Byte], B]): ZIO[R, Throwable, B] =
        self.decodeContent(decoder)
    }
  }
}

object Request {
  def apply[R1<: R,E1>:E](method: Method = Method.GET, url: URL = URL.root, headers: List[Header] = Nil, content: HttpData[R1,E1]=HttpData.empty): Request = {
    val m = method
    val u = url
    val h = headers
    new Request {
      override def method: Method = m

      override def url: URL = u

      override def headers: List[Header] = h

      override def remoteAddress: Option[InetAddress] = None

      override def decodeContent[R, B](decoder: ContentDecoder[R, Throwable, Chunk[Byte], B]): ZIO[R, Throwable, B] =
        for {
          v   <- decoder.getContent(content)
          res <- v match {
            case Some(value) => ZIO.succeed(value)
            case None        => ZIO.fail(ContentDecoder.Error.DecodeEmptyContent)
          }
        } yield res
    }
  }
}
