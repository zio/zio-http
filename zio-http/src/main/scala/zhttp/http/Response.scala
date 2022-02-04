package zhttp.http

import io.netty.buffer.{ByteBuf, Unpooled}
import io.netty.handler.codec.http.HttpVersion.HTTP_1_1
import io.netty.handler.codec.http.{HttpHeaderNames, HttpResponse}
import zhttp.core.Util
import zhttp.html.Html
import zhttp.http.HttpError.HTTPErrorWithCause
import zhttp.http.headers.HeaderExtension
import zhttp.socket.{IsWebSocket, Socket, SocketApp}
import zio.{Chunk, Task, UIO, ZIO}

import java.nio.charset.Charset
import java.nio.file.Files

final case class Response private (
  status: Status,
  headers: Headers,
  data: HttpData,
  private[zhttp] val attribute: Response.Attribute,
) extends HeaderExtension[Response] { self =>

  /**
   * Adds cookies in the response headers.
   */
  def addCookie(cookie: Cookie): Response =
    self.copy(headers = self.getHeaders ++ Headers(HttpHeaderNames.SET_COOKIE.toString, cookie.encode))

  /**
   * A micro-optimizations that ignores all further modifications to the response and encodes the current version into a
   * Netty response. The netty response is cached and reused for subsequent requests. This allows the server to reduce
   * memory utilization under load by not having to encode the response for each request. In case the response is
   * modified the server will detect the changes and encode the response again, however it will turn out to be counter
   * productive.
   */
  def freeze: UIO[Response] =
    UIO(self.copy(attribute = self.attribute.withEncodedResponse(unsafeEncode(), self)))

  override def getHeaders: Headers = headers

  /**
   * Sets the response attributes
   */
  def setAttribute(attribute: Response.Attribute): Response =
    self.copy(attribute = attribute)

  /**
   * Sets the status of the response
   */
  def setStatus(status: Status): Response =
    self.copy(status = status)

  /**
   * Updates the headers using the provided function
   */
  override def updateHeaders(update: Headers => Headers): Response =
    self.copy(headers = update(self.getHeaders))

  /**
   * A more efficient way to append server-time to the response headers.
   */
  def withServerTime: Response = self.copy(attribute = self.attribute.withServerTime)

  /**
   * Extracts the body as ByteBuf
   */
  private[zhttp] def getBodyAsByteBuf: Task[ByteBuf] = self.data.toByteBuf

  /**
   * Encodes the Response into a Netty HttpResponse. Sets default headers such as `content-length`. For performance
   * reasons, it is possible that it uses a FullHttpResponse if the complete data is available. Otherwise, it would
   * create a DefaultHttpResponse without any content.
   */
  private[zhttp] def unsafeEncode(): HttpResponse = {
    import io.netty.handler.codec.http._

    val jHeaders = self.getHeaders.encode
    val jContent = self.data match {
      case HttpData.Text(text, charset) => Unpooled.wrappedBuffer(text.getBytes(charset))
      case HttpData.BinaryChunk(data)   => Unpooled.copiedBuffer(data.toArray)
      case HttpData.BinaryByteBuf(data) => data
      case HttpData.BinaryStream(_)     => null
      case HttpData.Empty               => Unpooled.EMPTY_BUFFER
      case HttpData.File(file)          =>
        jHeaders.set(HttpHeaderNames.CONTENT_TYPE, Files.probeContentType(file.toPath))
        null
    }

    val hasContentLength = jHeaders.contains(HttpHeaderNames.CONTENT_LENGTH)
    if (jContent == null) {
      // TODO: Unit test for this
      // Client can't handle chunked responses and currently treats them as a FullHttpResponse.
      // Due to this client limitation it is not possible to write a unit-test for this.
      // Alternative would be to use sttp client for this use-case.

      if (!hasContentLength) jHeaders.set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED)

      // Set MIME type in the response headers. This is only relevant in case of File transfers as browsers use the MIME
      // type, not the file extension, to determine how to process a URL.<a href="MSDN
      // Doc">https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Content-Type</a>

      new DefaultHttpResponse(HttpVersion.HTTP_1_1, self.status.asJava, jHeaders)
    } else {
      val jResponse = new DefaultFullHttpResponse(HTTP_1_1, self.status.asJava, jContent, false)
      if (!hasContentLength) jHeaders.set(HttpHeaderNames.CONTENT_LENGTH, jContent.readableBytes())
      jResponse.headers().add(jHeaders)
      jResponse
    }
  }
}

