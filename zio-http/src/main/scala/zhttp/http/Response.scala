package zhttp.http

import io.netty.handler.codec.http.HttpHeaderNames
import zhttp.socket.{Socket, SocketApp, WebSocketFrame}
import zio.Chunk

import java.io.{PrintWriter, StringWriter}
import java.time.Instant

case class Response[-R, +E] private (
  status: Status,
  headers: List[Header],
  data: HttpData[R, E],
  private[zhttp] val attribute: HttpAttribute[R, E],
) extends HeadersHelpers { self =>

  /**
   * add cookies in response headers
   */
  def addCookie(cookie: Cookie): Response[R, E] =
    self.copy(headers = self.headers ++ List(Header.custom(HttpHeaderNames.SET_COOKIE.toString, cookie.asString)))

  /**
   * get cookies from response
   */
  def getCookies: List[Cookie] = getHeaderValues(HttpHeaderNames.SET_COOKIE).flatMap(Cookie.parse(_) match {
    case Left(_)      => Nil
    case Right(value) => List(value)
  })

  /**
   * remove cookie from response headers
   */
  def removeCookie(cookie: Cookie): Response[R, E] =
    self.copy(headers =
      self.headers ++ List(Header.custom(HttpHeaderNames.SET_COOKIE.toString, cookie.clearCookie.asString)),
    )

  /**
   * remove cookie from response headers
   */
  def removeCookie(cookie: String): Response[R, E] =
    self.copy(headers =
      self.headers ++ List(
        Header.custom(
          HttpHeaderNames.SET_COOKIE.toString,
          Cookie(cookie, content = "", expires = Some(Instant.ofEpochSecond(0))).asString,
        ),
      ),
    )

}

object Response {

  def apply[R, E](
    status: Status = Status.OK,
    headers: List[Header] = Nil,
    data: HttpData[R, E] = HttpData.Empty,
  ): Response[R, E] =
    Response(status, headers, data, HttpAttribute.empty)

  @deprecated("Use `Response(status, headers, content)` constructor instead.", "22-Sep-2021")
  def http[R, E](
    status: Status = Status.OK,
    headers: List[Header] = Nil,
    data: HttpData[R, E] = HttpData.empty,
  ): Response[R, E] = Response(status, headers, data)

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
          HttpData.fromChunk(cause.cause match {
            case Some(throwable) =>
              val sw = new StringWriter
              throwable.printStackTrace(new PrintWriter(sw))
              Chunk.fromArray(s"${cause.message}:\n${sw.toString}".getBytes(HTTP_CHARSET))
            case None            => Chunk.fromArray(s"${cause.message}".getBytes(HTTP_CHARSET))
          }),
        )
      case _ => Response(error.status, Nil, HttpData.fromChunk(Chunk.fromArray(error.message.getBytes(HTTP_CHARSET))))
    }

  }

  def ok: UResponse = Response(Status.OK)

  def text(text: String): UResponse =
    Response(
      data = HttpData.fromChunk(Chunk.fromArray(text.getBytes(HTTP_CHARSET))),
      headers = List(Header.contentTypeTextPlain),
    )

  def jsonString(data: String): UResponse =
    Response(
      data = HttpData.fromChunk(Chunk.fromArray(data.getBytes(HTTP_CHARSET))),
      headers = List(Header.contentTypeJson),
    )

  def status(status: Status): UResponse = Response(status)

  def temporaryRedirect(location: String): Response[Any, Nothing] =
    Response(Status.TEMPORARY_REDIRECT, List(Header.location(location)))

  def permanentRedirect(location: String): Response[Any, Nothing] =
    Response(Status.PERMANENT_REDIRECT, List(Header.location(location)))

}
