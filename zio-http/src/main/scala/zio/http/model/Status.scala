package zio.http.model

import io.netty.handler.codec.http.HttpResponseStatus
import zio.http._
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

sealed trait Status extends Product with Serializable { self =>

  def isInformational: Boolean = code >= 100 && code < 200
  def isSuccess: Boolean       = code >= 200 && code < 300
  def isRedirection: Boolean   = code >= 300 && code < 400
  def isClientError: Boolean   = code >= 400 && code < 500
  def isServerError: Boolean   = code >= 500 && code < 600
  def isError: Boolean         = isClientError | isServerError

  /**
   * Returns self as io.netty.handler.codec.http.HttpResponseStatus.
   */
  def asJava: HttpResponseStatus = self match {
    case Status.Continue                      => HttpResponseStatus.CONTINUE                        // 100
    case Status.SwitchingProtocols            => HttpResponseStatus.SWITCHING_PROTOCOLS             // 101
    case Status.Processing                    => HttpResponseStatus.PROCESSING                      // 102
    case Status.Ok                            => HttpResponseStatus.OK                              // 200
    case Status.Created                       => HttpResponseStatus.CREATED                         // 201
    case Status.Accepted                      => HttpResponseStatus.ACCEPTED                        // 202
    case Status.NonAuthoritativeInformation   => HttpResponseStatus.NON_AUTHORITATIVE_INFORMATION   // 203
    case Status.NoContent                     => HttpResponseStatus.NO_CONTENT                      // 204
    case Status.ResetContent                  => HttpResponseStatus.RESET_CONTENT                   // 205
    case Status.PartialContent                => HttpResponseStatus.PARTIAL_CONTENT                 // 206
    case Status.MultiStatus                   => HttpResponseStatus.MULTI_STATUS                    // 207
    case Status.MultipleChoices               => HttpResponseStatus.MULTIPLE_CHOICES                // 300
    case Status.MovedPermanently              => HttpResponseStatus.MOVED_PERMANENTLY               // 301
    case Status.Found                         => HttpResponseStatus.FOUND                           // 302
    case Status.SeeOther                      => HttpResponseStatus.SEE_OTHER                       // 303
    case Status.NotModified                   => HttpResponseStatus.NOT_MODIFIED                    // 304
    case Status.UseProxy                      => HttpResponseStatus.USE_PROXY                       // 305
    case Status.TemporaryRedirect             => HttpResponseStatus.TEMPORARY_REDIRECT              // 307
    case Status.PermanentRedirect             => HttpResponseStatus.PERMANENT_REDIRECT              // 308
    case Status.BadRequest                    => HttpResponseStatus.BAD_REQUEST                     // 400
    case Status.Unauthorized                  => HttpResponseStatus.UNAUTHORIZED                    // 401
    case Status.PaymentRequired               => HttpResponseStatus.PAYMENT_REQUIRED                // 402
    case Status.Forbidden                     => HttpResponseStatus.FORBIDDEN                       // 403
    case Status.NotFound                      => HttpResponseStatus.NOT_FOUND                       // 404
    case Status.MethodNotAllowed              => HttpResponseStatus.METHOD_NOT_ALLOWED              // 405
    case Status.NotAcceptable                 => HttpResponseStatus.NOT_ACCEPTABLE                  // 406
    case Status.ProxyAuthenticationRequired   => HttpResponseStatus.PROXY_AUTHENTICATION_REQUIRED   // 407
    case Status.RequestTimeout                => HttpResponseStatus.REQUEST_TIMEOUT                 // 408
    case Status.Conflict                      => HttpResponseStatus.CONFLICT                        // 409
    case Status.Gone                          => HttpResponseStatus.GONE                            // 410
    case Status.LengthRequired                => HttpResponseStatus.LENGTH_REQUIRED                 // 411
    case Status.PreconditionFailed            => HttpResponseStatus.PRECONDITION_FAILED             // 412
    case Status.RequestEntityTooLarge         => HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE        // 413
    case Status.RequestUriTooLong             => HttpResponseStatus.REQUEST_URI_TOO_LONG            // 414
    case Status.UnsupportedMediaType          => HttpResponseStatus.UNSUPPORTED_MEDIA_TYPE          // 415
    case Status.RequestedRangeNotSatisfiable  => HttpResponseStatus.REQUESTED_RANGE_NOT_SATISFIABLE // 416
    case Status.ExpectationFailed             => HttpResponseStatus.EXPECTATION_FAILED              // 417
    case Status.MisdirectedRequest            => HttpResponseStatus.MISDIRECTED_REQUEST             // 421
    case Status.UnprocessableEntity           => HttpResponseStatus.UNPROCESSABLE_ENTITY            // 422
    case Status.Locked                        => HttpResponseStatus.LOCKED                          // 423
    case Status.FailedDependency              => HttpResponseStatus.FAILED_DEPENDENCY               // 424
    case Status.UnorderedCollection           => HttpResponseStatus.UNORDERED_COLLECTION            // 425
    case Status.UpgradeRequired               => HttpResponseStatus.UPGRADE_REQUIRED                // 426
    case Status.PreconditionRequired          => HttpResponseStatus.PRECONDITION_REQUIRED           // 428
    case Status.TooManyRequests               => HttpResponseStatus.TOO_MANY_REQUESTS               // 429
    case Status.RequestHeaderFieldsTooLarge   => HttpResponseStatus.REQUEST_HEADER_FIELDS_TOO_LARGE // 431
    case Status.InternalServerError           => HttpResponseStatus.INTERNAL_SERVER_ERROR           // 500
    case Status.NotImplemented                => HttpResponseStatus.NOT_IMPLEMENTED                 // 501
    case Status.BadGateway                    => HttpResponseStatus.BAD_GATEWAY                     // 502
    case Status.ServiceUnavailable            => HttpResponseStatus.SERVICE_UNAVAILABLE             // 503
    case Status.GatewayTimeout                => HttpResponseStatus.GATEWAY_TIMEOUT                 // 504
    case Status.HttpVersionNotSupported       => HttpResponseStatus.HTTP_VERSION_NOT_SUPPORTED      // 505
    case Status.VariantAlsoNegotiates         => HttpResponseStatus.VARIANT_ALSO_NEGOTIATES         // 506
    case Status.InsufficientStorage           => HttpResponseStatus.INSUFFICIENT_STORAGE            // 507
    case Status.NotExtended                   => HttpResponseStatus.NOT_EXTENDED                    // 510
    case Status.NetworkAuthenticationRequired => HttpResponseStatus.NETWORK_AUTHENTICATION_REQUIRED // 511
    case Status.Custom(code)                  => HttpResponseStatus.valueOf(code)
  }

