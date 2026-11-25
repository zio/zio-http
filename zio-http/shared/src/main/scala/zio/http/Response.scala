/*
 * Copyright 2021 - 2023 Sporta Technologies PVT LTD & the ZIO HTTP contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.http

import java.nio.file.{AccessDeniedException, NotDirectoryException}

import scala.annotation.tailrec

import zio._

import zio.stream.ZStream

import zio.schema.Schema
import zio.schema.codec.BinaryCodec

import zio.http.internal.{HeaderOps, OutputEncoder}
import zio.http.template._

/**
 * An HTTP response: a status, headers and a body.
 *
 * Handlers return a `Response` on the server, and a client receives one. The
 * companion builds the common cases: `Response.text`, `Response.json`,
 * `Response.html`, `Response.redirect`, an error status such as
 * `Response.notFound`, or a response for a failure (`Response.fromCause`,
 * `Response.fromThrowable`). Every update returns a new response.
 *
 * {{{
 * val created: Response =
 *   Response
 *     .json("""{"id":42}""")
 *     .status(Status.Created)
 *     .addHeader("x-request-id", "r-17")
 *
 * created.status // Created
 * }}}
 *
 * @see
 *   [[https://zio.dev/zio-http/reference/response/ Response]]
 */
final case class Response(
  status: Status = Status.Ok,
  headers: Headers = Headers.empty,
  body: Body = Body.empty,
) extends HeaderOps[Response] { self =>

  // To be used by encoders to avoid re-encoding static responses (optimization)
  private[http] var encoded: AnyRef = null

  /** Adds a `Set-Cookie` header for `cookie`. */
  def addCookie(cookie: Cookie.Response): Response =
    self.copy(headers = self.headers ++ Headers(Header.SetCookie(cookie)))

  /** Adds one `Set-Cookie` header per cookie. */
  def addCookies(cookie: Cookie.Response, cookies: Cookie.Response*) =
    updateHeaders(_.addHeaders(Headers.fromIterable((cookie +: cookies).map(Header.SetCookie(_)))))

  /**
   * Adds flash values to the cookie-based flash-scope.
   */
  def addFlash[A](setter: Flash.Setter[A]): Response =
    self.addCookie(Flash.Setter.run(setter).copy(path = Some(Path.root)))

  /**
   * Collects the potentially streaming body of the response into a single
   * chunk.
   *
   * Any errors that occur from the collection of the body will be caught and
   * propagated to the Body
   */
  def collect(implicit trace: Trace): ZIO[Any, Nothing, Response] =
    self.body.materialize.map { b =>
      if (b eq self.body) self
      else self.copy(body = b)
    }

  /**
   * Adds a `content-type` header with the given media type. A `Content-Type`
   * header that is already there is kept, so the response then has two.
   */
  def contentType(mediaType: MediaType): Response =
    self.addHeader[String]("content-type", mediaType.fullType)

  /**
   * Consumes the streaming body fully and then discards it while also ignoring
   * any failures
   */
  def ignoreBody(implicit trace: Trace): ZIO[Any, Nothing, Response] = {
    val out   = self.copy(body = Body.empty)
    val body0 = self.body
    if (body0.isComplete) Exit.succeed(out)
    else body0.asStream.runDrain.ignore.as(out)
  }

  /** Applies the patch `p` to this response. */
  def patch(p: Response.Patch)(implicit trace: Trace): Response = p.apply(self)

  /**
   * Sets the status of the response
   */
  def status(status: Status): Response =
    copy(status = status)

  /**
   * Decodes the content of the body as a value based on a zio-schema
   * [[zio.schema.codec.BinaryCodec]].<br>
   *
   * Example for json:
   * {{{
   * import zio._
   * import zio.schema.{DeriveSchema, Schema}
   * import zio.schema.codec.JsonCodec._
   *
   * case class Point(x: Int, y: Int)
   * implicit val schema: Schema[Point] = DeriveSchema.gen[Point]
   *
   * val point: Task[Point] = Response.json("""{"x":1,"y":2}""").bodyAs[Point]
   * // succeeds with Point(1,2)
   * }}}
   */
  def bodyAs[A](implicit codec: BinaryCodec[A], trace: Trace): Task[A] =
    body.to[A]

  /**
   * Returns a handler that ignores its input and always succeeds with this
   * response.
   */
  def toHandler(implicit trace: Trace): Handler[Any, Nothing, Any, Response] = Handler.fromResponse(self)

  /**
   * Updates the current Headers with new one, using the provided update
   * function passed.
   */
  override def updateHeaders(update: Headers => Headers)(implicit trace: Trace): Response =
    copy(headers = update(headers))

}

