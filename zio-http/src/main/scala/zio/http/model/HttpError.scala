package zio.http.model

import zio.http.Response
import zio.http.model.HttpError.HTTPErrorWithCause
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

sealed abstract class HttpError(val status: Status, val message: String) extends Throwable(message) { self =>
  def foldCause[A](a: A)(f: Throwable => A): A = self match {
    case error: HTTPErrorWithCause =>
      error.cause match {
        case Some(throwable) => f(throwable)
        case None            => a
      }
    case _                         => a

  }

  def toResponse: Response = Response.fromHttpError(self)
}

object HttpError {
  def unapply(err: Throwable): Option[(Status, String)] = err match {
    case err: HttpError => Option((err.status, err.getMessage))
    case _              => None
  }

  sealed abstract class HTTPErrorWithCause(status: Status, msg: String) extends HttpError(status, msg) {
    def cause: Option[Throwable]
    cause.foreach(initCause)
  }

  final case class BadRequest(msg: String = "Bad Request") extends HttpError(Status.BadRequest, msg)

  final case class Unauthorized(msg: String = "Unauthorized") extends HttpError(Status.Unauthorized, msg)

  final case class PaymentRequired(msg: String = "Payment Required") extends HttpError(Status.PaymentRequired, msg)

  final case class Forbidden(msg: String = "Forbidden") extends HttpError(Status.Forbidden, msg)

  final case class NotFound(path: String)
      extends HttpError(Status.NotFound, s"""The requested URI "${path}" was not found on this server\n""")

  final case class MethodNotAllowed(msg: String = "Method Not Allowed") extends HttpError(Status.MethodNotAllowed, msg)

  final case class NotAcceptable(msg: String = "Not Acceptable") extends HttpError(Status.NotAcceptable, msg)

  final case class ProxyAuthenticationRequired(msg: String = "Proxy Authentication Required")
      extends HttpError(Status.ProxyAuthenticationRequired, msg)

  final case class Conflict(msg: String = "Conflict") extends HttpError(Status.Conflict, msg)

  final case class Gone(msg: String = "Gone") extends HttpError(Status.Gone, msg)

  final case class LengthRequired(msg: String = "Length Required") extends HttpError(Status.LengthRequired, msg)

  final case class PreconditionFailed(msg: String = "Precondition Failed")
      extends HttpError(Status.PreconditionFailed, msg)

  final case class RequestTimeout(msg: String = "Request Timeout") extends HttpError(Status.RequestTimeout, msg)

  final case class RequestEntityTooLarge(msg: String = "Request Entity Too Large")
      extends HttpError(Status.RequestEntityTooLarge, msg)

  final case class RequestUriTooLong(msg: String = "Request-URI Too Long")
      extends HttpError(Status.RequestUriTooLong, msg)

  final case class UnsupportedMediaType(msg: String = "Unsupported Media Type")
      extends HttpError(Status.UnsupportedMediaType, msg)

  final case class RequestedRangeNotSatisfiable(msg: String = "Requested Range Not Satisfiable")
      extends HttpError(Status.RequestedRangeNotSatisfiable, msg)

  final case class ExpectationFailed(msg: String = "Expectation Failed")
      extends HttpError(Status.ExpectationFailed, msg)

  final case class MisdirectedRequest(msg: String = "Misdirected Request")
      extends HttpError(Status.MisdirectedRequest, msg)

  final case class UnprocessableEntity(msg: String = "Unprocessable Entity")
      extends HttpError(Status.UnprocessableEntity, msg)

  final case class Locked(msg: String = "Locked") extends HttpError(Status.Locked, msg)

  final case class FailedDependency(msg: String = "Failed Dependency") extends HttpError(Status.FailedDependency, msg)

  final case class UnorderedCollection(msg: String = "Unordered Collection")
      extends HttpError(Status.UnorderedCollection, msg)

  final case class UpgradeRequired(msg: String = "Upgrade Required") extends HttpError(Status.UpgradeRequired, msg)

  final case class PreconditionRequired(msg: String = "Precondition Required")
      extends HttpError(Status.PreconditionRequired, msg)

  final case class TooManyRequests(msg: String = "Too Many Requests") extends HttpError(Status.TooManyRequests, msg)

  final case class RequestHeaderFieldsTooLarge(msg: String = "Request Header Fields Too Large")
      extends HttpError(Status.RequestHeaderFieldsTooLarge, msg)

  final case class GatewayTimeout(msg: String = "Gateway Timeout") extends HttpError(Status.GatewayTimeout, msg)

  final case class VariantAlsoNegotiates(msg: String = "Variant Also Negotiates")
      extends HttpError(Status.VariantAlsoNegotiates, msg)

  final case class InsufficientStorage(msg: String = "Insufficient Storage")
      extends HttpError(Status.InsufficientStorage, msg)

  final case class NotExtended(msg: String = "Not Extended") extends HttpError(Status.NotExtended, msg)

  final case class NetworkAuthenticationRequired(msg: String = "Network Authentication Required")
      extends HttpError(Status.NetworkAuthenticationRequired, msg)

  final case class InternalServerError(msg: String = "Internal Server Error", cause: Option[Throwable] = None)
      extends HTTPErrorWithCause(Status.InternalServerError, msg)

  final case class NotImplemented(msg: String = "Not Implemented") extends HttpError(Status.NotImplemented, msg)

  final case class HttpVersionNotSupported(msg: String = "HTTP Version Not Supported")
      extends HttpError(Status.HttpVersionNotSupported, msg)

  final case class ServiceUnavailable(msg: String = "Service Unavailable")
      extends HttpError(Status.ServiceUnavailable, msg)

  final case class BadGateway(msg: String = "Bad Gateway") extends HttpError(Status.BadGateway, msg)

  final case class Custom(code: Int, reason: String) extends HttpError(Status.Custom(code), reason)

}
