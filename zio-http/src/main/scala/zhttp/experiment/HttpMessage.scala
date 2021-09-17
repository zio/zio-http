package zhttp.experiment

import zhttp.http._
import zio._
import zio.stream.ZStream

sealed trait HttpMessage

object HttpMessage {

  /**
   * Request
   */
  trait AnyRequest extends HttpMessage {
    def method: Method
    def url: URL
    def path: Path = url.path
    def headers: List[Header]
    def decodeContent[R, E, B](decoder: ContentDecoder[R, E, B]): ZIO[R, E, B]
  }

  /**
   * Response
   */
  case class AnyResponse[-R, +E, +A](
    status: Status = Status.OK,
    headers: List[Header] = Nil,
    content: Content[R, E, A] = Content.empty,
  ) extends HttpMessage { self =>
    def map[B](ab: A => B): AnyResponse[R, E, B] = self.copy(content = self.content.map(ab))
  }

  type EmptyResponse = AnyResponse[Any, Nothing, Nothing]
  object EmptyResponse {
    def apply(
      status: Status = Status.OK,
      headers: List[Header] = Nil,
    ): EmptyResponse = AnyResponse(status, headers)
  }

  type CompleteResponse[+A] = AnyResponse[Any, Nothing, A]
  object CompleteResponse {
    def apply[A](
      status: Status = Status.OK,
      headers: List[Header] = Nil,
      content: A,
    ): CompleteResponse[A] = AnyResponse(status, headers, Content.complete(content))
  }

  type BufferedResponse[-R, +E, +A] = AnyResponse[R, E, A]
  object BufferedResponse {
    def apply[R, E, A](
      status: Status = Status.OK,
      headers: List[Header] = Nil,
      content: ZStream[R, E, A] = ZStream.empty,
    ): BufferedResponse[R, E, A] = AnyResponse(status, headers, Content.fromStream(content))
  }

  case class AnyContent[+A](content: A, isLast: Boolean) extends HttpMessage
}