/**
 * Constructors for [[Response]]: by status, from text, JSON or HTML, from a
 * failure, from server-sent events or a WebSocket app.
 */
object Response {

  /**
   * Models the set of operations that one would want to apply on a Response.
   */
  sealed trait Patch { self =>

    /** Combines two patches: `self` is applied first, then `that`. */
    def ++(that: Patch): Patch = Patch.Combine(self, that)

    /** Applies this patch to `res`. */
    def apply(res: Response)(implicit trace: Trace): Response = {

      @tailrec
      def loop(res: Response, patch: Patch): Response =
        patch match {
          case Patch.Empty                  => res
          case Patch.AddHeaders(headers)    => res.addHeaders(headers)
          case Patch.RemoveHeaders(headers) => res.removeHeaders(headers)
          case Patch.SetStatus(status)      => res.status(status)
          case Patch.Combine(self, other)   => loop(self(res), other)
          case Patch.UpdateHeaders(f)       => res.updateHeaders(f)
        }

      loop(res, self)
    }
  }

  /** The patch cases and their constructors. */
  object Patch {
    import Header.HeaderType

    /** A patch that changes nothing. */
    case object Empty extends Patch

    /** A patch that adds `headers`. */
    final case class AddHeaders(headers: Headers) extends Patch

    /** A patch that removes the headers with the given names. */
    final case class RemoveHeaders(headers: Set[String]) extends Patch

    /** A patch that sets the status. */
    final case class SetStatus(status: Status) extends Patch

    /** A patch that applies `left`, then `right`. */
    final case class Combine(left: Patch, right: Patch) extends Patch

    /** A patch that replaces the headers with `f` applied to them. */
    final case class UpdateHeaders(f: Headers => Headers) extends Patch

    /** A patch that changes nothing. */
    def empty: Patch = Empty

    /** A patch that adds a header of the given type with the given value. */
    def addHeader(headerType: HeaderType)(value: headerType.HeaderValue): Patch =
      addHeader(headerType.name, headerType.render(value))

    /** A patch that adds `header`. */
    def addHeader(header: Header): Patch = addHeaders(Headers(header))

    /** A patch that adds `headers`. */
    def addHeaders(headers: Headers): Patch = AddHeaders(headers)

    /** A patch that adds a header with the given name and value. */
    def addHeader(name: CharSequence, value: CharSequence): Patch = addHeaders(Headers(name, value))

    /** A patch that removes every header of the given types. */
    def removeHeaders(headerTypes: Set[HeaderType]): Patch = RemoveHeaders(headerTypes.map(_.name))

    /** A patch that sets the status. */
    def status(status: Status): Patch = SetStatus(status)

    /** A patch that replaces the headers with `f` applied to them. */
    def updateHeaders(f: Headers => Headers): Patch = UpdateHeaders(f)
  }

  /** Creates an empty response with status 400 Bad Request. */
  def badRequest: Response =
    error(Status.BadRequest)

  /**
   * Creates a response with status 400 Bad Request and a body made from
   * `message`. No `Content-Type` header is set.
   */
  def badRequest(message: String): Response =
    error(Status.BadRequest, message)

  /**
   * Creates a response with the given error status and a body made from
   * `message`. No `Content-Type` header is set.
   */
  def error(status: Status.Error, message: String): Response =
    Response(status = status, body = Body.fromString(OutputEncoder.encodeHtml(message)))

