package zhttp.http

import zhttp.core.JFullHttpResponse
import zhttp.socket.WebSocketFrame
import zio.Task
import zio.stream.ZStream

import java.io.{PrintWriter, StringWriter}

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

  def fromHttpError(error: HttpError): Response = {
    error match {
      case cause: HTTPErrorWithCause =>
        http(
          error.status,
          Nil,
          HttpContent.Complete(cause.cause match {
            case Some(throwable) =>
              val sw = new StringWriter
              throwable.printStackTrace(new PrintWriter(sw))
              s"${cause.message}:\n${sw.toString}"
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
      headers = List(Header.contentTypeTextPlain),
    )

  def jsonString(data: String): Response =
    http(
      content = HttpContent.Complete(data),
      headers = List(Header.contentTypeJson),
    )

  def status(status: Status): Response = http(status)

  def fromJFullHttpResponse(jRes: JFullHttpResponse): Task[Response] = Task {
    val status  = Status.fromJHttpResponseStatus(jRes.status())
    val headers = Header.parse(jRes.headers())
    val content = HttpContent.Complete(jRes.content().toString(HTTP_CHARSET))

    Response.http(status, headers, content)
  }
}
