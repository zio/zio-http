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

import zio.Trace

import zio.http._

sealed trait Status extends Product with Serializable { self =>

  def isInformational: Boolean = code >= 100 && code < 200
  def isSuccess: Boolean       = code >= 200 && code < 300
  def isRedirection: Boolean   = code >= 300 && code < 400
  def isClientError: Boolean   = code >= 400 && code < 500
  def isServerError: Boolean   = code >= 500 && code < 600
  def isError: Boolean         = isClientError | isServerError

  /**
   * Returns the status code.
   */
  val code: Int

  lazy val text: String = code.toString

  /**
   * Returns an HttpApp[Any, Nothing] that responses with this http status code.
   */
  def toApp(implicit trace: Trace): Handler[Any, Nothing, Any, Response] = Handler.status(self)

  /**
   * Returns a Response with empty data and no headers.
   */
  def toResponse: Response = Response.status(self)
}

object Status {
  case object Continue extends Status { override val code: Int = 100 }

  case object SwitchingProtocols extends Status { override val code: Int = 101 }

  case object Processing extends Status { override val code: Int = 102 }

  case object Ok extends Status { override val code: Int = 200 }

  case object Created extends Status { override val code: Int = 201 }

  case object Accepted extends Status { override val code: Int = 202 }

  case object NonAuthoritativeInformation extends Status { override val code: Int = 203 }

  case object NoContent extends Status { override val code: Int = 204 }

  case object ResetContent extends Status { override val code: Int = 205 }

  case object PartialContent extends Status { override val code: Int = 206 }

  case object MultiStatus extends Status { override val code: Int = 207 }

  case object MultipleChoices extends Status { override val code: Int = 300 }

  case object MovedPermanently extends Status { override val code: Int = 301 }

  case object Found extends Status { override val code: Int = 302 }

  case object SeeOther extends Status { override val code: Int = 303 }

  case object NotModified extends Status { override val code: Int = 304 }

  case object UseProxy extends Status { override val code: Int = 305 }

  case object TemporaryRedirect extends Status { override val code: Int = 307 }

  case object PermanentRedirect extends Status { override val code: Int = 308 }

  case object BadRequest extends Status { override val code: Int = 400 }

  case object Unauthorized extends Status { override val code: Int = 401 }

  case object PaymentRequired extends Status { override val code: Int = 402 }

  case object Forbidden extends Status { override val code: Int = 403 }

  case object NotFound extends Status { override val code: Int = 404 }

  case object MethodNotAllowed extends Status { override val code: Int = 405 }

  case object NotAcceptable extends Status { override val code: Int = 406 }

  case object ProxyAuthenticationRequired extends Status { override val code: Int = 407 }

  case object RequestTimeout extends Status { override val code: Int = 408 }

  case object Conflict extends Status { override val code: Int = 409 }

  case object Gone extends Status { override val code: Int = 410 }

  case object LengthRequired extends Status { override val code: Int = 411 }

  case object PreconditionFailed extends Status { override val code: Int = 412 }

  case object RequestEntityTooLarge extends Status { override val code: Int = 413 }

  case object RequestUriTooLong extends Status { override val code: Int = 414 }

  case object UnsupportedMediaType extends Status { override val code: Int = 415 }

  case object RequestedRangeNotSatisfiable extends Status { override val code: Int = 416 }

  case object ExpectationFailed extends Status { override val code: Int = 417 }

  case object MisdirectedRequest extends Status { override val code: Int = 421 }

  case object UnprocessableEntity extends Status { override val code: Int = 422 }

  case object Locked extends Status { override val code: Int = 423 }

  case object FailedDependency extends Status { override val code: Int = 424 }

  case object UnorderedCollection extends Status { override val code: Int = 425 }

  case object UpgradeRequired extends Status { override val code: Int = 426 }

  case object PreconditionRequired extends Status { override val code: Int = 428 }

  case object TooManyRequests extends Status { override val code: Int = 429 }

  case object RequestHeaderFieldsTooLarge extends Status { override val code: Int = 431 }

  case object InternalServerError extends Status { override val code: Int = 500 }

  case object NotImplemented extends Status { override val code: Int = 501 }

  case object BadGateway extends Status { override val code: Int = 502 }

  case object ServiceUnavailable extends Status { override val code: Int = 503 }

  case object GatewayTimeout extends Status { override val code: Int = 504 }

  case object HttpVersionNotSupported extends Status { override val code: Int = 505 }

  case object VariantAlsoNegotiates extends Status { override val code: Int = 506 }

  case object InsufficientStorage extends Status { override val code: Int = 507 }

  case object NotExtended extends Status { override val code: Int = 510 }

  case object NetworkAuthenticationRequired extends Status { override val code: Int = 511 }

  final case class Custom(override val code: Int) extends Status

  def fromInt(code: Int): Option[Status] = {

    if (code < 100 || code > 599) {
      None
    } else {

      val status = code match {
        case 100 => Status.Continue
        case 101 => Status.SwitchingProtocols
        case 102 => Status.Processing
        case 200 => Status.Ok
        case 201 => Status.Created
        case 202 => Status.Accepted
        case 203 => Status.NonAuthoritativeInformation
        case 204 => Status.NoContent
        case 205 => Status.ResetContent
        case 206 => Status.PartialContent
        case 207 => Status.MultiStatus
        case 300 => Status.MultipleChoices
        case 301 => Status.MovedPermanently
        case 302 => Status.Found
        case 303 => Status.SeeOther
        case 304 => Status.NotModified
        case 305 => Status.UseProxy
        case 307 => Status.TemporaryRedirect
        case 308 => Status.PermanentRedirect
        case 400 => Status.BadRequest
        case 401 => Status.Unauthorized
        case 402 => Status.PaymentRequired
        case 403 => Status.Forbidden
        case 404 => Status.NotFound
        case 405 => Status.MethodNotAllowed
        case 406 => Status.NotAcceptable
        case 407 => Status.ProxyAuthenticationRequired
        case 408 => Status.RequestTimeout
        case 409 => Status.Conflict
        case 410 => Status.Gone
        case 411 => Status.LengthRequired
        case 412 => Status.PreconditionFailed
        case 413 => Status.RequestEntityTooLarge
        case 414 => Status.RequestUriTooLong
        case 415 => Status.UnsupportedMediaType
        case 416 => Status.RequestedRangeNotSatisfiable
        case 417 => Status.ExpectationFailed
        case 421 => Status.MisdirectedRequest
        case 422 => Status.UnprocessableEntity
        case 423 => Status.Locked
        case 424 => Status.FailedDependency
        case 425 => Status.UnorderedCollection
        case 426 => Status.UpgradeRequired
        case 428 => Status.PreconditionRequired
        case 429 => Status.TooManyRequests
        case 431 => Status.RequestHeaderFieldsTooLarge
        case 500 => Status.InternalServerError
        case 501 => Status.NotImplemented
        case 502 => Status.BadGateway
        case 503 => Status.ServiceUnavailable
        case 504 => Status.GatewayTimeout
        case 505 => Status.HttpVersionNotSupported
        case 506 => Status.VariantAlsoNegotiates
        case 507 => Status.InsufficientStorage
        case 510 => Status.NotExtended
        case 511 => Status.NetworkAuthenticationRequired
        case _   => Status.Custom(code)

      }
      Some(status)
    }
  }
}