  /**
   * Creates a response with the given error status and body. When the body has
   * a media type, the response gets a matching `Content-Type` header.
   */
  def error(status: Status.Error, body: Body): Response =
    Response(
      status = status,
      body = body,
      headers = if (body.mediaType.isEmpty) Headers.empty else Headers(Header.ContentType(body.mediaType.get)),
    )

  /** Creates an empty response with the given error status. */
  def error(status: Status.Error): Response =
    Response(status = status)

  /** Creates an empty response with status 403 Forbidden. */
  def forbidden: Response =
    error(Status.Forbidden)

  /**
   * Creates a response with status 403 Forbidden and a body made from
   * `message`. No `Content-Type` header is set.
   */
  def forbidden(message: String): Response =
    error(Status.Forbidden, message)

  /**
   * Creates a response from `cause` with `ErrorResponseConfig.default`, which
   * gives an empty body. See the overload that takes an `ErrorResponseConfig`
   * for how the status is chosen.
   */
  def fromCause(cause: Cause[Any]): Response =
    fromCause(cause, ErrorResponseConfig.default)

  /**
   * Creates a new response from the specified cause. Note that this method is
   * not polymorphic, but will attempt to inspect the runtime class of the
   * failure inside the cause, if any.
   *
   * A typed failure that is a `Response` is returned as is, a `Throwable` goes
   * through `fromThrowable`, and a nested `Cause` is handled the same way.
   * Anything else gives 408 Request Timeout when the cause holds only
   * interruptions (or nothing at all), and 500 Internal Server Error otherwise;
   * a defect is not passed to `fromThrowable`. With `config.withErrorBody`,
   * those last two get the pretty-printed cause as a `text/plain` body.
   */
  @tailrec
  def fromCause(cause: Cause[Any], config: ErrorResponseConfig): Response =
    cause.failureOrCause match {
      case Left(failure: Response)  => failure
      case Left(failure: Throwable) => fromThrowable(failure, config)
      case Left(failure: Cause[_])  => fromCause(failure, config)
      case _                        =>
        val body =
          if (config.withErrorBody) Body.fromString(cause.prettyPrint).contentType(MediaType.text.`plain`)
          else Body.empty
        if (cause.isInterruptedOnly) error(Status.RequestTimeout, body)
        else error(Status.InternalServerError, body)
    }

  /**
   * Creates a new response from the specified cause, translating any typed
   * error to a response using the provided function.
   */
  def fromCauseWith[E](cause: Cause[E], config: ErrorResponseConfig)(f: E => Response): Response =
    cause.failureOrCause match {
      case Left(failure) => f(failure)
      case Right(cause)  => fromCause(cause, config)
    }

  /**
   * Creates a response with content-type set to text/event-stream
   * @param data
   *   \- stream of data to be sent as Server Sent Events
   */
  def fromServerSentEvents[T: Schema](data: ZStream[Any, Nothing, ServerSentEvent[T]])(implicit
    trace: Trace,
  ): Response = {
    val codec = ServerSentEvent.defaultBinaryCodec[T]
    Response(
      Status.Ok,
      contentTypeEventStream,
      Body.fromCharSequenceStreamChunked(data.map(codec.encode(_).asString)),
    )
  }

  /**
   * Creates a response with content-type set to text/event-stream whose body
   * holds all of the given events, encoded with the default codec for `T`. Each
   * encoded event is followed by one more line break.
   */
  def fromServerSentEvents[T: Schema](
    data: Iterable[ServerSentEvent[T]],
  )(implicit trace: Trace): Response = {
    val codec = ServerSentEvent.defaultBinaryCodec[T]
    val sb    = new StringBuilder

    data.foreach { event =>
      sb.append(codec.encode(event).asString)
      sb.append("\n")
    }

    Response(
      Status.Ok,
      contentTypeEventStream,
      Body.fromCharSequence(sb.toString()),
    )
  }

