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
import java.util.IllegalFormatException

import scala.annotation.tailrec

import zio._
import zio.stacktracer.TracingImplicits.disableAutoTrace

import zio.stream.ZStream

import zio.http.Response._
import zio.http.html.Html
import zio.http.internal.HeaderOps

sealed trait Response extends HeaderOps[Response] { self =>
  def addCookie(cookie: Cookie.Response): Response =
    self.copy(headers = self.headers ++ Headers(Header.SetCookie(cookie)))

  def body: Body

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

  def copy(
    status: Status = self.status,
    headers: Headers = self.headers,
    body: Body = self.body,
  ): Response

  override def equals(o: Any): Boolean = {
    if (o == null) return false

    if (o.getClass() != self.getClass()) return false

    val r = o.asInstanceOf[Response]

    if (r.body != self.body) return false

    if (r.headers != self.headers) return false

    if (r.status != self.status) return false

    if (r.frozen != self.frozen) return false

    if (r.addServerTime != self.addServerTime) return false

    if (r.socketApp != self.socketApp) return false

    true
  }

  def freeze: Response

  def frozen: Boolean = false

  override lazy val hashCode: Int = {
    val prime  = 31
    var result = 1

    result = prime * result + getClass().hashCode()
    result = prime * result + body.hashCode
    result = prime * result + headers.hashCode
    result = prime * result + status.hashCode
    result = prime * result + frozen.hashCode
    result = prime * result + addServerTime.hashCode
    result = prime * result + socketApp.hashCode
    result
  }

  def headers: Headers

  /** Consumes the streaming body fully and then drops it */
  final def ignoreBody(implicit trace: Trace): ZIO[Any, Throwable, Response] =
    self.collect.map(_.copy(body = Body.empty))

  final def isWebSocket: Boolean = self match {
    case _: SocketAppResponse => self.status == Status.SwitchingProtocols
    case _                    => false
  }

  final def patch(p: Response.Patch)(implicit trace: Trace): Response = p.apply(self)

  private[zio] def addServerTime: Boolean = false

  /**
   * Sets the status of the response
   */
  final def status(status: Status): Response =
    self.copy(status = status)

  private[zio] final def socketApp: Option[SocketApp[Any]] = self match {
    case Response.GetApp(app) => Some(app)
    case _                    => None
  }

  def status: Status

  /**
   * Creates an Http from a Response
   */
  final def toHandler(implicit trace: Trace): Handler[Any, Nothing, Any, Response] = Handler.response(self)

  def serverTime: Response
}

object Response {

  private trait InternalState extends Response {
    private[Response] def parent: Response

    override def frozen: Boolean = parent.frozen

    override def addServerTime: Boolean = parent.addServerTime
  }

  object GetApp {
    def unapply(response: Response): Option[SocketApp[Any]] = response match {
      case resp: SocketAppResponse => Some(resp.socketApp0)
      case _                       => None
    }
  }

  private[zio] trait CloseableResponse extends Response {
    def close(implicit trace: Trace): Task[Unit]
  }

  private[zio] class BasicResponse(
    val body: Body,
    val headers: Headers,
    val status: Status,
  ) extends Response { self =>

    override def copy(status: Status, headers: Headers, body: Body): Response =
      new BasicResponse(body, headers, status) with InternalState {
        override val parent: Response = self
      }

    override def freeze: Response = new BasicResponse(body, headers, status) with InternalState {

      override val parent: Response = self

      override def frozen: Boolean = true

    }

    override def toString(): String = s"Response(status = $status, headers = $headers, body = $body)"

    override def updateHeaders(update: Headers => Headers)(implicit trace: Trace): Response =
      copy(headers = update(headers))

    override def serverTime: Response = new BasicResponse(body, headers, status) with InternalState {

      override val parent: Response = self

      override def addServerTime: Boolean = true
    }

  }

  private[zio] class SocketAppResponse(
    val body: Body,
    val headers: Headers,
    val socketApp0: SocketApp[Any],
    val status: Status,
  ) extends Response { self =>

    override final def copy(status: Status, headers: Headers, body: Body): Response =
      new SocketAppResponse(body, headers, socketApp0, status) with InternalState {
        override val parent: Response = self
      }

    override final def freeze: Response = new SocketAppResponse(body, headers, socketApp0, status) with InternalState {

      override val parent: Response = self

      override def frozen: Boolean = true

    }

    override final def toString(): String =
      s"SocketAppResponse(status = $status, headers = $headers, body = $body, socketApp = $socketApp0)"

    override final def updateHeaders(update: Headers => Headers)(implicit trace: Trace): Response =
      copy(headers = update(headers))

    override final def serverTime: Response = new SocketAppResponse(body, headers, socketApp0, status)
      with InternalState {

      override val parent: Response = self

      override def addServerTime: Boolean = true
    }

  }

