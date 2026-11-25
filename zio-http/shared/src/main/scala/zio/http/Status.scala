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

import scala.util.Try

import zio.Trace
import zio.stacktracer.TracingImplicits.disableAutoTrace

/**
 * An HTTP response status code with its reason phrase. The constants in the
 * companion object cover the registered codes that the library knows by name;
 * any other code is a `Status.Custom`.
 *
 * {{{
 * val status: Status = Status.fromInt(404)
 * val reason         = status.reasonPhrase // Not Found
 * val isClient       = status.isClientError // true
 * }}}
 *
 * @see
 *   [[https://zio.dev/zio-http/reference/response/ Response]]
 */
sealed trait Status extends Product with Serializable { self =>

  /** Returns `true` when the code is in the range 100 to 199. */
  def isInformational: Boolean = code >= 100 && code < 200

  /** Returns `true` when the code is in the range 200 to 299. */
  def isSuccess: Boolean = code >= 200 && code < 300

  /** Returns `true` when the code is in the range 300 to 399. */
  def isRedirection: Boolean = code >= 300 && code < 400

  /** Returns `true` when the code is in the range 400 to 499. */
  def isClientError: Boolean = code >= 400 && code < 500

  /** Returns `true` when the code is in the range 500 to 599. */
  def isServerError: Boolean = code >= 500 && code < 600

  /** Returns `true` when the code is in the range 400 to 599. */
  def isError: Boolean = isClientError | isServerError

  /**
   * Returns the status code.
   */
  val code: Int

  /**
   * The reason phrase of the status. For the predefined constants it is the
   * constant name split into words (`Status.NotFound` gives `Not Found`,
   * `Status.Ok` gives `Ok`). For `Status.Custom` it is the value given when the
   * status was created.
   */
  val reasonPhrase: String =
    self.productPrefix.flatMap { c => if (c.isUpper) s" $c" else s"$c" }.drop(1)

  /** Returns the status code as a string, for example `404`. */
  lazy val text: String = code.toString

  /**
   * Returns a `Routes[Any, Nothing]` that responds to every request with this
   * status code.
   */
  def toRoutes(implicit trace: Trace): Routes[Any, Nothing] =
    Handler.status(self).toRoutes

  /**
   * Returns a Response with empty data and no headers.
   */
  def toResponse: Response = Response.status(self)
}

/**
 * Predefined statuses and conversions from status codes.
 */
object Status {

  /** A status with a code from 100 to 199. */
  sealed trait Informational extends Status // 100 – 199
  /** A status with a code from 200 to 299. */
  sealed trait Success       extends Status // 200 – 299
  /** A status with a code from 300 to 399. */
  sealed trait Redirection   extends Status // 300 – 399
  /** A status with a code from 400 to 599. */
  sealed trait Error         extends Status // 400 – 599
  /** A status with a code from 400 to 499. */
  sealed trait ClientError   extends Error  // 400 – 499
  /** A status with a code from 500 to 599. */
  sealed trait ServerError   extends Error  // 500 – 599

  /** The 100 status code, with the reason phrase `Continue`. */
  case object Continue extends Informational { override val code: Int = 100 }

  /** The 101 status code, with the reason phrase `Switching Protocols`. */
  case object SwitchingProtocols extends Informational { override val code: Int = 101 }

  /** The 102 status code, with the reason phrase `Processing`. */
  case object Processing extends Informational { override val code: Int = 102 }

  /** The 200 status code, with the reason phrase `Ok`. */
  case object Ok extends Success { override val code: Int = 200 }

  /** The 201 status code, with the reason phrase `Created`. */
  case object Created extends Success { override val code: Int = 201 }

  /** The 202 status code, with the reason phrase `Accepted`. */
  case object Accepted extends Success { override val code: Int = 202 }

  /**
   * The 203 status code, with the reason phrase
   * `Non Authoritative Information`.
   */
  case object NonAuthoritativeInformation extends Success { override val code: Int = 203 }

