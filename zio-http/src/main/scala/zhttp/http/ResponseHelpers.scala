package zhttp.http

import zhttp.http.Response.HttpResponse
import zhttp.socket.{Socket, SocketApp, WebSocketFrame}

import java.io.{PrintWriter, StringWriter}

private[zhttp] trait ResponseHelpers {
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
          HttpData.fromText(cause.cause match {
            case Some(throwable) =>
              val sw = new StringWriter
              throwable.printStackTrace(new PrintWriter(sw))
              s"${cause.message}:\n${sw.toString}"
            case None            => s"${cause.message}"
          }),
        )
      case _                         => http(error.status, Nil, HttpData.fromText(error.message))
    }

  }

  def ok: UResponse = http(Status.OK)

  def text(text: String): UResponse =
    http(
      content = HttpData.fromText(text),
      headers = List(Header.contentTypeTextPlain),
    )

  def jsonString(data: String): UResponse =
    http(
      content = HttpData.fromText(data),
      headers = List(Header.contentTypeJson),
    )

  def status(status: Status): UResponse = http(status)

  def temporaryRedirect(location: String): HttpResponse[Any, Nothing] =
    HttpResponse(Status.TEMPORARY_REDIRECT, List(Header.location(location)), content = HttpData.empty)

  def permanentRedirect(location: String): HttpResponse[Any, Nothing] =
    HttpResponse(Status.PERMANENT_REDIRECT, List(Header.location(location)), content = HttpData.empty)

}