object Response {
  def apply[R, E](
    status: Status = Status.OK,
    headers: Headers = Headers.empty,
    data: HttpData = HttpData.Empty,
  ): Response =
    Response(status, headers, data, Attribute.empty)

  def fromHttpError(error: HttpError): Response = {
    error match {
      case cause: HTTPErrorWithCause =>
        Response(
          error.status,
          Headers.empty,
          HttpData.fromString(cause.cause match {
            case Some(throwable) => Util.prettyPrintHtml(throwable)
            case None            => cause.message
          }),
        )
      case _                         =>
        Response(error.status, Headers.empty, HttpData.fromChunk(Chunk.fromArray(error.message.getBytes(HTTP_CHARSET))))
    }
  }

  /**
   * Creates a new response for the provided socket
   */
  def fromSocket[R, E, A, B](socket: Socket[R, E, A, B])(implicit
    ev: IsWebSocket[R, E, A, B],
  ): ZIO[R, Nothing, Response] =
    fromSocketApp(socket.toSocketApp)

  /**
   * Creates a new response for the provided socket app
   */
  def fromSocketApp[R](app: SocketApp[R]): ZIO[R, Nothing, Response] = {
    ZIO.environment[R].map { env =>
      Response(
        Status.SWITCHING_PROTOCOLS,
        Headers.empty,
        HttpData.empty,
        Attribute(socketApp = Option(app.provide(env))),
      )
    }

  }

  /**
   * Creates a response with content-type set to text/html
   */
  def html(data: Html): Response =
    Response(
      data = HttpData.fromString("<!DOCTYPE html>" + data.encode),
      headers = Headers(HeaderNames.contentType, HeaderValues.textHtml),
    )

  @deprecated("Use `Response(status, headers, data)` constructor instead.", "22-Sep-2021")
  def http[R, E](
    status: Status = Status.OK,
    headers: Headers = Headers.empty,
    data: HttpData = HttpData.empty,
  ): Response = Response(status, headers, data)

  /**
   * Creates a response with content-type set to application/json
   */
  def json(data: String): Response =
    Response(
      data = HttpData.fromChunk(Chunk.fromArray(data.getBytes(HTTP_CHARSET))),
      headers = Headers(HeaderNames.contentType, HeaderValues.applicationJson),
    )

  /**
   * Creates an empty response with status 200
   */
  def ok: Response = Response(Status.OK)

  /**
   * Creates an empty response with status 301 or 302 depending on if it's permanent or not.
   */
  def redirect(location: String, isPermanent: Boolean = false): Response = {
    val status = if (isPermanent) Status.PERMANENT_REDIRECT else Status.TEMPORARY_REDIRECT
    Response(status, Headers.location(location))
  }

  /**
   * Creates an empty response with the provided Status
   */
  def status(status: Status): Response = Response(status)

  /**
   * Creates a response with content-type set to text/plain
   */
  def text(text: String, charset: Charset = HTTP_CHARSET): Response =
    Response(
      data = HttpData.fromString(text, charset),
      headers = Headers(HeaderNames.contentType, HeaderValues.textPlain),
    )

  /**
   * Attribute holds meta data for the backend
   */

  private[zhttp] final case class Attribute(
    socketApp: Option[SocketApp[Any]] = None,
    memoize: Boolean = false,
    serverTime: Boolean = false,
    encoded: Option[(Response, HttpResponse)] = None,
  ) { self =>
    def withEncodedResponse(jResponse: HttpResponse, response: Response): Attribute =
      self.copy(encoded = Some(response -> jResponse))

    def withMemoization: Attribute = self.copy(memoize = true)

    def withServerTime: Attribute = self.copy(serverTime = true)

    def withSocketApp(app: SocketApp[Any]): Attribute = self.copy(socketApp = Option(app))
  }

  object Attribute {

    /**
     * Helper to create an empty HttpData
     */
    def empty: Attribute = Attribute()
  }
}
