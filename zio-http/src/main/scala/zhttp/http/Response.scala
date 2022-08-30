package zhttp.http

import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.{FullHttpResponse, HttpHeaderNames, HttpResponse}
import zhttp.html._
import zhttp.http.headers.HeaderExtension
import zhttp.service.{ChannelEvent, ChannelFuture}
import zhttp.socket.{SocketApp, WebSocketFrame}
import zio.{Task, ZIO}

import java.io.{IOException, PrintWriter, StringWriter}

final case class Response private (
  status: Status,
  headers: Headers,
  body: Body,
  private[zhttp] val attribute: Response.Attribute,
) extends HeaderExtension[Response] { self =>

  /**
   * Adds cookies in the response headers.
   */
  def addCookie(cookie: Cookie): Response =
    self.copy(headers = self.headers ++ Headers(HttpHeaderNames.SET_COOKIE.toString, cookie.encode))

  /**
   * A micro-optimizations that ignores all further modifications to the
   * response and encodes the current version into a Netty response. The netty
   * response is cached and reused for subsequent requests. This allows the
   * server to reduce memory utilization under load by not having to encode the
   * response for each request. In case the response is modified the server will
   * detect the changes and encode the response again, however it will turn out
   * to be counter productive.
   */
  def freeze: Task[Response] =
    for {
      encoded <- encode()
    } yield self.copy(attribute = self.attribute.withEncodedResponse(encoded, self))

  def isWebSocket: Boolean =
    self.status.asJava.code() == Status.SwitchingProtocols.asJava.code() && self.attribute.socketApp.nonEmpty

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
   * Creates an Http from a Response
   */
  def toHttp: Http[Any, Nothing, Any, Response] = Http.succeed(self)

  /**
   * Updates the headers using the provided function
   */
  override def updateHeaders(update: Headers => Headers): Response =
    self.copy(headers = update(self.headers))

  /**
   * A more efficient way to append server-time to the response headers.
   */
  def withServerTime: Response = self.copy(attribute = self.attribute.withServerTime)

  private[zhttp] def close: Task[Unit] = self.attribute.channel match {
    case Some(channel) => ChannelFuture.unit(channel.close())
    case None          => ZIO.fail(new IOException("Channel context isn't available"))
  }

  /**
   * Encodes the Response into a Netty HttpResponse. Sets default headers such
   * as `content-length`. For performance reasons, it is possible that it uses a
   * FullHttpResponse if the complete data is available. Otherwise, it would
   * create a DefaultHttpResponse without any content.
   */
  private[zhttp] def encode(): Task[HttpResponse] = for {
    content <- if (body.isComplete) body.asChunk.map(Some(_)) else ZIO.succeed(None)
    res     <-
      ZIO.attempt {
        import io.netty.handler.codec.http._
        val jHeaders         = self.headers.encode
        val hasContentLength = jHeaders.contains(HttpHeaderNames.CONTENT_LENGTH)
        content.map(chunks => Unpooled.wrappedBuffer(chunks.toArray)) match {
          case Some(jContent) =>
            val jResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, self.status.asJava, jContent, false)

            // TODO: Unit test for this
            // Client can't handle chunked responses and currently treats them as a FullHttpResponse.
            // Due to this client limitation it is not possible to write a unit-test for this.
            // Alternative would be to use sttp client for this use-case.
            if (!hasContentLength) jHeaders.set(HttpHeaderNames.CONTENT_LENGTH, jContent.readableBytes())
            jResponse.headers().add(jHeaders)
            jResponse
          case None           =>
            if (!hasContentLength) jHeaders.set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED)
            new DefaultHttpResponse(HttpVersion.HTTP_1_1, self.status.asJava, jHeaders)
        }
      }
  } yield res
}

object Response {
  def apply[R, E](
    status: Status = Status.Ok,
    headers: Headers = Headers.empty,
    body: Body = Body.empty,
  ): Response =
    Response(status, headers, body, Attribute.empty)