  /**
   * Creates a response with content-type set to text/event-stream whose body is
   * the single event `data`, encoded with the default codec for `T`.
   *
   * {{{
   * val tick: Response = Response.fromServerSentEvent(ServerSentEvent("12:00", eventType = Some("tick")))
   * // body: "event: tick\ndata: 12:00\n\n"
   * }}}
   */
  def fromServerSentEvent[T: Schema](data: ServerSentEvent[T])(implicit trace: Trace): Response = {
    val codec = ServerSentEvent.defaultBinaryCodec[T]
    Response(
      Status.Ok,
      contentTypeEventStream,
      Body.fromCharSequence(codec.encode(data).asString),
    )
  }

  /**
   * Creates a new response for the provided socket app: status 101 Switching
   * Protocols, with the app, given the environment of this effect, as the body.
   */
  def fromSocketApp[R](app: WebSocketApp[R])(implicit trace: Trace): ZIO[R, Nothing, Response] = {
    ZIO.environment[R].map { env =>
      Response(
        Status.SwitchingProtocols,
        Headers.empty,
        Body.fromSocketApp(app.provideEnvironment(env)),
      )
    }
  }

  /**
   * Creates a response for `throwable` with `ErrorResponseConfig.default`,
   * which gives an empty body. See the overload that takes an
   * `ErrorResponseConfig` for how the status is chosen.
   */
  def fromThrowable(throwable: Throwable): Response =
    fromThrowable(throwable, ErrorResponseConfig.default)

  /**
   * Creates a new response for the specified throwable. Note that this method
   * relies on the runtime class of the throwable.
   *
   * The status is 403 for `AccessDeniedException`, `IllegalAccessException` and
   * `IllegalAccessError`; 400 for `NotDirectoryException` and
   * `IllegalArgumentException`; 404 for `FileNotFoundException`; 503 for
   * `ConnectException`; 504 for `SocketTimeoutException`; and 500 for anything
   * else. With `config.withErrorBody`, the body shows the status and the
   * message (and, with `config.withStackTrace`, the stack trace) in
   * `config.errorFormat`; otherwise the body is empty.
   */
  def fromThrowable(throwable: Throwable, config: ErrorResponseConfig): Response = {
    throwable match { // TODO: Enhance
      case _: AccessDeniedException  => error(Status.Forbidden, throwableToMessage(throwable, Status.Forbidden, config))
      case _: IllegalAccessException => error(Status.Forbidden, throwableToMessage(throwable, Status.Forbidden, config))
      case _: IllegalAccessError     => error(Status.Forbidden, throwableToMessage(throwable, Status.Forbidden, config))
      case _: NotDirectoryException  =>
        error(Status.BadRequest, throwableToMessage(throwable, Status.BadRequest, config))
      case _: IllegalArgumentException        =>
        error(Status.BadRequest, throwableToMessage(throwable, Status.BadRequest, config))
      case _: java.io.FileNotFoundException   =>
        error(Status.NotFound, throwableToMessage(throwable, Status.NotFound, config))
      case _: java.net.ConnectException       =>
        error(Status.ServiceUnavailable, throwableToMessage(throwable, Status.ServiceUnavailable, config))
      case _: java.net.SocketTimeoutException =>
        error(Status.GatewayTimeout, throwableToMessage(throwable, Status.GatewayTimeout, config))
      case _ => error(Status.InternalServerError, throwableToMessage(throwable, Status.InternalServerError, config))
    }
  }

  private def throwableToMessage(throwable: Throwable, status: Status, config: ErrorResponseConfig): Body =
    if (!config.withErrorBody) Body.empty
    else {
      val rawTrace   = if (config.withStackTrace) throwable.getStackTrace else Array.empty[StackTraceElement]
      val stackTrace =
        if (config.withStackTrace && rawTrace.nonEmpty)
          (if (config.maxStackTraceDepth == 0) rawTrace
           else rawTrace.take(config.maxStackTraceDepth))
            .mkString("\n", "\n", "")
        else ""
      val message    = if (throwable.getMessage eq null) "" else throwable.getMessage
      bodyFromThrowable(message, stackTrace, status, config)
    }

