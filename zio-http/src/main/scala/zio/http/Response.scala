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
import zio.stacktracer.TracingImplicits.disableAutoTrace

import zio.http.Response._
import zio.http.html.Html
import zio.http.model._
import zio.http.model.headers.HeaderExtension
import zio.http.socket._

sealed trait Response extends HeaderExtension[Response] { self =>

  def addCookie(cookie: Cookie[Response]): Response =
    self.copy(headers = self.headers ++ Headers.setCookie(cookie))

  def body: Body

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

    if (r.serverTime != self.serverTime) return false

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
    result = prime * result + serverTime.hashCode
    result = prime * result + socketApp.hashCode
    result
  }

  def headers: Headers

  private[zio] final def httpError: Option[HttpError] = self match {
    case Response.GetError(error) => Some(error)
    case _                        => None
  }

  final def isWebSocket: Boolean = self match {
    case _: SocketAppResponse => self.status == Status.SwitchingProtocols
    case _                    => false
  }

  final def patch(p: Response.Patch): Response =
    copy(headers = self.headers ++ p.addHeaders, status = p.setStatus.getOrElse(self.status))

  private[zio] def serverTime: Boolean = false

  /**
   * Sets the status of the response
   */
  final def setStatus(status: Status): Response =
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

  def withServerTime: Response
}

object Response {

  private trait InternalState extends Response {
    private[Response] def parent: Response

    override def frozen: Boolean = parent.frozen

    override def serverTime: Boolean = parent.serverTime
  }

  object GetApp {
    def unapply(response: Response): Option[SocketApp[Any]] = response match {
      case resp: SocketAppResponse => Some(resp.socketApp0)
      case _                       => None
    }
  }

  object GetError {
    def unapply(response: Response): Option[HttpError] = response match {
      case resp: ErrorResponse => Some(resp.httpError0)
      case _                   => None
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

    override def updateHeaders(update: Headers => Headers): Response = copy(headers = update(headers))

    override def withServerTime: Response = new BasicResponse(body, headers, status) with InternalState {

      override val parent: Response = self

      override def serverTime: Boolean = true
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

    override final def updateHeaders(update: Headers => Headers): Response = copy(headers = update(headers))

    override final def withServerTime: Response = new SocketAppResponse(body, headers, socketApp0, status)
      with InternalState {

      override val parent: Response = self

      override def serverTime: Boolean = true
    }

  }

  private[zio] class ErrorResponse(val body: Body, val headers: Headers, val httpError0: HttpError, val status: Status)
      extends Response { self =>

    override def copy(status: Status, headers: Headers, body: Body): Response =
      new ErrorResponse(body, headers, httpError0, status) with InternalState {
        override val parent: Response = self
      }

    override def freeze: Response = new ErrorResponse(body, headers, httpError0, status) with InternalState {
      override val parent: Response = self
      override def frozen: Boolean  = true
    }

    override final def updateHeaders(update: Headers => Headers): Response = copy(headers = update(headers))

    override final def withServerTime: Response = new ErrorResponse(body, headers, httpError0, status)
      with InternalState {

      override val parent: Response = self

      override def serverTime: Boolean = true
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

    override final def updateHeaders(update: Headers => Headers): Response = copy(headers = update(headers))

    override final def withServerTime: Response = new NativeResponse(body, headers, status, onClose)
      with InternalState {
      override val parent: Response = self

      override def serverTime: Boolean = true
    }
  }

  final case class Patch(addHeaders: Headers, setStatus: Option[Status]) { self =>
    def ++(that: Patch): Patch =
      Patch(self.addHeaders ++ that.addHeaders, self.setStatus.orElse(that.setStatus))
  }

  def apply(
    status: Status = Status.Ok,
    headers: Headers = Headers.empty,
    body: Body = Body.empty,
  ): Response = new BasicResponse(body, headers, status)

  def fromHttpError(error: HttpError): Response = new ErrorResponse(Body.empty, Headers.empty, error, error.status)

  /**
   * Creates a new response for the provided socket
   */
  def fromSocket[R](
    http: Handler[R, Throwable, ChannelEvent[WebSocketFrame, WebSocketFrame], Unit],
  )(implicit trace: Trace): ZIO[R, Nothing, Response] =
    fromSocketApp(http.toSocketApp)

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
   * Creates a response with content-type set to text/html
   */
  def html(data: Html, status: Status = Status.Ok): Response =
    new BasicResponse(
      Body.fromString("<!DOCTYPE html>" + data.encode),
      Headers(HeaderNames.contentType, HeaderValues.textHtml),
      status,
    )

  /**
   * Creates a response with content-type set to application/json
   */
  def json(data: CharSequence): Response =
    new BasicResponse(
      Body.fromCharSequence(data),
      Headers(HeaderNames.contentType, HeaderValues.applicationJson),
      Status.Ok,
    )

  /**
   * Creates an empty response with status 200
   */
  def ok: Response = new BasicResponse(Body.empty, Headers.empty, Status.Ok)

  /**
   * Creates an empty response with status 301 or 302 depending on if it's
   * permanent or not.
   */
  def redirect(location: CharSequence, isPermanent: Boolean = false): Response = {
    val status = if (isPermanent) Status.PermanentRedirect else Status.TemporaryRedirect
    new BasicResponse(Body.empty, Headers.location(location), status)
  }

  /**
   * Creates an empty response with status 303
   */
  def seeOther(location: CharSequence): Response =
    new BasicResponse(Body.empty, Headers.location(location), Status.SeeOther)

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
      Headers(HeaderNames.contentType, HeaderValues.textPlain),
      Status.Ok,
    )
}
