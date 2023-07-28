package zio.http

import zio._
import zio.stream.ZStream

import zio.http.internal.HeaderOps

final case class Response(
  status: Status,
  headers: Headers = Headers.empty,
  body: Body = Body.empty,
  frozen: Boolean = false,
  socketApp: Option[SocketApp[Any]] = None,
) extends HeaderOps[Response] {

  def addCookie(cookie: Cookie.Response): Response =
    copy(headers = headers ++ Headers(Header.SetCookie(cookie)))

  /**
   * Collects the potentially streaming body of the response into a single
   * chunk.
   */
  def collect: ZIO[Any, Throwable, Response] =
    if (body.isComplete) ZIO.succeed(this)
    else
      body.asChunk.map { bytes =>
        copy(body = Body.fromChunk(bytes))
      }

  override def updateHeaders(update: Headers => Headers): Response =
    copy(headers = update(headers))

  def copy(status: Status = this.status, headers: Headers = this.headers, body: Body = this.body): Response =
    Response(status, headers, body)

  def freeze: Response = this

  override def equals(o: Any): Boolean = o match {
    case that: Response =>
      this.body == that.body &&
        this.headers == that.headers &&
        this.status == that.status
    case _ => false
  }

  override lazy val hashCode: Int =
    31 * (31 * (31 * (31 * (31 * (31 * getClass.hashCode + body.hashCode) + headers.hashCode) + status.hashCode)))

  override def toString(): String =
    s"Response(status = $status, headers = $headers, body = $body)"

  private[zio] final def httpError: Option[HttpError] = this match {
    case ErrorResponse(error) => Some(error)
    case _                    => None
  }

  final def ignoreBody: ZIO[Any, Throwable, Response] =
    collect.map(_.copy(body = Body.empty))

  final def isWebSocket: Boolean = this match {
    case _: SocketAppResponse => status == Status.SwitchingProtocols
    case _                    => false
  }

  final def patch(p: Response.Patch): Response = p.apply(this)

  private[zio] def addServerTime: Boolean = false

  /**
   * Sets the status of the response
   */
  final def status(status: Status): Response =
    copy(status = status)

  private[zio] final def socketApp: Option[SocketApp[Any]] = this match {
    case SocketAppResponse(app) => Some(app)
    case _                      => None
  }

  /**
   * Creates an Http from a Response
   */
  final def toHandler(implicit trace: Trace): Handler[Any, Nothing, Any, Response] = Handler.response(this)

  def serverTime: Response = copy()

}

object ResponseObject {
  import Response._

  private trait InternalState extends Response {
    private[Response] def parent: Response

    override def frozen: Boolean = parent.frozen

    override def addServerTime: Boolean = parent.addServerTime
  }

  object SocketAppResponse {
    def unapply(response: Response): Option[SocketApp[Any]] = response match {
      case resp: Response.SocketAppResponse => Some(resp.socketApp0)
      case _                                => None
    }
  }

  object ErrorResponse {
    def unapply(response: Response): Option[HttpError] = response match {
      case resp: Response.ErrorResponse => Some(resp.httpError0)
      case _                            => None
    }
  }

  private[zio] trait CloseableResponse extends Response {
    def close(implicit trace: Trace): Task[Unit]
  }

  final case class BasicResponse(
    body: Body,
    headers: Headers,
    status: Status
  ) extends Response {
    override def freeze: Response = this
    override def serverTime: Response = copy()
  }

  final case class SocketAppResponse(
    body: Body,
    headers: Headers,
    socketApp0: SocketApp[Any],
    status: Status
  ) extends Response {
    override def freeze: Response = this
    override def serverTime: Response = copy()
  }

  final case class ErrorResponse(
    body: Body,
    headers: Headers,
    httpError0: HttpError,
    status: Status
  ) extends Response {
    override def freeze: Response = this
    override def serverTime: Response = copy()
  }

