package zhttp.http

import java.net.InetAddress

import zhttp.experiment.ContentDecoder
import zio.{Chunk, ZIO}

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
  private def checkWebSocketUpgrade: Boolean = self.getHeaderValue("Upgrade") match {
    case Some(value) if value.toLowerCase equals "websocket" => true
    case Some(_)                                             => false
    case None                                                => false
  }
  private def checkWebSocketKey: Boolean     = self getHeaderValue "Sec-WebSocket-Key" match {
    case Some(_) => true
    case None    => false
  }
  private def checkMethod: Boolean           = self.method.equals(Method.GET)
  private def checkScheme: Boolean           = ???

  def validateRequest: Boolean = checkWebSocketKey && checkWebSocketUpgrade && checkMethod && checkScheme
}

object Request {
  def apply(method: Method = Method.GET, url: URL = URL.root, headers: List[Header] = Nil): Request = {
    val m = method
    val u = url
    val h = headers
    new Request {
      override def method: Method = m

      override def url: URL = u

      override def headers: List[Header] = h

      override def remoteAddress: Option[InetAddress] = None

      override def decodeContent[R, B](decoder: ContentDecoder[R, Throwable, Chunk[Byte], B]): ZIO[R, Throwable, B] =
        ZIO.fail(ContentDecoder.Error.DecodeEmptyContent)
    }
  }
}
