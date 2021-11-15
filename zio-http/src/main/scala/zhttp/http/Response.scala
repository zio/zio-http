package zhttp.http

import io.netty.handler.codec.http.{HttpHeaderNames, HttpResponse}
import zhttp.http.HttpError.HTTPErrorWithCause
import zhttp.socket.{Socket, SocketApp, WebSocketFrame}
import zio.Chunk

import java.io.{PrintWriter, StringWriter}
import java.nio.charset.Charset

final case class Response[-R, +E] private (
  status: Status,
  headers: List[Header],
  data: HttpData[R, E],
  attribute: Response.Attribute[R, E],
) extends HeaderExtension[Response[R, E]] { self =>

  /**
   * Adds cookies in the response headers
   */
  def addCookie(cookie: Cookie): Response[R, E] =
    self.copy(headers = self.getHeaders ++ List(Header.custom(HttpHeaderNames.SET_COOKIE.toString, cookie.encode)))

  override def getHeaders: List[Header] = headers

  /**
   * Memoization of response. NOTE: This may not necessarily improve performance and can potentially cause memory leaks.
   */
  def memoize: Response[R, E] = self.copy(attribute = self.attribute.withMemoization)

  /**
   * Sets the response attributes
   */
  def setAttribute[R1 <: R, E1 >: E](attribute: Response.Attribute[R1, E1]): Response[R1, E1] =
    self.copy(attribute = attribute)

  /**
   * Sets the status of the response
   */
  def setStatus(status: Status): Response[R, E] =
    self.copy(status = status)

  /**
   * Updates the headers using the provided function
   */
  override def updateHeaders(f: List[Header] => List[Header]): Response[R, E] =
    self.copy(headers = f(self.getHeaders))

  /**
   * Caches the response creation if set to true
   */
  private[zhttp] var cache: HttpResponse = null
}

object Response {

  def apply[R, E](
    status: Status = Status.OK,
    headers: List[Header] = Nil,
    data: HttpData[R, E] = HttpData.Empty,
  ): Response[R, E] = {
    val size      = data.unsafeSize
    val isChunked = data.isChunked

    val contentLength    = if (size >= 0) Header.contentLength(size) :: Nil else Nil
    val transferEncoding = if (isChunked) Header.transferEncodingChunked :: Nil else Nil

    Response(status, headers ++ transferEncoding ++ contentLength, data, Attribute.empty)
  }

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

  @deprecated("Use `Response(status, headers, data)` constructor instead.", "22-Sep-2021")
  def http[R, E](
    status: Status = Status.OK,
    headers: List[Header] = Nil,
    data: HttpData[R, E] = HttpData.empty,
  ): Response[R, E] = Response(status, headers, data)

  /**
   * Creates a response with content-type set to application/json
   */
  def jsonString(data: String): UResponse =
    Response(
      data = HttpData.fromChunk(Chunk.fromArray(data.getBytes(HTTP_CHARSET))),
      headers = List(Header.contentTypeJson),
    )

  /**
   * Creates an empty response with status 200
   */
  def ok: UResponse = Response(Status.OK)

  /**
   * Creates an empty response with status 301 or 302 depending on if it's permanent or not.
   */
  def redirect(location: String, isPermanent: Boolean = false): Response[Any, Nothing] = {
    val status = if (isPermanent) Status.PERMANENT_REDIRECT else Status.TEMPORARY_REDIRECT
    Response(status, List(Header.location(location)))
  }

  /**
   * Creates a socket response using an app
   */
  def socket[R, E](app: SocketApp[R, E]): Response[R, E] =
    Response(Status.SWITCHING_PROTOCOLS, Nil, HttpData.empty, Attribute(socketApp = app))

  /**
   * Creates a new WebSocket Response
   */
  def socket[R, E](ss: Socket[R, E, WebSocketFrame, WebSocketFrame]): Response[R, E] =
    SocketApp.message(ss).asResponse

  /**
   * Creates an empty response with the provided Status
   */
  def status(status: Status): UResponse = Response(status)

  /**
   * Creates a response with content-type set to plain/text
   */
  def text(text: String, charset: Charset = HTTP_CHARSET): UResponse =
    Response(
      data = HttpData.fromText(text, charset),
      headers = List(Header.contentTypeTextPlain),
    )

  /**
   * Attribute holds meta data for the backend
   */

  final case class Attribute[-R, +E](socketApp: SocketApp[R, E] = SocketApp.empty, memoization: Boolean = false) {
    self =>
    def withMemoization: Attribute[R, E]                                           = self.copy()
    def withSocketApp[R1 <: R, E1 >: E](app: SocketApp[R1, E1]): Attribute[R1, E1] = self.copy(socketApp = app)
  }

  object Attribute {

    /**
     * Helper to create an empty HttpData
     */
    def empty: Attribute[Any, Nothing] = Attribute()
  }
}
