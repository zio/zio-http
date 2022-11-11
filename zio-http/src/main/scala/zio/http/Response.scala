package zio.http

import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.{FullHttpResponse, HttpResponse}
import zio.http.html._
import zio.http.model._
import zio.http.model.headers.HeaderExtension
import zio.http.netty._
import zio.http.netty.client.{ChannelState, ClientResponseStreamHandler}
import zio.http.service.{CLIENT_INBOUND_HANDLER, CLIENT_STREAMING_BODY_HANDLER}
import zio.http.socket.{SocketApp, WebSocketFrame}
import zio.{Cause, Promise, Task, Trace, Unsafe, ZIO}

import java.io.IOException
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;
import java.util.concurrent.atomic.AtomicReference

sealed abstract case class Response private (
  status: Status,
  headers: Headers,
  body: Body,
  private[zio] val attribute: Response.Attribute,
  private[zio] val httpError: Option[HttpError],
) extends HeaderExtension[Response] { self =>

  /**
   * Adds cookies in the response headers.
   */
  def addCookie(cookie: Cookie[Response]): Response =
    self.copy(headers = self.headers ++ Headers.setCookie(cookie))

  /**
   * A micro-optimizations that ignores all further modifications to the
   * response and encodes the current version into a Netty response. The netty
   * response is cached and reused for subsequent requests. This allows the
   * server to reduce memory utilization under load by not having to encode the
   * response for each request. In case the response is modified the server will
   * detect the changes and encode the response again, however it will turn out
   * to be counter productive.
   */
  def freeze: Response = new Response(self.status, self.headers, self.body, self.attribute, self.httpError)
    with Response.Frozen {}

  def isWebSocket: Boolean =
    self.status.asJava.code() == Status.SwitchingProtocols.asJava.code() && self.attribute.socketApp.nonEmpty

  def patch(p: Response.Patch): Response =
    copy(headers = self.headers ++ p.addHeaders, status = p.setStatus.getOrElse(self.status))

  /**
   * Sets the response attributes
   */
  final def setAttribute(attribute: Response.Attribute): Response =
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

  private[zio] def close(implicit trace: Trace): Task[Unit] = self.attribute.channel match {
    case Some(channel) => NettyFutureExecutor.executed(channel.close())
    case None          => ZIO.refailCause(Cause.fail(new IOException("Channel context isn't available")))
  }

  final def copy(
    status: Status = self.status,
    headers: Headers = self.headers,
    body: Body = self.body,
    attribute: Response.Attribute = self.attribute,
  ): Response =
    self match {
      case _: Response.Frozen => new Response(status, headers, body, attribute, self.httpError) with Response.Frozen {}
      case _                  => new Response(status, headers, body, attribute, self.httpError) {}

    }

}

object Response {

  sealed trait Frozen extends Response

  final case class Patch(addHeaders: Headers, setStatus: Option[Status]) { self =>
    def ++(that: Patch): Patch =
      Patch(self.addHeaders ++ that.addHeaders, self.setStatus.orElse(that.setStatus))
  }

  def apply[R, E](
    status: Status = Status.Ok,
    headers: Headers = Headers.empty,
    body: Body = Body.empty,
    httpError: Option[HttpError] = None,
  ): Response =
    new Response(status, headers, body, Attribute.empty, httpError) {}

  def fromHttpError(error: HttpError): Response = Response(status = error.status, httpError = Some(error))

  /**
   * Creates a new response for the provided socket
   */
  def fromSocket[R](
    http: Http[R, Throwable, ChannelEvent[WebSocketFrame, WebSocketFrame], Unit],
  )(implicit trace: Trace): ZIO[R, Nothing, Response] =
    fromSocketApp(http.toSocketApp)

  /**
   * Creates a new response for the provided socket app
   */
  def fromSocketApp[R](app: SocketApp[R])(implicit trace: Trace): ZIO[R, Nothing, Response] = {
    ZIO.environment[R].map { env =>
      new Response(
        Status.SwitchingProtocols,
        Headers.empty,
        Body.empty,
        Attribute(socketApp = Option(app.provideEnvironment(env))),
        None,
      ) {}
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

  private[zio] object unsafe {
    final def fromJResponse(ctx: ChannelHandlerContext, jRes: FullHttpResponse)(implicit
      unsafe: Unsafe,
    ): Response = {
      val status       = Status.fromHttpResponseStatus(jRes.status())
      val headers      = Headers.decode(jRes.headers())
      val copiedBuffer = Unpooled.copiedBuffer(jRes.content())
      val data         = Body.fromByteBuf(copiedBuffer)
      new Response(
        status,
        headers,
        data,
        attribute = Attribute(channel = Some(ctx)),
        None,
      ) {}
    }

    final def fromStreamingJResponse(
      ctx: ChannelHandlerContext,
      jRes: HttpResponse,
      zExec: NettyRuntime,
      onComplete: Promise[Throwable, ChannelState],
      keepAlive: Boolean,
    )(implicit
      unsafe: Unsafe,
      trace: Trace,
    ): Response = {
      val status  = Status.fromHttpResponseStatus(jRes.status())
      val headers = Headers.decode(jRes.headers())
      val data    = Body.fromAsync { callback =>
        ctx
          .pipeline()
          .addAfter(
            CLIENT_INBOUND_HANDLER,
            CLIENT_STREAMING_BODY_HANDLER,
            new ClientResponseStreamHandler(callback, zExec, onComplete, keepAlive),
          ): Unit
      }
      new Response(
        status,
        headers,
        data,
        attribute = Attribute(channel = Some(ctx)),
        None,
      ) {}
    }
  }

  /**
   * Attribute holds meta data for the backend
   */

  private[zio] final case class Attribute(
    socketApp: Option[SocketApp[Any]] = None,
    memoize: Boolean = false,
    serverTime: Boolean = false,
    channel: Option[ChannelHandlerContext] = None,
  ) { self =>

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
