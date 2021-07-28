package zhttp.http

import zhttp.socket.{Socket, SocketApp, WebSocketFrame}
import zio.Chunk

import java.io.{PrintWriter, StringWriter}

// RESPONSE
sealed trait Response[-R, +E] extends Product with Serializable { self => }

object Response {
  // Constructors
  final case class HttpResponse[-R, +E](status: Status, headers: List[Header], content: HttpData[R, E])
      extends Response[R, E]
      with HasHeaders
      with HeadersHelpers

  final case class SocketResponse[-R, +E](socket: SocketApp[R, E] = SocketApp.empty) extends Response[R, E]

  private val defaultStatus  = Status.OK
  private val defaultHeaders = Nil

  // Helpers

  /**
   * Creates a new Http Response
   */
  def http[R, E](
    status: Status = defaultStatus,
    headers: List[Header] = defaultHeaders,
    content: HttpData[R, E] = HttpData.empty,
  ): Response.HttpResponse[R, E] =
    HttpResponse(status, headers, content)

  /**
   * Creates a new WebSocket Response
   */
  def socket[R, E](ss: SocketApp[R, E]): Response[R, E] = ss.asResponse

  /**
   * Creates a new WebSocket Response
   */
  def socket[R, E](ss: Socket[R, E, WebSocketFrame, WebSocketFrame]): Response[R, E] =
    SocketApp.message(ss).asResponse

  def fromHttpError(error: HttpError): UResponse = {
    error match {
      case cause: HTTPErrorWithCause =>
        http(
          error.status,
          Nil,
          HttpData.CompleteData(cause.cause match {
            case Some(throwable) =>
              val sw = new StringWriter
              throwable.printStackTrace(new PrintWriter(sw))
              Chunk.fromArray(s"${cause.message}:\n${sw.toString}".getBytes(HTTP_CHARSET))
            case None            => Chunk.fromArray(s"${cause.message}".getBytes(HTTP_CHARSET))
          }),
        )
      case _                         => http(error.status, Nil, HttpData.CompleteData(Chunk.fromArray(error.message.getBytes(HTTP_CHARSET))))
    }

  }

  def ok: UResponse = http(Status.OK)

  def text(text: String): UResponse =
    http(
      content = HttpData.CompleteData(Chunk.fromArray(text.getBytes(HTTP_CHARSET))),
      headers = List(Header.contentTypeTextPlain),
    )

  def jsonString(data: String): UResponse =
    http(
      content = HttpData.CompleteData(Chunk.fromArray(data.getBytes(HTTP_CHARSET))),
      headers = List(Header.contentTypeJson),
    )

  def status(status: Status): UResponse = http(status)

  def temporaryRedirect(location: String): HttpResponse[Any, Nothing] =
    HttpResponse(Status.TEMPORARY_REDIRECT, List(Header.location(location)), content = HttpData.empty)

  def permanentRedirect(location: String): HttpResponse[Any, Nothing] =
    HttpResponse(Status.PERMANENT_REDIRECT, List(Header.location(location)), content = HttpData.empty)

}
