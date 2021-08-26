package zhttp.experiment

import io.netty.handler.codec.http.HttpRequest
import zhttp.http.{Header, Method, Status, URL}
import zio.stream.ZStream
import zio.ZDequeue

sealed trait HttpMessage

object HttpMessage {

  /**
   * Request
   */
  sealed trait AnyRequest extends HttpMessage { self =>
    def method: Method
    def url: URL
    def headers: List[Header]

    private[zhttp] def toCompleteRequest[A](content: A): CompleteRequest[A] =
      CompleteRequest(self, content)

    private[zhttp] def toBufferedRequest[A](content: ZDequeue[Any, Nothing, A]): BufferedRequest[A] =
      BufferedRequest(self, content)
  }

  object AnyRequest {
    case class Default(override val method: Method, override val url: URL, override val headers: List[Header])
        extends AnyRequest

    case class FromJRequest(jReq: HttpRequest) extends AnyRequest {
      override def method: Method        = Method.fromHttpMethod(jReq.method())
      override def url: URL              = URL.fromString(jReq.uri()).getOrElse(null)
      override def headers: List[Header] = Header.make(jReq.headers())
    }

    private[zhttp] def from(jReq: HttpRequest): AnyRequest = FromJRequest(jReq)
  }

  case class CompleteRequest[+A](req: AnyRequest, content: A) extends AnyRequest {
    override def method: Method        = req.method
    override def url: URL              = req.url
    override def headers: List[Header] = req.headers
  }

  case class BufferedRequest[+A](req: AnyRequest, content: ZDequeue[Any, Nothing, A]) extends AnyRequest {
    override def method: Method        = req.method
    override def url: URL              = req.url
    override def headers: List[Header] = req.headers
  }

  /**
   * Response
   */
  case class AnyResponse[-R, +E, +A](
    status: Status = Status.OK,
    headers: List[Header] = Nil,
    content: Content[R, E, A] = Content.empty,
  ) extends HttpMessage

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