  /**
   * Returns the status code.
   */
  def code: Int = self.asJava.code()

  /**
   * Returns an HttpApp[Any, Nothing] that responses with this http status code.
   */
  def toApp: UHttpApp = Http.status(self)

  /**
   * Returns a Response with empty data and no headers.
   */
  def toResponse: Response = Response.status(self)
}

object Status {
  case object Continue                            extends Status
  case object SwitchingProtocols                  extends Status
  case object Processing                          extends Status
  case object Ok                                  extends Status
  case object Created                             extends Status
  case object Accepted                            extends Status
  case object NonAuthoritativeInformation         extends Status
  case object NoContent                           extends Status
  case object ResetContent                        extends Status
  case object PartialContent                      extends Status
  case object MultiStatus                         extends Status
  case object MultipleChoices                     extends Status
  case object MovedPermanently                    extends Status
  case object Found                               extends Status
  case object SeeOther                            extends Status
  case object NotModified                         extends Status
  case object UseProxy                            extends Status
  case object TemporaryRedirect                   extends Status
  case object PermanentRedirect                   extends Status
  case object BadRequest                          extends Status
  case object Unauthorized                        extends Status
  case object PaymentRequired                     extends Status
  case object Forbidden                           extends Status
  case object NotFound                            extends Status
  case object MethodNotAllowed                    extends Status
  case object NotAcceptable                       extends Status
  case object ProxyAuthenticationRequired         extends Status
  case object RequestTimeout                      extends Status
  case object Conflict                            extends Status
  case object Gone                                extends Status
  case object LengthRequired                      extends Status
  case object PreconditionFailed                  extends Status
  case object RequestEntityTooLarge               extends Status
  case object RequestUriTooLong                   extends Status
  case object UnsupportedMediaType                extends Status
  case object RequestedRangeNotSatisfiable        extends Status
  case object ExpectationFailed                   extends Status
  case object MisdirectedRequest                  extends Status
  case object UnprocessableEntity                 extends Status
  case object Locked                              extends Status
  case object FailedDependency                    extends Status
  case object UnorderedCollection                 extends Status
  case object UpgradeRequired                     extends Status
  case object PreconditionRequired                extends Status
  case object TooManyRequests                     extends Status
  case object RequestHeaderFieldsTooLarge         extends Status
  case object InternalServerError                 extends Status
  case object NotImplemented                      extends Status
  case object BadGateway                          extends Status
  case object ServiceUnavailable                  extends Status
  case object GatewayTimeout                      extends Status
  case object HttpVersionNotSupported             extends Status
  case object VariantAlsoNegotiates               extends Status
  case object InsufficientStorage                 extends Status
  case object NotExtended                         extends Status
  case object NetworkAuthenticationRequired       extends Status
  final case class Custom(override val code: Int) extends Status

