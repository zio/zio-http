package zhttp.http

import zhttp.socket.{Socket, SocketApp, WebSocketFrame}
import zio.Chunk

import java.io.{PrintWriter, StringWriter}

private[zhttp] trait ResponseHelpers {
  private val defaultStatus  = Status.OK
  private val defaultHeaders = Nil

  // Helpers

  /**
   * Creates a new Http Response
   */
  def http[R, E, B: HasContent](
    status: Status = defaultStatus,
    headers: List[Header] = defaultHeaders,
    content: HttpData[R, E, B] = HttpData.empty,
  ): Response[R, E, B] =
    Response(status, headers, content)

  /**
   * Creates a new WebSocket Response
   */
  def socket[R, E](ss: SocketApp[R, E]): Response[R, E, Any] = ss.asResponse

  /**
   * Creates a new WebSocket Response
   */
  def socket[R, E](ss: Socket[R, E, WebSocketFrame, WebSocketFrame]): Response[R, E, Any] =
    SocketApp.message(ss).asResponse

  def fromHttpError(error: HttpError): Response[Any, Nothing, Complete] = {
    error match {
      case cause: HTTPErrorWithCause =>
        http(
          error.status,
          Nil,
          HttpData.fromBytes(cause.cause match {
            case Some(throwable) =>
              val sw = new StringWriter
              throwable.printStackTrace(new PrintWriter(sw))
              Chunk.fromArray(s"${cause.message}:\n${sw.toString}".getBytes(HTTP_CHARSET))
            case None            => Chunk.fromArray(s"${cause.message}".getBytes(HTTP_CHARSET))
          }),
        )
      case _                         => http(error.status, Nil, HttpData.fromBytes(Chunk.fromArray(error.message.getBytes(HTTP_CHARSET))))
    }

  }

  def ok: Response[Any, Nothing, Any] = http(Status.OK)

  def text(text: String): CompleteResponse =
    http(
      content = HttpData.fromBytes(Chunk.fromArray(text.getBytes(HTTP_CHARSET))),
      headers = List(Header.contentTypeTextPlain),
    )

  def jsonString(data: String): CompleteResponse =
    http(
      content = HttpData.fromBytes(Chunk.fromArray(data.getBytes(HTTP_CHARSET))),
      headers = List(Header.contentTypeJson),
    )

  def status(status: Status): Response[Any, Nothing, Any] = http(status)

}
