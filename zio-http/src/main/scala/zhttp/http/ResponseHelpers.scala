package zhttp.http

import zhttp.http.Response.{Decode, Decoder, FromJResponse, HttpResponse}
import io.netty.handler.codec.http.{HttpResponse => JHttpResponse}
import zhttp.core.{Direction, HBuf1}
import zhttp.socket.{Socket, SocketApp, WebSocketFrame}
import zio.stm.TQueue

import java.io.{PrintWriter, StringWriter}

private[zhttp] trait ResponseHelpers {
  private val defaultStatus  = Status.OK
  private val defaultHeaders = Nil

  // Helpers

  /**
   * Creates a new Http Response
   * @deprecated
   *   use `Response(status = ???, headers = ???, content = ???)`
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
        Response(
          error.status,
          Nil,
          HttpData.fromString(cause.cause match {
            case Some(throwable) =>
              val sw = new StringWriter
              throwable.printStackTrace(new PrintWriter(sw))
              s"${cause.message}:\n${sw.toString}"
            case None            => s"${cause.message}"
          }),
        )
      case _                         =>
        Response(
          status = error.status,
          content = HttpData.fromString(error.message),
        )
    }

  }

  def ok: UResponse = Response(Status.OK)

  def text(text: String): UResponse =
    Response(
      content = HttpData.fromString(text),
      headers = Header.contentTypeTextPlain :: Nil,
    )

  def jsonString(data: String): UResponse =
    Response(
      content = HttpData.fromString(data),
      headers = Header.contentTypeJson :: Nil,
    )

  def status(status: Status): UResponse = Response(status)

  def fromJResponse(jRes: JHttpResponse): Response[Any, Nothing] = FromJResponse(jRes)

  def decode[R, E, A](decoder: Decoder[A])(cb: A => Response[R, E]): Response[R, E] = Decode(decoder, cb)

  def decodeComplete[R, E](maxSize: Int)(cb: HBuf1[Direction.In] => Response[R, E]): Response[R, E] =
    Response.decode(Decoder.Complete(maxSize))(cb)

  def decodeBuffered[R, E](maxSize: Int)(cb: TQueue[HBuf1[Direction.In]] => Response[R, E]): Response[R, E] =
    Response.decode(Decoder.Buffered(maxSize))(cb)
}
