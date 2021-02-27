package zio-http.domain.http.model

import zio-http.core.extras._
import zio-http.core.netty.JFullHttpResponse
import zio-http.domain.http._
import zio-http.domain.socket.WebSocketFrame
import zio.Task
import zio.stream.ZStream

// RESPONSE
sealed trait Response extends Product with Serializable { self => }

object Response {
  private val defaultStatus  = Status.OK
  private val defaultHeaders = Nil
  private val defaultContent = HttpContent.Empty

  // Constructors
  final case class HttpResponse(status: Status, headers: List[Header], content: HttpContent[Any, String])
      extends Response

  final case class SocketResponse(
    socket: WebSocketFrame => ZStream[Any, Nothing, WebSocketFrame],
    subProtocol: Option[String],
  ) extends Response

  // Helpers

  /**
   * Creates a new Http Response
   */
  def http(
    status: Status = defaultStatus,
    headers: List[Header] = defaultHeaders,
    content: HttpContent[Any, String] = defaultContent,
  ): Response =
    HttpResponse(status, headers, content)

  /**
   * Creates a new WebSocket Response
   */
  def socket(subProtocol: Option[String])(socket: WebSocketFrame => ZStream[Any, Nothing, WebSocketFrame]): Response =
    SocketResponse(socket, subProtocol)

  /**
   * Sets the content length of the response
   */

  def setContentLength(response: Response): Response = response match {
    case m @ Response.HttpResponse(_, _, HttpContent.Empty) =>
      m.copy(headers = Header.emptyContent :: m.headers)

    case m @ Response.HttpResponse(_, _, HttpContent.Complete(body)) =>
      m.copy(headers = Header.contentLength(body.length) :: m.headers)

    case m => m
  }

  def fromHttpError(error: HttpError): Response = {
    error match {
      case cause: HTTPErrorWithCause =>
        http(
          error.status,
          Nil,
          HttpContent.Complete(cause.cause match {
            case Some(throwable) => s"${cause.message}:\n${throwable.getStackAsString}"
            case None            => s"${cause.message}"
          }),
        )
      case _                         => http(error.status, Nil, HttpContent.Complete(error.message))
    }

  }

  def ok: Response = http(Status.OK)

  def text(text: String): Response =
    http(
      content = HttpContent.Complete(text),
      headers = List(Header.contentLength(text.length)),
    )

  def jsonString(data: String): Response =
    http(
      content = HttpContent.Complete(data),
      headers = List(Header.contentTypeJson, Header.contentLength(data.length)),
    )

  def status(status: Status): Response = http(status, List(Header.emptyContent))

  def fromJFullHttpResponse(jRes: JFullHttpResponse): Task[Response] = Task {
    val status  = Status.fromJHttpResponseStatus(jRes.status())
    val headers = Header.parse(jRes.headers())
    val content = HttpContent.Complete(jRes.content().toString(HTTP_CHARSET))

    Response.http(status, headers, content)
  }
}
