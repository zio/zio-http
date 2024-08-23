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

import zio.http.internal.{HeaderOps, OutputEncoder}
import zio.http.template._

final case class Response(
  status: Status = Status.Ok,
  headers: Headers = Headers.empty,
  body: Body = Body.empty,
) extends HeaderOps[Response] { self =>

  // To be used by encoders to avoid re-encoding static responses (optimization)
  private[http] var encoded: AnyRef = null

  def addCookie(cookie: Cookie.Response): Response =
    self.copy(headers = self.headers ++ Headers(Header.SetCookie(cookie)))

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

  def contentType(json: MediaType): Response =
    self.addHeader(Header.ContentType(json))

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

  def patch(p: Response.Patch)(implicit trace: Trace): Response = p.apply(self)

  /**
   * Sets the status of the response
   */
  def status(status: Status): Response =
    copy(status = status)

  /**
   * Creates an Http from a Response
   */
  def toHandler(implicit trace: Trace): Handler[Any, Nothing, Any, Response] = Handler.fromResponse(self)

  /**
   * Updates the current Headers with new one, using the provided update
   * function passed.
   */
  override def updateHeaders(update: Headers => Headers)(implicit trace: Trace): Response =
    copy(headers = update(headers))

}

object Response {

  /**
   * Models the set of operations that one would want to apply on a Response.
   */
  sealed trait Patch { self =>
    def ++(that: Patch): Patch                                = Patch.Combine(self, that)
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

  object Patch {
    import Header.HeaderType

    case object Empty                                     extends Patch
    final case class AddHeaders(headers: Headers)         extends Patch
    final case class RemoveHeaders(headers: Set[String])  extends Patch
    final case class SetStatus(status: Status)            extends Patch
    final case class Combine(left: Patch, right: Patch)   extends Patch
    final case class UpdateHeaders(f: Headers => Headers) extends Patch

    def empty: Patch = Empty

    def addHeader(headerType: HeaderType)(value: headerType.HeaderValue): Patch =
      addHeader(headerType.name, headerType.render(value))

    def addHeader(header: Header): Patch                          = addHeaders(Headers(header))
    def addHeaders(headers: Headers): Patch                       = AddHeaders(headers)
    def addHeader(name: CharSequence, value: CharSequence): Patch = addHeaders(Headers(name, value))

    def removeHeaders(headerTypes: Set[HeaderType]): Patch = RemoveHeaders(headerTypes.map(_.name))
    def status(status: Status): Patch                      = SetStatus(status)
    def updateHeaders(f: Headers => Headers): Patch        = UpdateHeaders(f)
  }

  def badRequest: Response =
    error(Status.BadRequest)

  def badRequest(message: String): Response =
    error(Status.BadRequest, message)

  def error(status: Status.Error, message: String): Response =
    Response(status = status, body = Body.fromString(OutputEncoder.encodeHtml(message)))

  def error(status: Status.Error, body: Body): Response =
    Response(
      status = status,
      body = body,
      headers = if (body.mediaType.isEmpty) Headers.empty else Headers(Header.ContentType(body.mediaType.get)),
    )

  def error(status: Status.Error): Response =
    Response(status = status)

  def forbidden: Response =
    error(Status.Forbidden)

  def forbidden(message: String): Response =
    error(Status.Forbidden, message)

  def fromCause(cause: Cause[Any]): Response =
    fromCause(cause, ErrorResponseConfig.default)

  /**
   * Creates a new response from the specified cause. Note that this method is
   * not polymorphic, but will attempt to inspect the runtime class of the
   * failure inside the cause, if any.
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
      Body.fromCharSequenceStreamChunked(data.map(codec.encode).map(_.asString)),
    )
  }

  /**
   * Creates a new response for the provided socket app
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

  def fromThrowable(throwable: Throwable): Response =
    fromThrowable(throwable, ErrorResponseConfig.default)

  /**
   * Creates a new response for the specified throwable. Note that this method
   * relies on the runtime class of the throwable.
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
      val stackTrace =
        if (config.withStackTrace && throwable.getStackTrace.nonEmpty)
          (if (config.maxStackTraceDepth == 0) throwable.getStackTrace
           else throwable.getStackTrace.take(config.maxStackTraceDepth))
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

  def gatewayTimeout: Response = error(Status.GatewayTimeout)

  def gatewayTimeout(message: String): Response = error(Status.GatewayTimeout, message)

  /**
   * Creates a response with content-type set to text/html
   */
  def html(data: Html, status: Status = Status.Ok): Response =
    Response(
      status,
      contentTypeHtml,
      Body.fromString("<!DOCTYPE html>" + data.encode),
    )

  def httpVersionNotSupported: Response = error(Status.HttpVersionNotSupported)

  def httpVersionNotSupported(message: String): Response = error(Status.HttpVersionNotSupported, message)

  def internalServerError: Response = error(Status.InternalServerError)

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

  def networkAuthenticationRequired: Response = error(Status.NetworkAuthenticationRequired)

  def networkAuthenticationRequired(message: String): Response = error(Status.NetworkAuthenticationRequired, message)

  def notExtended: Response = error(Status.NotExtended)

  def notExtended(message: String): Response = error(Status.NotExtended, message)

  def notFound: Response = error(Status.NotFound)

  def notFound(message: String): Response = error(Status.NotFound, message)

  def notImplemented: Response = error(Status.NotImplemented)

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
   * given location you very likely should use `Response#seeOther` instead.
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

  def serviceUnavailable: Response = error(Status.ServiceUnavailable)

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

  def unauthorized: Response = error(Status.Unauthorized)

  def unauthorized(message: String): Response = error(Status.Unauthorized, message)

  private lazy val contentTypeJson: Headers        = Headers(Header.ContentType(MediaType.application.json).untyped)
  private lazy val contentTypeHtml: Headers        = Headers(Header.ContentType(MediaType.text.html).untyped)
  private lazy val contentTypeText: Headers        = Headers(Header.ContentType(MediaType.text.plain).untyped)
  private lazy val contentTypeEventStream: Headers =
    Headers(Header.ContentType(MediaType.text.`event-stream`).untyped)
}
