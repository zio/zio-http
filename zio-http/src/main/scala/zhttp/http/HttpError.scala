package zhttp.http

sealed abstract class HttpError(val status: Status, val message: String) extends Throwable(message) { self =>
  def toResponse: UResponse = Response.fromHttpError(self)
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

  final case class BadRequest(msg: String = "Bad Request") extends HttpError(Status.BAD_REQUEST, msg)

  final case class Unauthorized(msg: String = "Unauthorized") extends HttpError(Status.UNAUTHORIZED, msg)

  final case class PaymentRequired(msg: String = "Payment Required") extends HttpError(Status.PAYMENT_REQUIRED, msg)

  final case class Forbidden(msg: String = "Forbidden") extends HttpError(Status.FORBIDDEN, msg)

  final case class NotFound(path: Path)
      extends HttpError(Status.NOT_FOUND, s"""The requested URI "${path.asString}" was not found on this server\n""")

  final case class MethodNotAllowed(msg: String = "Method Not Allowed")
      extends HttpError(Status.METHOD_NOT_ALLOWED, msg)

  final case class NotAcceptable(msg: String = "Not Acceptable") extends HttpError(Status.NOT_ACCEPTABLE, msg)

  final case class ProxyAuthenticationRequired(msg: String = "Proxy Authentication Required")
      extends HttpError(Status.PROXY_AUTHENTICATION_REQUIRED, msg)

  final case class Conflict(msg: String = "Conflict") extends HttpError(Status.CONFLICT, msg)

  final case class Gone(msg: String = "Gone") extends HttpError(Status.GONE, msg)

  final case class LengthRequired(msg: String = "Length Required") extends HttpError(Status.LENGTH_REQUIRED, msg)

  final case class PreconditionFailed(msg: String = "Precondition Failed")
      extends HttpError(Status.PRECONDITION_FAILED, msg)

  final case class RequestTimeout(msg: String = "Request Timeout") extends HttpError(Status.REQUEST_TIMEOUT, msg)

  final case class RequestEntityTooLarge(msg: String = "Request Entity Too Large")
      extends HttpError(Status.REQUEST_ENTITY_TOO_LARGE, msg)

  final case class RequestUriTooLong(msg: String = "Request-URI Too Long")
      extends HttpError(Status.REQUEST_URI_TOO_LONG, msg)

  final case class UnsupportedMediaType(msg: String = "Unsupported Media Type")
      extends HttpError(Status.UNSUPPORTED_MEDIA_TYPE, msg)

  final case class RequestedRangeNotSatisfiable(msg: String = "Requested Range Not Satisfiable")
      extends HttpError(Status.REQUESTED_RANGE_NOT_SATISFIABLE, msg)

  final case class ExpectationFailed(msg: String = "Expectation Failed")
      extends HttpError(Status.EXPECTATION_FAILED, msg)

  final case class MisdirectedRequest(msg: String = "Misdirected Request")
      extends HttpError(Status.MISDIRECTED_REQUEST, msg)

  final case class UnprocessableEntity(msg: String = "Unprocessable Entity")
      extends HttpError(Status.UNPROCESSABLE_ENTITY, msg)

  final case class Locked(msg: String = "Locked") extends HttpError(Status.LOCKED, msg)

  final case class FailedDependency(msg: String = "Failed Dependency") extends HttpError(Status.FAILED_DEPENDENCY, msg)

  final case class UnorderedCollection(msg: String = "Unordered Collection")
      extends HttpError(Status.UNORDERED_COLLECTION, msg)

  final case class UpgradeRequired(msg: String = "Upgrade Required") extends HttpError(Status.UPGRADE_REQUIRED, msg)

  final case class PreconditionRequired(msg: String = "Precondition Required")
      extends HttpError(Status.PRECONDITION_REQUIRED, msg)

  final case class TooManyRequests(msg: String = "Too Many Requests") extends HttpError(Status.TOO_MANY_REQUESTS, msg)

  final case class RequestHeaderFieldsTooLarge(msg: String = "Request Header Fields Too Large")
      extends HttpError(Status.REQUEST_HEADER_FIELDS_TOO_LARGE, msg)

  final case class GatewayTimeout(msg: String = "Gateway Timeout") extends HttpError(Status.GATEWAY_TIMEOUT, msg)

  final case class VariantAlsoNegotiates(msg: String = "Variant Also Negotiates")
      extends HttpError(Status.VARIANT_ALSO_NEGOTIATES, msg)

  final case class InsufficientStorage(msg: String = "Insufficient Storage")
      extends HttpError(Status.INSUFFICIENT_STORAGE, msg)

  final case class NotExtended(msg: String = "Not Extended") extends HttpError(Status.NOT_EXTENDED, msg)

  final case class NetworkAuthenticationRequired(msg: String = "Network Authentication Required")
      extends HttpError(Status.NETWORK_AUTHENTICATION_REQUIRED, msg)

  final case class InternalServerError(msg: String = "Internal Server Error", cause: Option[Throwable] = None)
      extends HTTPErrorWithCause(Status.INTERNAL_SERVER_ERROR, msg)

  final case class NotImplemented(msg: String = "Not Implemented") extends HttpError(Status.NOT_IMPLEMENTED, msg)

  final case class HttpVersionNotSupported(msg: String = "HTTP Version Not Supported")
      extends HttpError(Status.HTTP_VERSION_NOT_SUPPORTED, msg)

  final case class ServiceUnavailable(msg: String = "Service Unavailable")
      extends HttpError(Status.SERVICE_UNAVAILABLE, msg)

  final case class BadGateway(msg: String = "Bad Gateway") extends HttpError(Status.BAD_GATEWAY, msg)

}
