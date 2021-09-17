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