  private def bodyFromThrowable(
    message: String,
    stackTrace: String,
    status: Status,
    config: ErrorResponseConfig,
  ): Body = {
    def htmlResponse: Body = {
      val data = Template.container(s"$status") {
        div(
          div(
            styles := "text-align: center",
            div(s"${status.code}", styles := "font-size: 20em"),
            div(message),
            div(stackTrace),
          ),
        )
      }
      Body.fromString("<!DOCTYPE html>" + data.encode)
    }

    def textResponse: Body =
      Body.fromString {
        val statusCode = status.code
        s"${scala.Console.BOLD}${scala.Console.RED}${status}${scala.Console.RESET} - " +
          s"${scala.Console.BOLD}${scala.Console.CYAN}$statusCode${scala.Console.RESET} - " +
          s"$message" +
          s"${scala.Console.BOLD}${scala.Console.RED} $stackTrace ${scala.Console.RESET}"
      }

    def jsonMessage =
      Body.fromString(
        s"""{"status": "${status.code}", "message": "$message", "stackTrace": "$stackTrace"}""",
      )

    config.errorFormat match {
      case ErrorResponseConfig.ErrorFormat.Html => htmlResponse.contentType(config.errorFormat.mediaType)
      case ErrorResponseConfig.ErrorFormat.Text => textResponse.contentType(config.errorFormat.mediaType)
      case ErrorResponseConfig.ErrorFormat.Json => jsonMessage.contentType(config.errorFormat.mediaType)
    }
  }

  /** Creates an empty response with status 502 Bad Gateway. */
  def badGateway: Response = error(Status.BadGateway)

  /**
   * Creates a response with status 502 Bad Gateway and a body made from
   * `message`. No `Content-Type` header is set.
   */
  def badGateway(message: String): Response = error(Status.BadGateway, message)

  /** Creates an empty response with status 504 Gateway Timeout. */
  def gatewayTimeout: Response = error(Status.GatewayTimeout)

  /**
   * Creates a response with status 504 Gateway Timeout and a body made from
   * `message`. No `Content-Type` header is set.
   */
  def gatewayTimeout(message: String): Response = error(Status.GatewayTimeout, message)

  /**
   * Creates a response with content-type set to text/html. The body is a
   * `DOCTYPE html` declaration followed by the rendered `data`.
   */
  def html(data: Html, status: Status = Status.Ok): Response =
    Response(
      status,
      contentTypeHtml,
      Body.fromString("<!DOCTYPE html>" + data.encode),
    )

  /**
   * Creates a response with content-type set to text/html and status 200. The
   * body is a `DOCTYPE html` declaration followed by the rendered `data`.
   */
  def html(data: zio.http.template2.Dom): Response =
    Response(
      Status.Ok,
      contentTypeHtml,
      Body.fromString("<!DOCTYPE html>" + data.render),
    )

  /**
   * Creates a response with content-type set to text/html. The body is a
   * `DOCTYPE html` declaration followed by the rendered `data`.
   */
  def html(data: zio.http.template2.Dom, status: Status): Response =
    Response(
      status,
      contentTypeHtml,
      Body.fromString("<!DOCTYPE html>" + data.render),
    )

  /** Creates an empty response with status 505 HTTP Version Not Supported. */
  def httpVersionNotSupported: Response = error(Status.HttpVersionNotSupported)

  /**
   * Creates a response with status 505 HTTP Version Not Supported and a body
   * made from `message`. No `Content-Type` header is set.
   */
  def httpVersionNotSupported(message: String): Response = error(Status.HttpVersionNotSupported, message)

  /** Creates an empty response with status 500 Internal Server Error. */
  def internalServerError: Response = error(Status.InternalServerError)

  /**
   * Creates a response with status 500 Internal Server Error and a body made
   * from `message`. No `Content-Type` header is set.
   */
  def internalServerError(message: String): Response = error(Status.InternalServerError, message)

  /**
   * Creates a response with content-type set to application/json
   */
  def json(data: CharSequence): Response =
    Response(
      Status.Ok,
      contentTypeJson,
      Body.fromCharSequence(data),
    )