  /** The 204 status code, with the reason phrase `No Content`. */
  case object NoContent extends Success { override val code: Int = 204 }

  /** The 205 status code, with the reason phrase `Reset Content`. */
  case object ResetContent extends Success { override val code: Int = 205 }

  /** The 206 status code, with the reason phrase `Partial Content`. */
  case object PartialContent extends Success { override val code: Int = 206 }

  /** The 207 status code, with the reason phrase `Multi Status`. */
  case object MultiStatus extends Success { override val code: Int = 207 }

  /** The 300 status code, with the reason phrase `Multiple Choices`. */
  case object MultipleChoices extends Redirection { override val code: Int = 300 }

  /** The 301 status code, with the reason phrase `Moved Permanently`. */
  case object MovedPermanently extends Redirection { override val code: Int = 301 }

  /** The 302 status code, with the reason phrase `Found`. */
  case object Found extends Redirection { override val code: Int = 302 }

  /** The 303 status code, with the reason phrase `See Other`. */
  case object SeeOther extends Redirection { override val code: Int = 303 }

  /** The 304 status code, with the reason phrase `Not Modified`. */
  case object NotModified extends Redirection { override val code: Int = 304 }

  /** The 305 status code, with the reason phrase `Use Proxy`. */
  case object UseProxy extends Redirection { override val code: Int = 305 }

  /** The 307 status code, with the reason phrase `Temporary Redirect`. */
  case object TemporaryRedirect extends Redirection { override val code: Int = 307 }

  /** The 308 status code, with the reason phrase `Permanent Redirect`. */
  case object PermanentRedirect extends Redirection { override val code: Int = 308 }

  /** The 400 status code, with the reason phrase `Bad Request`. */
  case object BadRequest extends ClientError { override val code: Int = 400 }

  /** The 401 status code, with the reason phrase `Unauthorized`. */
  case object Unauthorized extends ClientError { override val code: Int = 401 }

  /** The 402 status code, with the reason phrase `Payment Required`. */
  case object PaymentRequired extends ClientError { override val code: Int = 402 }

  /** The 403 status code, with the reason phrase `Forbidden`. */
  case object Forbidden extends ClientError { override val code: Int = 403 }

  /** The 404 status code, with the reason phrase `Not Found`. */
  case object NotFound extends ClientError { override val code: Int = 404 }

  /** The 405 status code, with the reason phrase `Method Not Allowed`. */
  case object MethodNotAllowed extends ClientError { override val code: Int = 405 }

  /** The 406 status code, with the reason phrase `Not Acceptable`. */
  case object NotAcceptable extends ClientError { override val code: Int = 406 }

  /**
   * The 407 status code, with the reason phrase
   * `Proxy Authentication Required`.
   */
  case object ProxyAuthenticationRequired extends ClientError { override val code: Int = 407 }

  /** The 408 status code, with the reason phrase `Request Timeout`. */
  case object RequestTimeout extends ClientError { override val code: Int = 408 }

  /** The 409 status code, with the reason phrase `Conflict`. */
  case object Conflict extends ClientError { override val code: Int = 409 }

  /** The 410 status code, with the reason phrase `Gone`. */
  case object Gone extends ClientError { override val code: Int = 410 }

  /** The 411 status code, with the reason phrase `Length Required`. */
  case object LengthRequired extends ClientError { override val code: Int = 411 }

  /** The 412 status code, with the reason phrase `Precondition Failed`. */
  case object PreconditionFailed extends ClientError { override val code: Int = 412 }

  /** The 413 status code, with the reason phrase `Request Entity Too Large`. */
  case object RequestEntityTooLarge extends ClientError { override val code: Int = 413 }

  /** The 414 status code, with the reason phrase `Request Uri Too Long`. */
  case object RequestUriTooLong extends ClientError { override val code: Int = 414 }

  /** The 415 status code, with the reason phrase `Unsupported Media Type`. */
  case object UnsupportedMediaType extends ClientError { override val code: Int = 415 }