  def fromHttpResponseStatus(jStatus: HttpResponseStatus): Status = (jStatus: @unchecked) match {
    case HttpResponseStatus.CONTINUE                        => Status.Continue
    case HttpResponseStatus.SWITCHING_PROTOCOLS             => Status.SwitchingProtocols
    case HttpResponseStatus.PROCESSING                      => Status.Processing
    case HttpResponseStatus.OK                              => Status.Ok
    case HttpResponseStatus.CREATED                         => Status.Created
    case HttpResponseStatus.ACCEPTED                        => Status.Accepted
    case HttpResponseStatus.NON_AUTHORITATIVE_INFORMATION   => Status.NonAuthoritativeInformation
    case HttpResponseStatus.NO_CONTENT                      => Status.NoContent
    case HttpResponseStatus.RESET_CONTENT                   => Status.ResetContent
    case HttpResponseStatus.PARTIAL_CONTENT                 => Status.PartialContent
    case HttpResponseStatus.MULTI_STATUS                    => Status.MultiStatus
    case HttpResponseStatus.MULTIPLE_CHOICES                => Status.MultipleChoices
    case HttpResponseStatus.MOVED_PERMANENTLY               => Status.MovedPermanently
    case HttpResponseStatus.FOUND                           => Status.Found
    case HttpResponseStatus.SEE_OTHER                       => Status.SeeOther
    case HttpResponseStatus.NOT_MODIFIED                    => Status.NotModified
    case HttpResponseStatus.USE_PROXY                       => Status.UseProxy
    case HttpResponseStatus.TEMPORARY_REDIRECT              => Status.TemporaryRedirect
    case HttpResponseStatus.PERMANENT_REDIRECT              => Status.PermanentRedirect
    case HttpResponseStatus.BAD_REQUEST                     => Status.BadRequest
    case HttpResponseStatus.UNAUTHORIZED                    => Status.Unauthorized
    case HttpResponseStatus.PAYMENT_REQUIRED                => Status.PaymentRequired
    case HttpResponseStatus.FORBIDDEN                       => Status.Forbidden
    case HttpResponseStatus.NOT_FOUND                       => Status.NotFound
    case HttpResponseStatus.METHOD_NOT_ALLOWED              => Status.MethodNotAllowed
    case HttpResponseStatus.NOT_ACCEPTABLE                  => Status.NotAcceptable
    case HttpResponseStatus.PROXY_AUTHENTICATION_REQUIRED   => Status.ProxyAuthenticationRequired
    case HttpResponseStatus.REQUEST_TIMEOUT                 => Status.RequestTimeout
    case HttpResponseStatus.CONFLICT                        => Status.Conflict
    case HttpResponseStatus.GONE                            => Status.Gone
    case HttpResponseStatus.LENGTH_REQUIRED                 => Status.LengthRequired
    case HttpResponseStatus.PRECONDITION_FAILED             => Status.PreconditionFailed
    case HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE        => Status.RequestEntityTooLarge
    case HttpResponseStatus.REQUEST_URI_TOO_LONG            => Status.RequestUriTooLong
    case HttpResponseStatus.UNSUPPORTED_MEDIA_TYPE          => Status.UnsupportedMediaType
    case HttpResponseStatus.REQUESTED_RANGE_NOT_SATISFIABLE => Status.RequestedRangeNotSatisfiable
    case HttpResponseStatus.EXPECTATION_FAILED              => Status.ExpectationFailed
    case HttpResponseStatus.MISDIRECTED_REQUEST             => Status.MisdirectedRequest
    case HttpResponseStatus.UNPROCESSABLE_ENTITY            => Status.UnprocessableEntity
    case HttpResponseStatus.LOCKED                          => Status.Locked
    case HttpResponseStatus.FAILED_DEPENDENCY               => Status.FailedDependency
    case HttpResponseStatus.UNORDERED_COLLECTION            => Status.UnorderedCollection
    case HttpResponseStatus.UPGRADE_REQUIRED                => Status.UpgradeRequired
    case HttpResponseStatus.PRECONDITION_REQUIRED           => Status.PreconditionRequired
    case HttpResponseStatus.TOO_MANY_REQUESTS               => Status.TooManyRequests
    case HttpResponseStatus.REQUEST_HEADER_FIELDS_TOO_LARGE => Status.RequestHeaderFieldsTooLarge
    case HttpResponseStatus.INTERNAL_SERVER_ERROR           => Status.InternalServerError
    case HttpResponseStatus.NOT_IMPLEMENTED                 => Status.NotImplemented
    case HttpResponseStatus.BAD_GATEWAY                     => Status.BadGateway
    case HttpResponseStatus.SERVICE_UNAVAILABLE             => Status.ServiceUnavailable
    case HttpResponseStatus.GATEWAY_TIMEOUT                 => Status.GatewayTimeout
    case HttpResponseStatus.HTTP_VERSION_NOT_SUPPORTED      => Status.HttpVersionNotSupported
    case HttpResponseStatus.VARIANT_ALSO_NEGOTIATES         => Status.VariantAlsoNegotiates
    case HttpResponseStatus.INSUFFICIENT_STORAGE            => Status.InsufficientStorage
    case HttpResponseStatus.NOT_EXTENDED                    => Status.NotExtended
    case HttpResponseStatus.NETWORK_AUTHENTICATION_REQUIRED => Status.NetworkAuthenticationRequired
    case status                                             => Status.Custom(status.code)
  }
}