  /**
   * Creates a response with content-type set to application/json and the
   * specified extra headers.
   */
  def json(data: CharSequence, extraHeaders: Headers): Response =
    Response(
      Status.Ok,
      extraHeaders ++ contentTypeJson,
      Body.fromCharSequence(data),
    )

  /**
   * Creates an empty response with status 511 Network Authentication Required.
   */
  def networkAuthenticationRequired: Response = error(Status.NetworkAuthenticationRequired)

  /**
   * Creates a response with status 511 Network Authentication Required and a
   * body made from `message`. No `Content-Type` header is set.
   */
  def networkAuthenticationRequired(message: String): Response = error(Status.NetworkAuthenticationRequired, message)

  /** Creates an empty response with status 510 Not Extended. */
  def notExtended: Response = error(Status.NotExtended)

  /**
   * Creates a response with status 510 Not Extended and a body made from
   * `message`. No `Content-Type` header is set.
   */
  def notExtended(message: String): Response = error(Status.NotExtended, message)

  /** Creates an empty response with status 404 Not Found. */
  def notFound: Response = error(Status.NotFound)

  /**
   * Creates a response with status 404 Not Found and a body made from
   * `message`. No `Content-Type` header is set.
   */
  def notFound(message: String): Response = error(Status.NotFound, message)

  /** Creates an empty response with status 501 Not Implemented. */
  def notImplemented: Response = error(Status.NotImplemented)

  /**
   * Creates a response with status 501 Not Implemented and a body made from
   * `message`. No `Content-Type` header is set.
   */
  def notImplemented(message: String): Response = error(Status.NotImplemented, message)

  /**
   * Creates an empty response with status 200
   */
  def ok: Response = status(Status.Ok)

  /**
   * Creates an empty response with status 307 or 308 depending on if it's
   * permanent or not.
   *
   * Note: if you intend to always redirect a browser with a HTTP GET to the
   * given location you very likely should use `Response.seeOther` instead.
   */
  def redirect(location: URL, isPermanent: Boolean = false): Response = {
    val status = if (isPermanent) Status.PermanentRedirect else Status.TemporaryRedirect
    Response(status = status, headers = Headers(Header.Location(location)))
  }

  /**
   * Creates an empty response with status 303.
   */
  def seeOther(location: URL): Response =
    Response(status = Status.SeeOther, headers = Headers(Header.Location(location)))

  /** Creates an empty response with status 503 Service Unavailable. */
  def serviceUnavailable: Response = error(Status.ServiceUnavailable)

  /**
   * Creates a response with status 503 Service Unavailable and a body made from
   * `message`. No `Content-Type` header is set.
   */
  def serviceUnavailable(message: String): Response = error(Status.ServiceUnavailable, message)

  /**
   * Creates an empty response with the provided Status
   */
  def status(status: Status): Response =
    Response(status = status)

  /**
   * Creates a response with content-type set to text/plain
   */
  def text(text: CharSequence): Response =
    Response(
      Status.Ok,
      contentTypeText,
      Body.fromCharSequence(text),
    )

  /**
   * Creates a response with content-type set to text/plain and the specified
   * extra headers.
   */
  def text(text: CharSequence, extraHeaders: Headers): Response =
    Response(
      Status.Ok,
      extraHeaders ++ contentTypeText,
      Body.fromCharSequence(text),
    )

  /** Creates an empty response with status 401 Unauthorized. */
  def unauthorized: Response = error(Status.Unauthorized)

  /**
   * Creates a response with status 401 Unauthorized and a body made from
   * `message`. No `Content-Type` header is set.
   */
  def unauthorized(message: String): Response = error(Status.Unauthorized, message)

  private val contentTypeJson: Headers        = Headers(Header.ContentType(MediaType.application.json).untyped)
  private val contentTypeHtml: Headers        = Headers(Header.ContentType(MediaType.text.html).untyped)
  private val contentTypeText: Headers        = Headers(Header.ContentType(MediaType.text.plain).untyped)
  private val contentTypeEventStream: Headers = Headers(Header.ContentType(MediaType.text.`event-stream`).untyped)
}
