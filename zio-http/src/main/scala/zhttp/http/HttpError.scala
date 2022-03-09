package zhttp.http

import zhttp.http.HttpError.HTTPErrorWithCause

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

  abstract class HTTPErrorWithCause(status: Status, msg: String) extends HttpError(status, msg) {
    def cause: Option[Throwable]
    cause.foreach(initCause)
  }

  final case class BadRequest(msg: String = "Bad Request") extends HttpError(Status.Bad_Request, msg)

  final case class Unauthorized(msg: String = "Unauthorized") extends HttpError(Status.Unauthorized, msg)

  final case class PaymentRequired(msg: String = "Payment Required") extends HttpError(Status.Payment_Required, msg)

  final case class Forbidden(msg: String = "Forbidden") extends HttpError(Status.Forbidden, msg)

  final case class NotFound(path: Path)
      extends HttpError(Status.Not_Found, s"""The requested URI "${path.encode}" was not found on this server\n""")

  final case class MethodNotAllowed(msg: String = "Method Not Allowed")
      extends HttpError(Status.Method_Not_Allowed, msg)

  final case class NotAcceptable(msg: String = "Not Acceptable") extends HttpError(Status.Not_Acceptable, msg)

  final case class ProxyAuthenticationRequired(msg: String = "Proxy Authentication Required")
      extends HttpError(Status.Proxy_Authentication_Required, msg)

  final case class Conflict(msg: String = "Conflict") extends HttpError(Status.Conflict, msg)

  final case class Gone(msg: String = "Gone") extends HttpError(Status.Gone, msg)

  final case class LengthRequired(msg: String = "Length Required") extends HttpError(Status.Length_Required, msg)

  final case class PreconditionFailed(msg: String = "Precondition Failed")
      extends HttpError(Status.Precondition_Failed, msg)

  final case class RequestTimeout(msg: String = "Request Timeout") extends HttpError(Status.Request_Timeout, msg)

  final case class RequestEntityTooLarge(msg: String = "Request Entity Too Large")
      extends HttpError(Status.Request_Entity_Too_Large, msg)

  final case class RequestUriTooLong(msg: String = "Request-URI Too Long")
      extends HttpError(Status.Request_Uri_Too_Long, msg)

  final case class UnsupportedMediaType(msg: String = "Unsupported Media Type")
      extends HttpError(Status.Unsupported_Media_Type, msg)

  final case class RequestedRangeNotSatisfiable(msg: String = "Requested Range Not Satisfiable")
      extends HttpError(Status.Requested_Range_Not_Satisfiable, msg)

  final case class ExpectationFailed(msg: String = "Expectation Failed")
      extends HttpError(Status.Expectation_Failed, msg)

  final case class MisdirectedRequest(msg: String = "Misdirected Request")
      extends HttpError(Status.Misdirected_Request, msg)

  final case class UnprocessableEntity(msg: String = "Unprocessable Entity")
      extends HttpError(Status.Unprocessable_Entity, msg)

  final case class Locked(msg: String = "Locked") extends HttpError(Status.Locked, msg)

  final case class FailedDependency(msg: String = "Failed Dependency") extends HttpError(Status.Failed_Dependency, msg)

  final case class UnorderedCollection(msg: String = "Unordered Collection")
      extends HttpError(Status.Unordered_Collection, msg)

  final case class UpgradeRequired(msg: String = "Upgrade Required") extends HttpError(Status.Upgrade_Required, msg)

  final case class PreconditionRequired(msg: String = "Precondition Required")
      extends HttpError(Status.Precondition_Required, msg)

  final case class TooManyRequests(msg: String = "Too Many Requests") extends HttpError(Status.Too_Many_Requests, msg)

  final case class RequestHeaderFieldsTooLarge(msg: String = "Request Header Fields Too Large")
      extends HttpError(Status.Request_Header_Fields_Too_Large, msg)

  final case class GatewayTimeout(msg: String = "Gateway Timeout") extends HttpError(Status.Gateway_Timeout, msg)

  final case class VariantAlsoNegotiates(msg: String = "Variant Also Negotiates")
      extends HttpError(Status.Variant_Also_Negotiates, msg)

  final case class InsufficientStorage(msg: String = "Insufficient Storage")
      extends HttpError(Status.Insufficient_Storage, msg)

  final case class NotExtended(msg: String = "Not Extended") extends HttpError(Status.Not_Extended, msg)

  final case class NetworkAuthenticationRequired(msg: String = "Network Authentication Required")
      extends HttpError(Status.Network_Authentication_Required, msg)

  final case class InternalServerError(msg: String = "Internal Server Error", cause: Option[Throwable] = None)
      extends HTTPErrorWithCause(Status.Internal_Server_Error, msg)

  final case class NotImplemented(msg: String = "Not Implemented") extends HttpError(Status.Not_Implemented, msg)

  final case class HttpVersionNotSupported(msg: String = "HTTP Version Not Supported")
      extends HttpError(Status.Http_Version_Not_Supported, msg)

  final case class ServiceUnavailable(msg: String = "Service Unavailable")
      extends HttpError(Status.Service_Unavailable, msg)

  final case class BadGateway(msg: String = "Bad Gateway") extends HttpError(Status.Bad_Gateway, msg)

  final case class CustomResponseStatus(code: Int, reason: String) extends HttpError(Status.Custom(code), reason)

}
