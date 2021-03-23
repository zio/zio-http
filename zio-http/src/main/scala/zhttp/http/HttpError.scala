package zhttp.http

sealed abstract class HttpError(val status: Status, val message: String) extends Throwable(message) { self =>
  def toResponse: UResponse = Response.fromHttpError(self)
}

abstract class HTTPErrorWithCause(status: Status, msg: String) extends HttpError(status, msg) {
  def cause: Option[Throwable]
  cause.foreach(initCause)
}

object HttpError {
  def unapply(err: Throwable): Option[(Status, String)] = err match {
    case err: HttpError => Option((err.status, err.getMessage))
    case _              => None
  }

  final case class BadRequest(msg: String) extends HttpError(Status.BAD_REQUEST, msg)

  final case class Unauthorized(msg: String) extends HttpError(Status.UNAUTHORIZED, msg)

  final case class Forbidden(msg: String) extends HttpError(Status.FORBIDDEN, msg)

  final case class NotFound(path: Path)
      extends HttpError(Status.NOT_FOUND, s"""The requested URI "${path.asString}" was not found on this server\n""")

  final case class MethodNotAllowed(msg: String) extends HttpError(Status.METHOD_NOT_ALLOWED, msg)

  final case class RequestTimeout(msg: String) extends HttpError(Status.REQUEST_TIMEOUT, msg)

  final case class RequestEntityTooLarge(msg: String) extends HttpError(Status.REQUEST_ENTITY_TOO_LARGE, msg)

  final case class InternalServerError(msg: String, cause: Option[Throwable] = None)
      extends HTTPErrorWithCause(Status.INTERNAL_SERVER_ERROR, msg)

  final case class NotImplemented(msg: String) extends HttpError(Status.NOT_IMPLEMENTED, msg)

  final case class HttpVersionNotSupported(msg: String) extends HttpError(Status.HTTP_VERSION_NOT_SUPPORTED, msg)

  final case class ServiceUnavailable(msg: String) extends HttpError(Status.SERVICE_UNAVAILABLE, msg)

}
