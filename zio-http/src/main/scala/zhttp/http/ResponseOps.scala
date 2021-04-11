package zhttp.http

import zhttp.http.Response.{HttpResponse, SocketResponse}
import zhttp.socket.Socket

import java.io.{PrintWriter, StringWriter}

trait ResponseOps {
  private val defaultStatus  = Status.OK
  private val defaultHeaders = Nil
  private val emptyContent   = HttpContent.Complete("")

  // Helpers

  /**
   * Creates a new Http Response
   */
  def http[R](
    status: Status = defaultStatus,
    headers: List[Header] = defaultHeaders,
    content: HttpContent[R, String] = emptyContent,
  ): Response.HttpResponse[R] =
    HttpResponse(status, headers, content)

  /**
   * Creates a new WebSocket Response with a sub-protocol
   */
  def socket[R, E](ss: Socket[R, E]): Response[R, E] = SocketResponse(ss)

  def fromHttpError(error: HttpError): UResponse = {
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

  def ok: UResponse = http(Status.OK)

  def text(text: String): UResponse =
    http(
      content = HttpContent.Complete(text),
      headers = List(Header.contentTypeTextPlain),
    )

  def jsonString(data: String): UResponse =
    http(
      content = HttpContent.Complete(data),
      headers = List(Header.contentTypeJson),
    )

  def status(status: Status): UResponse = http(status)
}