  /**
   * The 416 status code, with the reason phrase
   * `Requested Range Not Satisfiable`.
   */
  case object RequestedRangeNotSatisfiable extends ClientError { override val code: Int = 416 }

  /** The 417 status code, with the reason phrase `Expectation Failed`. */
  case object ExpectationFailed extends ClientError { override val code: Int = 417 }

  /** The 421 status code, with the reason phrase `Misdirected Request`. */
  case object MisdirectedRequest extends ClientError { override val code: Int = 421 }

  /** The 422 status code, with the reason phrase `Unprocessable Entity`. */
  case object UnprocessableEntity extends ClientError { override val code: Int = 422 }

  /** The 423 status code, with the reason phrase `Locked`. */
  case object Locked extends ClientError { override val code: Int = 423 }

  /** The 424 status code, with the reason phrase `Failed Dependency`. */
  case object FailedDependency extends ClientError { override val code: Int = 424 }

  /** The 425 status code, with the reason phrase `Unordered Collection`. */
  case object UnorderedCollection extends ClientError { override val code: Int = 425 }

  /** The 426 status code, with the reason phrase `Upgrade Required`. */
  case object UpgradeRequired extends ClientError { override val code: Int = 426 }

  /** The 428 status code, with the reason phrase `Precondition Required`. */
  case object PreconditionRequired extends ClientError { override val code: Int = 428 }

  /** The 429 status code, with the reason phrase `Too Many Requests`. */
  case object TooManyRequests extends ClientError { override val code: Int = 429 }

  /**
   * The 431 status code, with the reason phrase
   * `Request Header Fields Too Large`.
   */
  case object RequestHeaderFieldsTooLarge extends ClientError { override val code: Int = 431 }

  /** The 500 status code, with the reason phrase `Internal Server Error`. */
  case object InternalServerError extends ServerError { override val code: Int = 500 }

  /** The 501 status code, with the reason phrase `Not Implemented`. */
  case object NotImplemented extends ServerError { override val code: Int = 501 }

  /** The 502 status code, with the reason phrase `Bad Gateway`. */
  case object BadGateway extends ServerError { override val code: Int = 502 }

  /** The 503 status code, with the reason phrase `Service Unavailable`. */
  case object ServiceUnavailable extends ServerError { override val code: Int = 503 }

  /** The 504 status code, with the reason phrase `Gateway Timeout`. */
  case object GatewayTimeout extends ServerError { override val code: Int = 504 }

  /**
   * The 505 status code, with the reason phrase `Http Version Not Supported`.
   */
  case object HttpVersionNotSupported extends ServerError { override val code: Int = 505 }

  /** The 506 status code, with the reason phrase `Variant Also Negotiates`. */
  case object VariantAlsoNegotiates extends ServerError { override val code: Int = 506 }

  /** The 507 status code, with the reason phrase `Insufficient Storage`. */
  case object InsufficientStorage extends ServerError { override val code: Int = 507 }

  /** The 510 status code, with the reason phrase `Not Extended`. */
  case object NotExtended extends ServerError { override val code: Int = 510 }

  /**
   * The 511 status code, with the reason phrase
   * `Network Authentication Required`.
   */
  case object NetworkAuthenticationRequired extends ServerError { override val code: Int = 511 }

  /**
   * A status with an arbitrary code and reason phrase, for codes that have no
   * predefined constant. The reason phrase defaults to the empty string.
   */
  final case class Custom(override val code: Int, override val reasonPhrase: String = "") extends Status

  /**
   * Parses the string as an `Int` and returns the matching status, or `None`
   * when it is not an integer. A code without a predefined constant gives a
   * `Status.Custom`.
   */
  def fromString(code: String): Option[Status] =
    Try(code.toInt).toOption.map(fromInt)

  /**
   * Returns the predefined status for the code, or `Status.Custom(code, "")`
   * when there is none (306 and 299 are examples).
   */
  def fromInt(code: Int): Status = {
    (code: @annotation.switch) match {
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
      case _   => Status.Custom(code, "")
    }
  }
}
