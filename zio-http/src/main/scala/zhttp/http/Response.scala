package zhttp.http

import io.netty.handler.codec.http.HttpHeaderNames
import zhttp.http.HttpError.HTTPErrorWithCause
import zhttp.socket.{Socket, SocketApp, WebSocketFrame}
import zio.Chunk

import java.io.{PrintWriter, StringWriter}

case class Response[-R, +E] private (
  status: Status,
  headers: List[Header],
  data: HttpData[R, E],
  private[zhttp] val attribute: HttpAttribute[R, E],
) extends HeaderExtension[Response[R, E]] { self =>

  /**
   * Adds cookies in the response headers
   */
  def addCookie(cookie: Cookie): Response[R, E] =
    self.copy(headers = self.headers ++ List(Header.custom(HttpHeaderNames.SET_COOKIE.toString, cookie.encode)))

  /**
   * Adds headers to response
   */
  override def addHeaders(headers: List[Header]): Response[R, E] =
    self.copy(headers = self.headers ++ headers)

  /**
   * Gets the http [[Method]] of this request.
   */
  /**
   * Gets content length of the response, if possible.
   */
  def getContentLength: Option[Long] = self.data.size

  /**
   * Removes headers by name from the response
   */
  override def removeHeaders(headers: List[String]): Response[R, E] =
    self.copy(headers = self.headers.filterNot(h => headers.contains(h.name)))

  /**
   * Automatically detects the size of the content and sets it
   */
  def setPayloadHeaders: Response[R, E] = {
    getContentLength match {
      case Some(value) => setContentLength(value)
      case None        => setTransferEncodingChunked
    }
  }

  /**
   * Sets the status of the response
   */
  def setStatus(status: Status): Response[R, E] =
    self.copy(status = status)
}

object Response {
  def apply[R, E](
    status: Status = Status.OK,
    headers: List[Header] = Nil,
    data: HttpData[R, E] = HttpData.Empty,
  ): Response[R, E] =
    Response(status, headers, data, HttpAttribute.empty)

  /**
   * Builds an error [[Response]] from the given [[HttpError]].
   */
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

  /**
   * Creates a [[io.netty.handler.codec.http.HttpHeaderValues.APPLICATION_JSON]] [[Response]] from the data.
   * @param data
   *   the response body
   */
  def jsonString(data: String): UResponse =
    Response(
      data = HttpData.fromChunk(Chunk.fromArray(data.getBytes(HTTP_CHARSET))),
      headers = List(Header.contentTypeJson),
    )

  @deprecated("Use `Response(status, headers, data)` constructor instead.", "22-Sep-2021")
  def http[R, E](
    status: Status = Status.OK,
    headers: List[Header] = Nil,
    data: HttpData[R, E] = HttpData.empty,
  ): Response[R, E] = Response(status, headers, data)

  /**
   * Creates a [[Response]] with [[Status.OK]].
   */
  def ok: UResponse = Response(Status.OK)

  /**
   * Creates a permanent redirect [[Response]] to the given location.
   */
  def permanentRedirect(location: String): Response[Any, Nothing] =
    Response(Status.PERMANENT_REDIRECT, List(Header.location(location)))

  /**
   * Creates a socket response using an app
   */
  def socket[R, E](ss: SocketApp[R, E]): Response[R, E] =
    Response(Status.SWITCHING_PROTOCOLS, Nil, HttpData.empty, HttpAttribute.socket(ss))

  /**
   * Creates a new WebSocket Response
   */
  def socket[R, E](ss: Socket[R, E, WebSocketFrame, WebSocketFrame]): Response[R, E] =
    SocketApp.message(ss).asResponse

  /**
   * Creates a [[Response]] with given status.
   */
  def status(status: Status): UResponse = Response(status)

  /**
   * Creates a [[io.netty.handler.codec.http.HttpHeaderValues.TEXT_PLAIN]] [[Response]] from the data.
   * @param text
   *   the response body
   */
  def text(text: String): UResponse =
    Response(
      data = HttpData.fromChunk(Chunk.fromArray(text.getBytes(HTTP_CHARSET))),
      headers = List(Header.contentTypeTextPlain),
    )

  /**
   * Creates a temporary redirect [[Response]] to the given location.
   */
  def temporaryRedirect(location: String): Response[Any, Nothing] =
    Response(Status.TEMPORARY_REDIRECT, List(Header.location(location)))
}
