package zhttp.http

import io.netty.buffer.{Unpooled => JUnpooled}
import io.netty.handler.codec.http.{HttpHeaderNames => JHttpHeaderNames, HttpVersion => JHttpVersion}
import zhttp.core.{JDefaultFullHttpResponse, JDefaultHttpHeaders, JFullHttpResponse, JHttpHeaders}
import zhttp.socket.WebSocketFrame
import zio.Task
import zio.stream.ZStream

import java.io.{PrintWriter, StringWriter}
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

// RESPONSE
sealed trait Response extends Product with Serializable { self => }

object Response {
  private val defaultStatus    = Status.OK
  private val defaultHeaders   = List(Header(JHttpHeaderNames.SERVER, "zio-http"))
  private val defaultContent   = HttpContent.Empty
  private val jTrailingHeaders = new JDefaultHttpHeaders(false)

  // Constructors
  final case class HttpResponse(status: Status, headers: List[Header], content: HttpContent[Any, String])
      extends Response { res =>

    /**
     * Encode the [[Response]] to [io.netty.handler.codec.http.FullHttpResponse]
     */
    def toJFullHttpResponse: JFullHttpResponse = {
      val jHttpHeaders   =
        res.headers.foldLeft[JHttpHeaders](new JDefaultHttpHeaders()) { (jh, hh) =>
          jh.set(hh.name, hh.value)
        }
      val jStatus        = res.status.toJHttpStatus
      val jContentBytBuf = res.content match {
        case HttpContent.Complete(data) =>
          jHttpHeaders.set(JHttpHeaderNames.CONTENT_LENGTH, data.length())
          JUnpooled.copiedBuffer(data, HTTP_CHARSET)

        case _ =>
          jHttpHeaders.set(JHttpHeaderNames.CONTENT_LENGTH, 0)
          JUnpooled.buffer(0)
      }

      new JDefaultFullHttpResponse(JHttpVersion.HTTP_1_1, jStatus, jContentBytBuf, jHttpHeaders, jTrailingHeaders)
    }
  }

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
  ): Response = {
    val createDate: String = DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now)
    HttpResponse(status, headers concat List(Header(JHttpHeaderNames.DATE, s"$createDate")), content)
  }

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
      headers = defaultHeaders concat List(Header.contentTypeTextPlain),
    )

  def jsonString(data: String): Response =
    http(
      content = HttpContent.Complete(data),
      headers = defaultHeaders concat List(Header.contentTypeJson),
    )

  def status(status: Status): Response = http(status)

  def fromJFullHttpResponse(jRes: JFullHttpResponse): Task[Response] = Task {
    val status  = Status.fromJHttpResponseStatus(jRes.status())
    val headers = Header.parse(jRes.headers())
    val content = HttpContent.Complete(jRes.content().toString(HTTP_CHARSET))

    Response.http(status, headers, content)
  }
}
