package zhttp.http

import io.netty.handler.codec.http.{HttpHeaderNames, HttpResponse}
import zhttp.core.Util
import zhttp.http.Headers.Literals._
import zhttp.http.HttpError.HTTPErrorWithCause
import zhttp.http.headers.HeaderExtension
import zhttp.socket.{Socket, SocketApp, WebSocketFrame}
import zio.Chunk

import java.nio.charset.Charset

final case class Response[-R, +E] private (
  status: Status,
  headers: Headers,
  data: HttpData[R, E],
  private[zhttp] val attribute: Response.Attribute[R, E],
) extends HeaderExtension[Response[R, E]] { self =>

  /**
   * Adds cookies in the response headers
   */
  def addCookie(cookie: Cookie): Response[R, E] =
    self.copy(headers = self.getHeaders ++ Headers(HttpHeaderNames.SET_COOKIE.toString, cookie.encode))

  override def getHeaders: Headers = headers

  /**
   * Caches the encoded response as buffer. This is a "best effort" cache and doesn't always guarantee performance
   * gains. It can potentially also cause memory leaks or degrade performance for some use-cases.
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
  override def updateHeaders(f: Headers => Headers): Response[R, E] =
    self.copy(headers = f(self.getHeaders))

  /**
   * A more efficient way to append server-time to the response headers.
   */
  def withServerTime: Response[R, E] = self.copy(attribute = self.attribute.withServerTime)

  /**
   * Caches the response creation if set to true
   */
  private[zhttp] var cache: HttpResponse = null
}

object Response {

  def apply[R, E](
    status: Status = Status.OK,
    headers: Headers = Headers.empty,
    data: HttpData[R, E] = HttpData.Empty,
  ): Response[R, E] = {
    val size      = data.unsafeSize
    val isChunked = data.isChunked

    Response(
      status,
      headers ++
        Headers(Name.ContentLength -> size.toString).when(size >= 0) ++
        Headers(Name.TransferEncoding -> Value.Chunked).when(isChunked),
      data,
      Attribute.empty,
    )
  }

  def fromHttpError(error: HttpError): UResponse = {
    error match {
      case cause: HTTPErrorWithCause =>
        Response(
          error.status,
          Headers.empty,
          HttpData.fromText(cause.cause match {
            case Some(throwable) => Util.prettyPrintHtml(throwable)
            case None            => cause.message
          }),
        )
      case _                         =>
        Response(error.status, Headers.empty, HttpData.fromChunk(Chunk.fromArray(error.message.getBytes(HTTP_CHARSET))))
    }
  }

  /**
   * Creates a response with content-type set to text/html
   */
  def html(data: String): UResponse =
    Response(
      data = HttpData.fromText(data),
      headers = Headers(Name.ContentType, Value.TextHtml),
    )

  @deprecated("Use `Response(status, headers, data)` constructor instead.", "22-Sep-2021")
  def http[R, E](
    status: Status = Status.OK,
    headers: Headers = Headers.empty,
    data: HttpData[R, E] = HttpData.empty,
  ): Response[R, E] = Response(status, headers, data)

  /**
   * Creates a response with content-type set to application/json
   */
  def json(data: String): UResponse =
    Response(
      data = HttpData.fromChunk(Chunk.fromArray(data.getBytes(HTTP_CHARSET))),
      headers = Headers(Name.ContentLength, Value.ApplicationJson),
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
    Response(status, Headers.location(location))
  }

  /**
   * Creates a socket response using an app
   */
  def socket[R, E](app: SocketApp[R, E]): Response[R, E] =
    Response(Status.SWITCHING_PROTOCOLS, Headers.empty, HttpData.empty, Attribute(socketApp = app))

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
   * Creates a response with content-type set to text/plain
   */
  def text(text: String, charset: Charset = HTTP_CHARSET): UResponse =
    Response(
      data = HttpData.fromText(text, charset),
      headers = Headers(Name.ContentType, Value.TextPlain),
    )

  /**
   * Attribute holds meta data for the backend
   */

  final case class Attribute[-R, +E](
    socketApp: SocketApp[R, E] = SocketApp.empty,
    memoize: Boolean = false,
    serverTime: Boolean = false,
  ) {
    self =>
    def withMemoization: Attribute[R, E]                                           = self.copy(memoize = true)
    def withServerTime: Attribute[R, E]                                            = self.copy(serverTime = true)
    def withSocketApp[R1 <: R, E1 >: E](app: SocketApp[R1, E1]): Attribute[R1, E1] = self.copy(socketApp = app)
  }

  object Attribute {

    /**
     * Helper to create an empty HttpData
     */
    def empty: Attribute[Any, Nothing] = Attribute()
  }
}
