package zhttp.http

import io.netty.buffer.Unpooled
import io.netty.handler.codec.http.HttpVersion.HTTP_1_1
import io.netty.handler.codec.http.{HttpHeaderNames, HttpResponse}
import zhttp.core.Util
import zhttp.html.Html
import zhttp.http.Headers.Literals._
import zhttp.http.HttpError.HTTPErrorWithCause
import zhttp.http.headers.HeaderExtension
import zhttp.socket.{Socket, SocketApp, WebSocketFrame}
import zio.{Chunk, UIO}

import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.charset.Charset

final case class Response[-R, +E] private (
  status: Status,
  headers: Headers,
  data: HttpData[R, E],
  private[zhttp] val attribute: Response.Attribute[R, E],
) extends HeaderExtension[Response[R, E]] { self =>

  /**
   * Adds cookies in the response headers.
   */
  def addCookie(cookie: Cookie): Response[R, E] =
    self.copy(headers = self.getHeaders ++ Headers(HttpHeaderNames.SET_COOKIE.toString, cookie.encode))

  /**
   * A micro-optimizations that ignores all further modifications to the response and encodes the current version into a
   * Netty response. The netty response is cached and reused for subsequent requests. This allows the server to reduce
   * memory utilization under load by not having to encode the response for each request. In case the response is
   * modified the server will detect the changes and encode the response again, however it will turn out to be counter
   * productive.
   */
  def freeze: UIO[Response[R, E]] =
    UIO(self.copy(attribute = self.attribute.withEncodedResponse(unsafeEncode(), self)))

  override def getHeaders: Headers = headers

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
  override def updateHeaders(update: Headers => Headers): Response[R, E] =
    self.copy(headers = update(self.getHeaders))

  /**
   * A more efficient way to append server-time to the response headers.
   */
  def withServerTime: Response[R, E] = self.copy(attribute = self.attribute.withServerTime)

  /**
   * Encodes the Response into a Netty HttpResponse. Sets default headers such as `content-length`. For performance
   * reasons, it is possible that it uses a FullHttpResponse if the complete data is available. Otherwise, it would
   * create a DefaultHttpResponse without any content.
   */
  private[zhttp] def unsafeEncode(): HttpResponse = {
    import io.netty.handler.codec.http._

    val jHeaders = self.getHeaders.encode
    val jContent = self.data match {
      case HttpData.Text(text, charset) => Unpooled.copiedBuffer(text, charset)
      case HttpData.BinaryChunk(data)   => Unpooled.copiedBuffer(data.toArray)
      case HttpData.BinaryByteBuf(data) => data
      case HttpData.BinaryStream(_)     => null
      case HttpData.Empty               => Unpooled.EMPTY_BUFFER
      case HttpData.File(file)          =>
        // Transfers the content of file channel to ByteBuf
        val aFile: RandomAccessFile = new RandomAccessFile(file.toString, "r")
        val inChannel: FileChannel  = aFile.getChannel
        val fileSize: Long          = inChannel.size
        val buffer: ByteBuffer      = ByteBuffer.allocate(fileSize.toInt)
        inChannel.read(buffer)
        inChannel.close()
        aFile.close()
        Unpooled.wrappedBuffer(buffer.flip)
    }

    val hasContentLength = jHeaders.contains(HttpHeaderNames.CONTENT_LENGTH)
    if (jContent == null) {
      // TODO: Unit test for this
      // Client can't handle chunked responses and currently treats them as a FullHttpResponse.
      // Due to this client limitation it is not possible to write a unit-test for this.
      // Alternative would be to use sttp client for this use-case.

      if (!hasContentLength) jHeaders.set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED)
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
    data: HttpData[R, E] = HttpData.Empty,
  ): Response[R, E] =
    Response(status, headers, data, Attribute.empty)

  def fromHttpError(error: HttpError): UResponse = {
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
   * Creates a response with content-type set to text/html
   */
  def html(data: Html): UResponse =
    Response(
      data = HttpData.fromString(data.encode),
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
      data = HttpData.fromString(text, charset),
      headers = Headers(Name.ContentType, Value.TextPlain),
    )

  /**
   * Attribute holds meta data for the backend
   */

  private[zhttp] final case class Attribute[-R, +E](
    socketApp: SocketApp[R, E] = SocketApp.empty,
    memoize: Boolean = false,
    serverTime: Boolean = false,
    encoded: Option[(Response[R, E], HttpResponse)] = None,
  ) {
    self =>
    def withEncodedResponse[R1 <: R, E1 >: E](jResponse: HttpResponse, response: Response[R1, E1]): Attribute[R1, E1] =
      self.copy(encoded = Some(response -> jResponse))

    def withMemoization: Attribute[R, E] = self.copy(memoize = true)

    def withServerTime: Attribute[R, E] = self.copy(serverTime = true)

    def withSocketApp[R1 <: R, E1 >: E](app: SocketApp[R1, E1]): Attribute[R1, E1] = self.copy(socketApp = app)
  }

  object Attribute {

    /**
     * Helper to create an empty HttpData
     */
    def empty: Attribute[Any, Nothing] = Attribute()
  }
}