  final case class NativeResponse(
    body: Body,
    headers: Headers,
    status: Status,
    onClose: () => Task[Unit]
  ) extends CloseableResponse {
    override def freeze: Response = this
    override def serverTime: Response = copy()
    override def close(implicit trace: Trace): Task[Unit] = onClose()
  }

  /**
   * Models the set of operations that one would want to apply on a Response.
   */
  sealed trait Patch { self =>
    def ++(that: Patch): Patch = Patch.Combine(self, that)
    def apply(res: Response): Response = {

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

    case object Empty extends Patch
    final case class AddHeaders(headers: Headers) extends Patch
    final case class RemoveHeaders(headers: Set[String]) extends Patch
    final case class SetStatus(status: Status) extends Patch
    final case class Combine(left: Patch, right: Patch) extends Patch
    final case class UpdateHeaders(f: Headers => Headers) extends Patch

    def empty: Patch = Empty

    def addHeader(headerType: HeaderType)(value: headerType.HeaderValue): Patch =
      addHeader(headerType.name, headerType.render(value))

    def addHeader(header: Header): Patch = addHeaders(Headers(header))
    def addHeaders(headers: Headers): Patch = AddHeaders(headers)
    def addHeader(name: CharSequence, value: CharSequence): Patch = addHeaders(Headers(name, value))

    def removeHeaders(headerTypes: Set[HeaderType]): Patch = RemoveHeaders(headerTypes.map(_.name))
    def status(status: Status): Patch = SetStatus(status)
    def updateHeaders(f: Headers => Headers): Patch = UpdateHeaders(f)
  }

  def fromHttpError(error: HttpError): Response =
    ErrorResponse(Body.empty, Headers.empty, error, error.status)

  /**
   * Creates a new response for the provided socket
   */
  def fromSocket[R](
    http: Handler[R, Throwable, WebSocketChannel, Any]
  )(implicit trace: Trace): ZIO[R, Nothing, Response] =
    fromSocketApp(http)

  /**
   * Creates a new response for the provided socket app
   */
  def fromSocketApp[R](app: SocketApp[R])(implicit trace: Trace): ZIO[R, Nothing, Response] = {
    ZIO.environment[R].map { env =>
      SocketAppResponse(
        Body.empty,
        Headers.empty,
        app.provideEnvironment(env),
        Status.SwitchingProtocols
      )
    }
  }

  def html(data: Html, status: Status = Status.Ok): Response =
    BasicResponse(
      Body.fromString("<!DOCTYPE html>" + data.encode),
      contentTypeHtml,
      status
    )

  def json(data: CharSequence): Response =
    BasicResponse(
      Body.fromCharSequence(data),
      contentTypeJson,
      Status.Ok
    )

  def ok: Response = BasicResponse(Body.empty, Headers.empty, Status.Ok)

  def redirect(location: URL, isPermanent: Boolean = false): Response = {
    val status = if (isPermanent) Status.PermanentRedirect else Status.TemporaryRedirect
    BasicResponse(Body.empty, Headers(Header.Location(location)), status)
  }

  def seeOther(location: URL): Response =
    BasicResponse(Body.empty, Headers(Header.Location(location)), Status.SeeOther)

  def status(status: Status): Response = BasicResponse(Body.empty, Headers.empty, status)

  def text(text: CharSequence): Response =
    BasicResponse(
      Body.fromCharSequence(text),
      contentTypeText,
      Status.Ok
    )

  def fromServerSentEvents(data: ZStream[Any, Nothing, ServerSentEvent]): Response =
    BasicResponse(Body.fromStream(data.map(_.encode)), contentTypeEventStream, Status.Ok)

  private lazy val contentTypeJson: Headers = Headers(Header.ContentType(MediaType.application.json).untyped)
  private lazy val contentTypeHtml: Headers = Headers(Header.ContentType(MediaType.text.html).untyped)
  private lazy val contentTypeText: Headers = Headers(Header.ContentType(MediaType.text.plain).untyped)
  private lazy val contentTypeEventStream: Headers =
    Headers(Header.ContentType(MediaType.text.`event-stream`).untyped)
}