  def fromHttpError(error: HttpError): Response = {

    def prettify(throwable: Throwable): String = {
      val sw = new StringWriter
      throwable.printStackTrace(new PrintWriter(sw))
      s"${sw.toString}"
    }

    Response
      .html(
        status = error.status,
        data = Template.container(s"${error.status}") {
          div(
            div(
              styles := Seq("text-align" -> "center"),
              div(s"${error.status.code}", styles := Seq("font-size" -> "20em")),
              div(error.message),
            ),
            div(
              error.foldCause(div()) { throwable =>
                div(h3("Cause:"), pre(prettify(throwable)))
              },
            ),
          )
        },
      )
  }

  /**
   * Creates a new response for the provided socket
   */
  def fromSocket[R](
    http: Http[R, Throwable, ChannelEvent[WebSocketFrame, WebSocketFrame], Unit],
  ): ZIO[R, Nothing, Response] =
    fromSocketApp(http.toSocketApp)

  /**
   * Creates a new response for the provided socket app
   */
  def fromSocketApp[R](app: SocketApp[R]): ZIO[R, Nothing, Response] = {
    ZIO.environment[R].map { env =>
      Response(
        Status.SwitchingProtocols,
        Headers.empty,
        Body.empty,
        Attribute(socketApp = Option(app.provideEnvironment(env))),
      )
    }

  }

  /**
   * Creates a response with content-type set to text/html
   */
  def html(data: Html, status: Status = Status.Ok): Response =
    Response(
      status = status,
      body = Body.fromString("<!DOCTYPE html>" + data.encode),
      headers = Headers(HeaderNames.contentType, HeaderValues.textHtml),
    )

  /**
   * Creates a response with content-type set to application/json
   */
  def json(data: CharSequence): Response =
    Response(
      body = Body.fromCharSequence(data),
      headers = Headers(HeaderNames.contentType, HeaderValues.applicationJson),
    )

  /**
   * Creates an empty response with status 200
   */
  def ok: Response = Response(Status.Ok)

  /**
   * Creates an empty response with status 301 or 302 depending on if it's
   * permanent or not.
   */
  def redirect(location: CharSequence, isPermanent: Boolean = false): Response = {
    val status = if (isPermanent) Status.PermanentRedirect else Status.TemporaryRedirect
    Response(status, Headers.location(location))
  }

  /**
   * Creates an empty response with status 303
   */
  def seeOther(location: CharSequence): Response =
    Response(Status.SeeOther, Headers.location(location))

  /**
   * Creates an empty response with the provided Status
   */
  def status(status: Status): Response = Response(status)

  /**
   * Creates a response with content-type set to text/plain
   */
  def text(text: CharSequence): Response =
    Response(
      body = Body.fromCharSequence(text),
      headers = Headers(HeaderNames.contentType, HeaderValues.textPlain),
    )

  private[zhttp] def unsafeFromJResponse(ctx: ChannelHandlerContext, jRes: FullHttpResponse): Response = {
    val status       = Status.fromHttpResponseStatus(jRes.status())
    val headers      = Headers.decode(jRes.headers())
    val copiedBuffer = Unpooled.copiedBuffer(jRes.content())
    val data         = Body.fromByteBuf(copiedBuffer)
    Response(status, headers, data, attribute = Attribute(channel = Some(ctx)))
  }

  /**
   * Attribute holds meta data for the backend
   */

  private[zhttp] final case class Attribute(
    socketApp: Option[SocketApp[Any]] = None,
    memoize: Boolean = false,
    serverTime: Boolean = false,
    encoded: Option[(Response, HttpResponse)] = None,
    channel: Option[ChannelHandlerContext] = None,
  ) { self =>
    def withEncodedResponse(jResponse: HttpResponse, response: Response): Attribute =
      self.copy(encoded = Some(response -> jResponse))

    def withMemoization: Attribute = self.copy(memoize = true)

    def withServerTime: Attribute = self.copy(serverTime = true)

    def withSocketApp(app: SocketApp[Any]): Attribute = self.copy(socketApp = Option(app))
  }

  object Attribute {

    /**
     * Helper to create an empty Body
     */
    def empty: Attribute = Attribute()
  }
}
