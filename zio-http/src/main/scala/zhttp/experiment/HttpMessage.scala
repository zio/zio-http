package zhttp.experiment

import io.netty.handler.codec.http.HttpRequest
import zhttp.http.{Header, Method, Status, URL}
import zio.Chunk
import zio.stream.ZStream

object HttpMessage {

  /**
   * Request
   */
  trait HRequest { self =>
    def method: Method
    def url: URL
    def headers: List[Header]

    private[zhttp] def toCompleteRequest(content: Chunk[Byte]): CompleteRequest =
      CompleteRequest(self, content)

    private[zhttp] def toBufferedRequest(content: ZStream[Any, Nothing, Byte]): BufferedRequest =
      BufferedRequest(self, content)
  }

  case class AnyRequest(override val method: Method, override val url: URL, override val headers: List[Header])
      extends HRequest

  object AnyRequest {
    def from(jReq: HttpRequest): AnyRequest = ???
  }

  case class CompleteRequest(req: HRequest, content: Chunk[Byte]) extends HRequest {
    override def method: Method        = req.method
    override def url: URL              = req.url
    override def headers: List[Header] = req.headers
  }

  case class BufferedRequest(req: HRequest, content: ZStream[Any, Nothing, Byte]) extends HRequest {
    override def method: Method        = req.method
    override def url: URL              = req.url
    override def headers: List[Header] = req.headers
  }

  /**
   * Response
   */
  case class HResponse[-R, +E](
    status: Status = Status.OK,
    headers: List[Header] = Nil,
    content: HContent[R, E] = HContent.empty,
  )

  type CompleteResponse = HResponse[Any, Nothing]
  object CompleteResponse {
    def apply(
      status: Status = Status.OK,
      headers: List[Header] = Nil,
      content: Chunk[Byte] = Chunk.empty,
    ): CompleteResponse = HResponse(status, headers, HContent.from(content))
  }

  type BufferedResponse[-R, +E] = HResponse[R, E]
  object BufferedResponse {
    def apply[R, E](
      status: Status = Status.OK,
      headers: List[Header] = Nil,
      content: ZStream[R, E, Byte] = ZStream.empty,
    ): BufferedResponse[R, E] = HResponse(status, headers, HContent.from(content))
  }

}
