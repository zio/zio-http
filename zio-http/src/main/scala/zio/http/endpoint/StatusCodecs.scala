package zio.http.endpoint

import zio.http.endpoint.internal.TextCodec
import zio.http.model.Status

private[endpoint] trait StatusCodecs {
  val Continue: StatusCodec[Unit]           = HttpCodec.Status(TextCodec.constant(Status.Continue.text))
  val SwitchingProtocols: StatusCodec[Unit] = HttpCodec.Status(TextCodec.constant(Status.SwitchingProtocols.text))
  val Processing: StatusCodec[Unit]         = HttpCodec.Status(TextCodec.constant(Status.Processing.text))
  val Ok: StatusCodec[Unit]                 = HttpCodec.Status(TextCodec.constant(Status.Ok.text))
  val Created: StatusCodec[Unit]            = HttpCodec.Status(TextCodec.constant(Status.Created.text))
  val Accepted: StatusCodec[Unit]           = HttpCodec.Status(TextCodec.constant(Status.Accepted.text))
  val NonAuthoritativeInformation: StatusCodec[Unit] =
    HttpCodec.Status(TextCodec.constant(Status.NonAuthoritativeInformation.text))
  val NoContent: StatusCodec[Unit]                   = HttpCodec.Status(TextCodec.constant(Status.NoContent.text))
  val ResetContent: StatusCodec[Unit]                = HttpCodec.Status(TextCodec.constant(Status.ResetContent.text))
  val PartialContent: StatusCodec[Unit]              = HttpCodec.Status(TextCodec.constant(Status.PartialContent.text))
  val MultiStatus: StatusCodec[Unit]                 = HttpCodec.Status(TextCodec.constant(Status.MultiStatus.text))
  val MultipleChoices: StatusCodec[Unit]             = HttpCodec.Status(TextCodec.constant(Status.MultipleChoices.text))
  val MovedPermanently: StatusCodec[Unit]  = HttpCodec.Status(TextCodec.constant(Status.MovedPermanently.text))
  val Found: StatusCodec[Unit]             = HttpCodec.Status(TextCodec.constant(Status.Found.text))
  val SeeOther: StatusCodec[Unit]          = HttpCodec.Status(TextCodec.constant(Status.SeeOther.text))
  val NotModified: StatusCodec[Unit]       = HttpCodec.Status(TextCodec.constant(Status.NotModified.text))
  val UseProxy: StatusCodec[Unit]          = HttpCodec.Status(TextCodec.constant(Status.UseProxy.text))
  val TemporaryRedirect: StatusCodec[Unit] = HttpCodec.Status(TextCodec.constant(Status.TemporaryRedirect.text))
  val PermanentRedirect: StatusCodec[Unit] = HttpCodec.Status(TextCodec.constant(Status.PermanentRedirect.text))
  val BadRequest: StatusCodec[Unit]        = HttpCodec.Status(TextCodec.constant(Status.BadRequest.text))
  val Unauthorized: StatusCodec[Unit]      = HttpCodec.Status(TextCodec.constant(Status.Unauthorized.text))
  val PaymentRequired: StatusCodec[Unit]   = HttpCodec.Status(TextCodec.constant(Status.PaymentRequired.text))
  val Forbidden: StatusCodec[Unit]         = HttpCodec.Status(TextCodec.constant(Status.Forbidden.text))
  val NotFound: StatusCodec[Unit]          = HttpCodec.Status(TextCodec.constant(Status.NotFound.text))
  val MethodNotAllowed: StatusCodec[Unit]  = HttpCodec.Status(TextCodec.constant(Status.MethodNotAllowed.text))
  val NotAcceptable: StatusCodec[Unit]     = HttpCodec.Status(TextCodec.constant(Status.NotAcceptable.text))
  val ProxyAuthenticationRequired: StatusCodec[Unit] =
    HttpCodec.Status(TextCodec.constant(Status.ProxyAuthenticationRequired.text))
  val RequestTimeout: StatusCodec[Unit]              = HttpCodec.Status(TextCodec.constant(Status.RequestTimeout.text))
  val Conflict: StatusCodec[Unit]                    = HttpCodec.Status(TextCodec.constant(Status.Conflict.text))
  val Gone: StatusCodec[Unit]                        = HttpCodec.Status(TextCodec.constant(Status.Gone.text))
  val LengthRequired: StatusCodec[Unit]              = HttpCodec.Status(TextCodec.constant(Status.LengthRequired.text))
  val PreconditionFailed: StatusCodec[Unit]    = HttpCodec.Status(TextCodec.constant(Status.PreconditionFailed.text))
  val RequestEntityTooLarge: StatusCodec[Unit] = HttpCodec.Status(TextCodec.constant(Status.RequestEntityTooLarge.text))
  val RequestUriTooLong: StatusCodec[Unit]     = HttpCodec.Status(TextCodec.constant(Status.RequestUriTooLong.text))
  val UnsupportedMediaType: StatusCodec[Unit]  = HttpCodec.Status(TextCodec.constant(Status.UnsupportedMediaType.text))
  val RequestedRangeNotSatisfiable: StatusCodec[Unit] =
    HttpCodec.Status(TextCodec.constant(Status.RequestedRangeNotSatisfiable.text))
  val ExpectationFailed: StatusCodec[Unit]    = HttpCodec.Status(TextCodec.constant(Status.ExpectationFailed.text))
  val MisdirectedRequest: StatusCodec[Unit]   = HttpCodec.Status(TextCodec.constant(Status.MisdirectedRequest.text))
  val UnprocessableEntity: StatusCodec[Unit]  = HttpCodec.Status(TextCodec.constant(Status.UnprocessableEntity.text))
  val Locked: StatusCodec[Unit]               = HttpCodec.Status(TextCodec.constant(Status.Locked.text))
  val FailedDependency: StatusCodec[Unit]     = HttpCodec.Status(TextCodec.constant(Status.FailedDependency.text))
  val UnorderedCollection: StatusCodec[Unit]  = HttpCodec.Status(TextCodec.constant(Status.UnorderedCollection.text))
  val UpgradeRequired: StatusCodec[Unit]      = HttpCodec.Status(TextCodec.constant(Status.UpgradeRequired.text))
  val PreconditionRequired: StatusCodec[Unit] = HttpCodec.Status(TextCodec.constant(Status.PreconditionRequired.text))
  val TooManyRequests: StatusCodec[Unit]      = HttpCodec.Status(TextCodec.constant(Status.TooManyRequests.text))
  val RequestHeaderFieldsTooLarge: StatusCodec[Unit] =
    HttpCodec.Status(TextCodec.constant(Status.RequestHeaderFieldsTooLarge.text))
  val InternalServerError: StatusCodec[Unit]     = HttpCodec.Status(TextCodec.constant(Status.InternalServerError.text))
  val NotImplemented: StatusCodec[Unit]          = HttpCodec.Status(TextCodec.constant(Status.NotImplemented.text))
  val BadGateway: StatusCodec[Unit]              = HttpCodec.Status(TextCodec.constant(Status.BadGateway.text))
  val ServiceUnavailable: StatusCodec[Unit]      = HttpCodec.Status(TextCodec.constant(Status.ServiceUnavailable.text))
  val GatewayTimeout: StatusCodec[Unit]          = HttpCodec.Status(TextCodec.constant(Status.GatewayTimeout.text))
  val HttpVersionNotSupported: StatusCodec[Unit] =
    HttpCodec.Status(TextCodec.constant(Status.HttpVersionNotSupported.text))
  val VariantAlsoNegotiates: StatusCodec[Unit] = HttpCodec.Status(TextCodec.constant(Status.VariantAlsoNegotiates.text))
  val InsufficientStorage: StatusCodec[Unit]   = HttpCodec.Status(TextCodec.constant(Status.InsufficientStorage.text))
  val NotExtended: StatusCodec[Unit]           = HttpCodec.Status(TextCodec.constant(Status.NotExtended.text))
  val NetworkAuthenticationRequired: StatusCodec[Unit] =
    HttpCodec.Status(TextCodec.constant(Status.NetworkAuthenticationRequired.text))
  val CustomStatus: StatusCodec[Int]                   = HttpCodec.Status(TextCodec.int)

  def status(status: zio.http.model.Status): StatusCodec[Unit] = HttpCodec.Status(TextCodec.constant(status.text))
}
