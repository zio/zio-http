package zhttp.experiment

import zhttp.http._
import zio._
import zio.stream.ZStream

import java.net.InetAddress

sealed trait HttpMessage

object HttpMessage {

  /**
   * Request
   */
  trait AnyRequest extends HttpMessage { self =>
    def method: Method
    def url: URL
    def headers: List[Header]

    def path: Path = url.path
    def decodeContent[R, E, B](decoder: ContentDecoder[R, E, B]): ZIO[R, E, B]

    def remoteAddress: Option[InetAddress]

    def withMethod(method: Method): AnyRequest = self.copy(method = method)

    def addHeader(header: Header): AnyRequest = self.copy(headers = header :: self.headers)

    def removeHeader(name: CharSequence): AnyRequest = self.copy(headers = self.headers.filter(_.name != name))

    def copy(method: Method = self.method, url: URL = self.url, headers: List[Header] = self.headers): AnyRequest = {
      val m = method
      val u = url
      val h = headers
      new AnyRequest {
        override def method: Method = m

        override def url: URL = u

        override def headers: List[Header] = h

        override def remoteAddress: Option[InetAddress] =
          self.remoteAddress

        override def decodeContent[R, E, B](decoder: ContentDecoder[R, E, B]): ZIO[R, E, B] =
          self.decodeContent(decoder)
      }
    }

  }

  /**
   * Response
   */
  case class AnyResponse[-R, +E](
    status: Status = Status.OK,
    headers: List[Header] = Nil,
    content: Content[R, E] = Content.empty,
  ) extends HttpMessage

  type EmptyResponse = AnyResponse[Any, Nothing]
  object EmptyResponse {
    def apply(
      status: Status = Status.OK,
      headers: List[Header] = Nil,
    ): EmptyResponse = AnyResponse(status, headers)
  }

  type CompleteResponse = AnyResponse[Any, Nothing]
  object CompleteResponse {
    def apply[A](
      status: Status = Status.OK,
      headers: List[Header] = Nil,
      content: String,
    ): CompleteResponse = AnyResponse(status, headers, Content.text(content))
  }

  type BufferedResponse[-R, +E] = AnyResponse[R, E]
  object BufferedResponse {
    def apply[R, E](
      status: Status = Status.OK,
      headers: List[Header] = Nil,
      content: ZStream[R, E, Byte] = ZStream.empty,
    ): BufferedResponse[R, E] = AnyResponse(status, headers, Content.fromStream(content))
  }

  case class AnyContent[+A](content: A, isLast: Boolean) extends HttpMessage
}
