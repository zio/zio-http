package zhttp.experiment

import io.netty.handler.codec.http.HttpRequest
import zhttp.http.{Header, Method, Status, URL}
import zio.stream.ZStream

object HttpMessage {

  /**
   * Request
   */
  trait HRequest { self =>
    def method: Method
    def url: URL
    def headers: List[Header]

    private[zhttp] def toCompleteRequest[A](content: A): CompleteRequest[A] =
      CompleteRequest(self, content)

    private[zhttp] def toBufferedRequest[A](content: ZStream[Any, Nothing, A]): BufferedRequest[A] =
      BufferedRequest(self, content)
  }

  type EmptyRequest[+A]

  case class AnyRequest[+A](override val method: Method, override val url: URL, override val headers: List[Header])
      extends HRequest

  object AnyRequest {
    def from(jReq: HttpRequest): AnyRequest[Nothing] = AnyRequest(
      // TODO: improve for performance
      method = Method.fromHttpMethod(jReq.method()),
      url = URL.fromString(jReq.uri()).getOrElse(null),
      headers = Header.make(jReq.headers()),
    )
  }

  case class CompleteRequest[+A](req: HRequest, content: A) extends HRequest {
    override def method: Method        = req.method
    override def url: URL              = req.url
    override def headers: List[Header] = req.headers
  }

  case class BufferedRequest[+A](req: HRequest, content: ZStream[Any, Nothing, A]) extends HRequest {
    override def method: Method        = req.method
    override def url: URL              = req.url
    override def headers: List[Header] = req.headers
  }

  /**
   * Response
   */
  case class HResponse[-R, +E, +A](
    status: Status = Status.OK,
    headers: List[Header] = Nil,
    content: HContent[R, E, A] = HContent.empty,
  )

  type CompleteResponse[+A] = HResponse[Any, Nothing, A]
  object CompleteResponse {
    def apply[A](
      status: Status = Status.OK,
      headers: List[Header] = Nil,
      content: A,
    ): CompleteResponse[A] = HResponse(status, headers, HContent.complete(content))
  }

  type BufferedResponse[-R, +E, +A] = HResponse[R, E, A]
  object BufferedResponse {
    def apply[R, E, A](
      status: Status = Status.OK,
      headers: List[Header] = Nil,
      content: ZStream[R, E, A] = ZStream.empty,
    ): BufferedResponse[R, E, A] = HResponse(status, headers, HContent.fromStream(content))
  }

}
