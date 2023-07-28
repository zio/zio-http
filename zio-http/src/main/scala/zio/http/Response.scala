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

import zio._
import zio.http.html.Html
import zio.http.internal.HeaderOps
import zio.stream.ZStream
import zio.stacktracer.TracingImplicits.disableAutoTrace

import java.nio.file.{AccessDeniedException, NotDirectoryException}
import scala.annotation.tailrec

final case class Response(
  status: Status,
  headers: Headers,
  body: Body,
  socketApp: Option[SocketApp[Any]], // TODO: move to Body
) extends HeaderOps[Response] { self =>

  private[http] var encoded: AnyRef = null

  def addCookie(cookie: Cookie.Response): Response =
    self.copy(headers = self.headers ++ Headers(Header.SetCookie(cookie)))

  /**
   * Collects the potentially streaming body of the response into a single
   * chunk.
   */
  def collect(implicit trace: Trace): ZIO[Any, Throwable, Response] =
    if (self.body.isComplete) ZIO.succeed(self)
    else
      self.body.asChunk.map { bytes =>
        self.copy(body = Body.fromChunk(bytes))
      }

  /** Consumes the streaming body fully and then drops it */
  def ignoreBody(implicit trace: Trace): ZIO[Any, Throwable, Response] =
    self.collect.map(_.copy(body = Body.empty))

  def isWebSocket: Boolean =
    socketApp.isDefined && self.status == Status.SwitchingProtocols

  def patch(p: Response.Patch)(implicit trace: Trace): Response = p.apply(self)

  /**
   * Sets the status of the response
   */
  def status(status: Status): Response =
    copy(status = status)

  /**
   * Creates an Http from a Response
   */
  def toHandler(implicit trace: Trace): Handler[Any, Nothing, Any, Response] = Handler.response(self)

  /**
   * Updates the current Headers with new one, using the provided update
   * function passed.
   */
  override def updateHeaders(update: Headers => Headers)(implicit trace: Trace): Response =
    copy(headers = update(headers))
}

object Response {

  // TODO: move to handler
  private[zio] trait CloseableResponse {
    def close(implicit trace: Trace): Task[Unit]
  }

  private[zio] class NativeResponse(
    response: Response,
    onClose: () => Task[Unit],
  ) extends CloseableResponse { self =>

    override final def close(implicit trace: Trace): Task[Unit] = onClose()

  }
  // end TODO

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

  // TODO: remove after removing socketApp from Response model
  def apply(
    status: Status = Status.Ok,
    headers: Headers = Headers.empty,
    body: Body = Body.empty,
  ): Response = Response(status, headers, body, None)

  def badRequest: Response = error(Status.BadRequest)

  def badRequest(message: String): Response = error(Status.BadRequest, message)

  def error(status: Status.Error, message: String): Response = {
    import zio.http.internal.OutputEncoder

    val message2 = OutputEncoder.encodeHtml(if (message == null) status.text else message)

    Response(status = status, headers = Headers(Header.Warning(status.code, "ZIO HTTP", message2)))
  }

  def error(status: Status.Error): Response =
    error(status, status.text)

  def forbidden: Response = error(Status.Forbidden)

  def forbidden(message: String): Response = error(Status.Forbidden, message)

  /**
   * Creates a new response from the specified cause. Note that this method is
   * not polymorphic, but will attempt to inspect the runtime class of the
   * failure inside the cause, if any.
   */
  def fromCause(cause: Cause[Any]): Response = {
    cause.failureOrCause match {
      case Left(failure: Response)  => failure
      case Left(failure: Throwable) => fromThrowable(failure)
      case Left(failure: Cause[_])  => fromCause(failure)
      case _                        =>
        if (cause.isInterruptedOnly) error(Status.RequestTimeout, cause.prettyPrint.take(100))
        else error(Status.InternalServerError, cause.prettyPrint.take(100))
    }
  }

  /**
   * Creates a new response from the specified cause, translating any typed
   * error to a response using the provided function.
   */
  def fromCauseWith[E](cause: Cause[E])(f: E => Response): Response = {
    cause.failureOrCause match {
      case Left(failure) => f(failure)
      case Right(cause)  => fromCause(cause)
    }
  }

  /**
   * Creates a response with content-type set to text/event-stream
   * @param data
   *   \- stream of data to be sent as Server Sent Events
   */
  def fromServerSentEvents(data: ZStream[Any, Nothing, ServerSentEvent])(implicit trace: Trace): Response =
    Response(Status.Ok, contentTypeEventStream, Body.fromStream(data.map(_.encode)))

  /**
   * Creates a new response for the provided socket
   */
  def fromSocket[R](
    http: Handler[R, Throwable, WebSocketChannel, Any],
  )(implicit trace: Trace): ZIO[R, Nothing, Response] =
    fromSocketApp(http)

  /**
   * Creates a new response for the provided socket app
   */
  def fromSocketApp[R](app: SocketApp[R])(implicit trace: Trace): ZIO[R, Nothing, Response] = {
    ZIO.environment[R].map { env =>
      Response(
        Status.SwitchingProtocols,
        Headers.empty,
        Body.empty,
        Some(app.provideEnvironment(env)),
      )
    }
  }

  /**
   * Creates a new response for the specified throwable. Note that this method
   * relies on the runtime class of the throwable.
   */
  def fromThrowable(throwable: Throwable): Response = {
    throwable match { // TODO: Enhance
      case _: AccessDeniedException           => error(Status.Forbidden, throwable.getMessage)
      case _: IllegalAccessException          => error(Status.Forbidden, throwable.getMessage)
      case _: IllegalAccessError              => error(Status.Forbidden, throwable.getMessage)
      case _: NotDirectoryException           => error(Status.BadRequest, throwable.getMessage)
      case _: IllegalArgumentException        => error(Status.BadRequest, throwable.getMessage)
      case _: java.io.FileNotFoundException   => error(Status.NotFound, throwable.getMessage)
      case _: java.net.ConnectException       => error(Status.ServiceUnavailable, throwable.getMessage)
      case _: java.net.SocketTimeoutException => error(Status.GatewayTimeout, throwable.getMessage)
      case _                                  => error(Status.InternalServerError, throwable.getMessage)
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
   * Creates an empty response with status 301 or 302 depending on if it's
   * permanent or not.
   */
  def redirect(location: URL, isPermanent: Boolean = false): Response = {
    val status = if (isPermanent) Status.PermanentRedirect else Status.TemporaryRedirect
    Response(status = status, headers = Headers(Header.Location(location)))
  }

  /**
   * Creates an empty response with status 303
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
