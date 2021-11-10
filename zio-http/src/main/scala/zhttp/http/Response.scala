package zhttp.http

import io.netty.handler.codec.http.{HttpHeaderNames, HttpHeaderValues}
import zhttp.http.HttpError.HTTPErrorWithCause
import zhttp.socket.{Socket, SocketApp, WebSocketFrame}
import zio.Chunk

import java.io.{PrintWriter, StringWriter}

case class Response[-R, +E] private (
  status: Status,
  getHeaders: List[Header],
  data: HttpData[R, E],
  private[zhttp] val attribute: HttpAttribute[R, E],
) extends HeaderExtension[Response[R, E]] { self =>

  /**
   * Sets the status of the response
   */
  def setStatus(status: Status): Response[R, E] =
    self.copy(status = status)

  /**
   * Adds cookies in the response headers
   */
  def addCookie(cookie: Cookie): Response[R, E] =
    self.copy(getHeaders = self.getHeaders ++ List(Header.custom(HttpHeaderNames.SET_COOKIE.toString, cookie.encode)))

  /**
   * Extracts the length of the content specified in the response data.
   */
  def getContentLength: Option[Long] = self.data.size

  /**
   * Adds to the existing 'Transfer-Encoding' header a new value separated by comma or sets a given value if the header
   * is not set
   */
  def setTransferEncoding(value: String): Response[R, E] =
    getHeaderValue(Header.transferEncodingChunked.name) match {
      case Some(current) if !current.contains(value) =>
        self
          .removeHeader(Header.transferEncodingChunked.name.toString())
          .addHeader(Header(Header.transferEncodingChunked.name, s"$current, $value"))
      case _                                         =>
        self.addHeader(Header(Header.transferEncodingChunked.name, value))
    }

  /**
   * Automatically detects the size of the content and sets it
   */
  def setPayloadHeaders: Response[R, E] = {
    getContentLength match {
      case Some(value) => setContentLength(value)
      case None        => setTransferEncoding(HttpHeaderValues.CHUNKED.toString)
    }
  }

  /**
   * Updates the headers using the provided function
   */
  final override def updateHeaders(f: List[Header] => List[Header]): Response[R, E] =
    self.copy(getHeaders = f(self.getHeaders))
}

object Response {

  def apply[R, E](
    status: Status = Status.OK,
    headers: List[Header] = Nil,
    data: HttpData[R, E] = HttpData.Empty,
  ): Response[R, E] = {
    val resp       = Response(status, headers, data, HttpAttribute.empty)
    // Since these headers are mutual exclusive, check for the presence of at least one of them
    val hasHeaders =
      resp.getHeader(HttpHeaderNames.TRANSFER_ENCODING).exists(_.value.toString.contains(HttpHeaderValues.CHUNKED)) ||
        resp.getHeader(HttpHeaderNames.CONTENT_LENGTH).isDefined

    if (hasHeaders) resp
    else resp.setPayloadHeaders
  }

  @deprecated("Use `Response(status, headers, data)` constructor instead.", "22-Sep-2021")
  def http[R, E](
    status: Status = Status.OK,
    headers: List[Header] = Nil,
    data: HttpData[R, E] = HttpData.empty,
  ): Response[R, E] = Response(status, headers, data)

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
