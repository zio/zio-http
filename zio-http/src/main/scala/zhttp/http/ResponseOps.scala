package zhttp.http

import zhttp.http.Response.{HttpResponse, SocketResponse}
import zhttp.socket.Socket
import zio.Chunk

import java.io.{PrintWriter, StringWriter}

trait ResponseOps {
  private val defaultStatus  = Status.OK
  private val defaultHeaders = Nil
  private val emptyContent   = HttpContent.Complete(Chunk.empty)

  // Helpers

  /**
   * Creates a new Http Response
   */
  def http[R](
    status: Status = defaultStatus,
    headers: List[Header] = defaultHeaders,
    content: HttpContent[R, Byte] = emptyContent,
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
              Chunk.fromArray(s"${cause.message}:\n${sw.toString}".getBytes(HTTP_CHARSET))
            case None            => Chunk.fromArray(s"${cause.message}".getBytes(HTTP_CHARSET))
          }),
        )
      case _                         => http(error.status, Nil, HttpContent.Complete(Chunk.fromArray(error.message.getBytes(HTTP_CHARSET))))
    }

  }

  def ok: UResponse = http(Status.OK)

  def text(text: String): UResponse =
    http(
      content = HttpContent.Complete(Chunk.fromArray(text.getBytes(HTTP_CHARSET))),
      headers = List(Header.contentTypeTextPlain),
    )

  def jsonString(data: String): UResponse =
    http(
      content = HttpContent.Complete(Chunk.fromArray(data.getBytes(HTTP_CHARSET))),
      headers = List(Header.contentTypeJson),
    )

  def status(status: Status): UResponse = http(status)
}