  private[zio] class NativeResponse(
    val body: Body,
    val headers: Headers,
    val status: Status,
    onClose: () => Task[Unit],
  ) extends CloseableResponse { self =>

    override final def close(implicit trace: Trace): Task[Unit] = onClose()

    override final def copy(status: Status, headers: Headers, body: Body): Response =
      new NativeResponse(body, headers, status, onClose) with InternalState {
        override val parent: Response = self
      }

    override final def freeze: Response = new NativeResponse(body, headers, status, onClose) with InternalState {
      override val parent: Response = self
      override def frozen: Boolean  = true
    }

    override final def toString(): String =
      s"NativeResponse(status = $status, headers = $headers, body = $body)"

    override final def updateHeaders(update: Headers => Headers)(implicit trace: Trace): Response =
      copy(headers = update(headers))

    override final def serverTime: Response = new NativeResponse(body, headers, status, onClose) with InternalState {
      override val parent: Response = self

      override def addServerTime: Boolean = true
    }
  }

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

  def apply(
    status: Status = Status.Ok,
    headers: Headers = Headers.empty,
    body: Body = Body.empty,
  ): Response = new BasicResponse(body, headers, status)

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
    new BasicResponse(Body.fromStream(data.map(_.encode)), contentTypeEventStream, Status.Ok)

  /**
   * Creates a new response for the provided socket app
   */
  def fromSocketApp[R](app: SocketApp[R])(implicit trace: Trace): ZIO[R, Nothing, Response] = {
    ZIO.environment[R].map { env =>
      new SocketAppResponse(
        Body.empty,
        Headers.empty,
        app.provideEnvironment(env),
        Status.SwitchingProtocols,
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
    new BasicResponse(
      Body.fromString("<!DOCTYPE html>" + data.encode),
      contentTypeHtml,
      status,
    )

  def httpVersionNotSupported: Response = error(Status.HttpVersionNotSupported)

  def httpVersionNotSupported(message: String): Response = error(Status.HttpVersionNotSupported, message)

  def internalServerError: Response = error(Status.InternalServerError)

  def internalServerError(message: String): Response = error(Status.InternalServerError, message)

  /**
   * Creates a response with content-type set to application/json
   */
  def json(data: CharSequence): Response =
    new BasicResponse(
      Body.fromCharSequence(data),
      contentTypeJson,
      Status.Ok,
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
  def ok: Response = new BasicResponse(Body.empty, Headers.empty, Status.Ok)

  /**
   * Creates an empty response with status 301 or 302 depending on if it's
   * permanent or not.
   */
  def redirect(location: URL, isPermanent: Boolean = false): Response = {
    val status = if (isPermanent) Status.PermanentRedirect else Status.TemporaryRedirect
    new BasicResponse(Body.empty, Headers(Header.Location(location)), status)
  }

  /**
   * Creates an empty response with status 303
   */
  def seeOther(location: URL): Response =
    new BasicResponse(Body.empty, Headers(Header.Location(location)), Status.SeeOther)

  def serviceUnavailable: Response = error(Status.ServiceUnavailable)

  def serviceUnavailable(message: String): Response = error(Status.ServiceUnavailable, message)

  /**
   * Creates an empty response with the provided Status
   */
  def status(status: Status): Response = new BasicResponse(Body.empty, Headers.empty, status)

  /**
   * Creates a response with content-type set to text/plain
   */
  def text(text: CharSequence): Response =
    new BasicResponse(
      Body.fromCharSequence(text),
      contentTypeText,
      Status.Ok,
    )

  def unauthorized: Response = error(Status.Unauthorized)

  def unauthorized(message: String): Response = error(Status.Unauthorized, message)

  private lazy val contentTypeJson: Headers        = Headers(Header.ContentType(MediaType.application.json).untyped)
  private lazy val contentTypeHtml: Headers        = Headers(Header.ContentType(MediaType.text.html).untyped)
  private lazy val contentTypeText: Headers        = Headers(Header.ContentType(MediaType.text.plain).untyped)
  private lazy val contentTypeEventStream: Headers =
    Headers(Header.ContentType(MediaType.text.`event-stream`).untyped)
}
