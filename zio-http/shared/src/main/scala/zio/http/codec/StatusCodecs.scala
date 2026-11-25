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

package zio.http.codec

import zio.stacktracer.TracingImplicits.disableAutoTrace

import zio.http.Status

/**
 * The status codecs that `StatusCodec` and `HttpCodec` provide: one for each
 * predefined [[zio.http.Status]], and `status` and `CustomStatus` for any
 * other.
 */
private[codec] trait StatusCodecs {

  /**
   * Creates a codec for responses with the status `status`, which holds no
   * value. Decoding a response with another status gives an
   * `HttpCodecError.MalformedStatus` error.
   */
  def status(status: Status): StatusCodec[Unit] = HttpCodec.Status(SimpleCodec.Specified(status))

  /**
   * The codec for the status `Status.Continue` (100); the same as
   * `status(Status.Continue)`.
   */
  val Continue: StatusCodec[Unit] = status(Status.Continue)

  /**
   * The codec for the status `Status.SwitchingProtocols` (101); the same as
   * `status(Status.SwitchingProtocols)`.
   */
  val SwitchingProtocols: StatusCodec[Unit] = status(Status.SwitchingProtocols)

  /**
   * The codec for the status `Status.Processing` (102); the same as
   * `status(Status.Processing)`.
   */
  val Processing: StatusCodec[Unit] = status(Status.Processing)

  /**
   * The codec for the status `Status.Ok` (200); the same as
   * `status(Status.Ok)`.
   */
  val Ok: StatusCodec[Unit] = status(Status.Ok)

  /**
   * The codec for the status `Status.Created` (201); the same as
   * `status(Status.Created)`.
   */
  val Created: StatusCodec[Unit] = status(Status.Created)

  /**
   * The codec for the status `Status.Accepted` (202); the same as
   * `status(Status.Accepted)`.
   */
  val Accepted: StatusCodec[Unit] = status(Status.Accepted)

  /**
   * The codec for the status `Status.NonAuthoritativeInformation` (203); the
   * same as `status(Status.NonAuthoritativeInformation)`.
   */
  val NonAuthoritativeInformation: StatusCodec[Unit] =
    status(Status.NonAuthoritativeInformation)

  /**
   * The codec for the status `Status.NoContent` (204); the same as
   * `status(Status.NoContent)`.
   */
  val NoContent: StatusCodec[Unit] = status(Status.NoContent)

  /**
   * The codec for the status `Status.ResetContent` (205); the same as
   * `status(Status.ResetContent)`.
   */
  val ResetContent: StatusCodec[Unit] = status(Status.ResetContent)

  /**
   * The codec for the status `Status.PartialContent` (206); the same as
   * `status(Status.PartialContent)`.
   */
  val PartialContent: StatusCodec[Unit] = status(Status.PartialContent)

  /**
   * The codec for the status `Status.MultiStatus` (207); the same as
   * `status(Status.MultiStatus)`.
   */
  val MultiStatus: StatusCodec[Unit] = status(Status.MultiStatus)

  /**
   * The codec for the status `Status.MultipleChoices` (300); the same as
   * `status(Status.MultipleChoices)`.
   */
  val MultipleChoices: StatusCodec[Unit] = status(Status.MultipleChoices)

  /**
   * The codec for the status `Status.MovedPermanently` (301); the same as
   * `status(Status.MovedPermanently)`.
   */
  val MovedPermanently: StatusCodec[Unit] = status(Status.MovedPermanently)

  /**
   * The codec for the status `Status.Found` (302); the same as
   * `status(Status.Found)`.
   */
  val Found: StatusCodec[Unit] = status(Status.Found)

  /**
   * The codec for the status `Status.SeeOther` (303); the same as
   * `status(Status.SeeOther)`.
   */
  val SeeOther: StatusCodec[Unit] = status(Status.SeeOther)

  /**
   * The codec for the status `Status.NotModified` (304); the same as
   * `status(Status.NotModified)`.
   */
  val NotModified: StatusCodec[Unit] = status(Status.NotModified)

  /**
   * The codec for the status `Status.UseProxy` (305); the same as
   * `status(Status.UseProxy)`.
   */
  val UseProxy: StatusCodec[Unit] = status(Status.UseProxy)

  /**
   * The codec for the status `Status.TemporaryRedirect` (307); the same as
   * `status(Status.TemporaryRedirect)`.
   */
  val TemporaryRedirect: StatusCodec[Unit] = status(Status.TemporaryRedirect)

  /**
   * The codec for the status `Status.PermanentRedirect` (308); the same as
   * `status(Status.PermanentRedirect)`.
   */
  val PermanentRedirect: StatusCodec[Unit] = status(Status.PermanentRedirect)

  /**
   * The codec for the status `Status.BadRequest` (400); the same as
   * `status(Status.BadRequest)`.
   */
  val BadRequest: StatusCodec[Unit] = status(Status.BadRequest)

  /**
   * The codec for the status `Status.Unauthorized` (401); the same as
   * `status(Status.Unauthorized)`.
   */
  val Unauthorized: StatusCodec[Unit] = status(Status.Unauthorized)

  /**
   * The codec for the status `Status.PaymentRequired` (402); the same as
   * `status(Status.PaymentRequired)`.
   */
  val PaymentRequired: StatusCodec[Unit] = status(Status.PaymentRequired)

  /**
   * The codec for the status `Status.Forbidden` (403); the same as
   * `status(Status.Forbidden)`.
   */
  val Forbidden: StatusCodec[Unit] = status(Status.Forbidden)

  /**
   * The codec for the status `Status.NotFound` (404); the same as
   * `status(Status.NotFound)`.
   */
  val NotFound: StatusCodec[Unit] = status(Status.NotFound)

  /**
   * The codec for the status `Status.MethodNotAllowed` (405); the same as
   * `status(Status.MethodNotAllowed)`.
   */
  val MethodNotAllowed: StatusCodec[Unit] = status(Status.MethodNotAllowed)

  /**
   * The codec for the status `Status.NotAcceptable` (406); the same as
   * `status(Status.NotAcceptable)`.
   */
  val NotAcceptable: StatusCodec[Unit] = status(Status.NotAcceptable)

  /**
   * The codec for the status `Status.ProxyAuthenticationRequired` (407); the
   * same as `status(Status.ProxyAuthenticationRequired)`.
   */
  val ProxyAuthenticationRequired: StatusCodec[Unit] =
    status(Status.ProxyAuthenticationRequired)

  /**
   * The codec for the status `Status.RequestTimeout` (408); the same as
   * `status(Status.RequestTimeout)`.
   */
  val RequestTimeout: StatusCodec[Unit] = status(Status.RequestTimeout)

  /**
   * The codec for the status `Status.Conflict` (409); the same as
   * `status(Status.Conflict)`.
   */
  val Conflict: StatusCodec[Unit] = status(Status.Conflict)

  /**
   * The codec for the status `Status.Gone` (410); the same as
   * `status(Status.Gone)`.
   */
  val Gone: StatusCodec[Unit] = status(Status.Gone)

  /**
   * The codec for the status `Status.LengthRequired` (411); the same as
   * `status(Status.LengthRequired)`.
   */
  val LengthRequired: StatusCodec[Unit] = status(Status.LengthRequired)

  /**
   * The codec for the status `Status.PreconditionFailed` (412); the same as
   * `status(Status.PreconditionFailed)`.
   */
  val PreconditionFailed: StatusCodec[Unit] = status(Status.PreconditionFailed)

  /**
   * The codec for the status `Status.RequestEntityTooLarge` (413); the same as
   * `status(Status.RequestEntityTooLarge)`.
   */
  val RequestEntityTooLarge: StatusCodec[Unit] = status(Status.RequestEntityTooLarge)

  /**
   * The codec for the status `Status.RequestUriTooLong` (414); the same as
   * `status(Status.RequestUriTooLong)`.
   */
  val RequestUriTooLong: StatusCodec[Unit] = status(Status.RequestUriTooLong)

  /**
   * The codec for the status `Status.UnsupportedMediaType` (415); the same as
   * `status(Status.UnsupportedMediaType)`.
   */
  val UnsupportedMediaType: StatusCodec[Unit] = status(Status.UnsupportedMediaType)

  /**
   * The codec for the status `Status.RequestedRangeNotSatisfiable` (416); the
   * same as `status(Status.RequestedRangeNotSatisfiable)`.
   */
  val RequestedRangeNotSatisfiable: StatusCodec[Unit] =
    status(Status.RequestedRangeNotSatisfiable)

  /**
   * The codec for the status `Status.ExpectationFailed` (417); the same as
   * `status(Status.ExpectationFailed)`.
   */
  val ExpectationFailed: StatusCodec[Unit] = status(Status.ExpectationFailed)

  /**
   * The codec for the status `Status.MisdirectedRequest` (421); the same as
   * `status(Status.MisdirectedRequest)`.
   */
  val MisdirectedRequest: StatusCodec[Unit] = status(Status.MisdirectedRequest)

  /**
   * The codec for the status `Status.UnprocessableEntity` (422); the same as
   * `status(Status.UnprocessableEntity)`.
   */
  val UnprocessableEntity: StatusCodec[Unit] = status(Status.UnprocessableEntity)

  /**
   * The codec for the status `Status.Locked` (423); the same as
   * `status(Status.Locked)`.
   */
  val Locked: StatusCodec[Unit] = status(Status.Locked)

  /**
   * The codec for the status `Status.FailedDependency` (424); the same as
   * `status(Status.FailedDependency)`.
   */
  val FailedDependency: StatusCodec[Unit] = status(Status.FailedDependency)

  /**
   * The codec for the status `Status.UnorderedCollection` (425); the same as
   * `status(Status.UnorderedCollection)`.
   */
  val UnorderedCollection: StatusCodec[Unit] = status(Status.UnorderedCollection)

  /**
   * The codec for the status `Status.UpgradeRequired` (426); the same as
   * `status(Status.UpgradeRequired)`.
   */
  val UpgradeRequired: StatusCodec[Unit] = status(Status.UpgradeRequired)

  /**
   * The codec for the status `Status.PreconditionRequired` (428); the same as
   * `status(Status.PreconditionRequired)`.
   */
  val PreconditionRequired: StatusCodec[Unit] = status(Status.PreconditionRequired)

  /**
   * The codec for the status `Status.TooManyRequests` (429); the same as
   * `status(Status.TooManyRequests)`.
   */
  val TooManyRequests: StatusCodec[Unit] = status(Status.TooManyRequests)

  /**
   * The codec for the status `Status.RequestHeaderFieldsTooLarge` (431); the
   * same as `status(Status.RequestHeaderFieldsTooLarge)`.
   */
  val RequestHeaderFieldsTooLarge: StatusCodec[Unit] =
    status(Status.RequestHeaderFieldsTooLarge)

  /**
   * The codec for the status `Status.InternalServerError` (500); the same as
   * `status(Status.InternalServerError)`.
   */
  val InternalServerError: StatusCodec[Unit] = status(Status.InternalServerError)

  /**
   * The codec for the status `Status.NotImplemented` (501); the same as
   * `status(Status.NotImplemented)`.
   */
  val NotImplemented: StatusCodec[Unit] = status(Status.NotImplemented)

  /**
   * The codec for the status `Status.BadGateway` (502); the same as
   * `status(Status.BadGateway)`.
   */
  val BadGateway: StatusCodec[Unit] = status(Status.BadGateway)

  /**
   * The codec for the status `Status.ServiceUnavailable` (503); the same as
   * `status(Status.ServiceUnavailable)`.
   */
  val ServiceUnavailable: StatusCodec[Unit] = status(Status.ServiceUnavailable)

  /**
   * The codec for the status `Status.GatewayTimeout` (504); the same as
   * `status(Status.GatewayTimeout)`.
   */
  val GatewayTimeout: StatusCodec[Unit] = status(Status.GatewayTimeout)

  /**
   * The codec for the status `Status.HttpVersionNotSupported` (505); the same
   * as `status(Status.HttpVersionNotSupported)`.
   */
  val HttpVersionNotSupported: StatusCodec[Unit] =
    status(Status.HttpVersionNotSupported)

  /**
   * The codec for the status `Status.VariantAlsoNegotiates` (506); the same as
   * `status(Status.VariantAlsoNegotiates)`.
   */
  val VariantAlsoNegotiates: StatusCodec[Unit] = status(Status.VariantAlsoNegotiates)

  /**
   * The codec for the status `Status.InsufficientStorage` (507); the same as
   * `status(Status.InsufficientStorage)`.
   */
  val InsufficientStorage: StatusCodec[Unit] = status(Status.InsufficientStorage)

  /**
   * The codec for the status `Status.NotExtended` (510); the same as
   * `status(Status.NotExtended)`.
   */
  val NotExtended: StatusCodec[Unit] = status(Status.NotExtended)

  /**
   * The codec for the status `Status.NetworkAuthenticationRequired` (511); the
   * same as `status(Status.NetworkAuthenticationRequired)`.
   */
  val NetworkAuthenticationRequired: StatusCodec[Unit] =
    status(Status.NetworkAuthenticationRequired)

  /**
   * Creates a codec for the status `Status.Custom(code, "")`, with an empty
   * reason phrase. Decoding matches a response whose status equals that value,
   * which is what `Status.fromInt` returns for a code that has no predefined
   * `Status`; `CustomStatus(200)` does not match a response with `Status.Ok`.
   */
  def CustomStatus(code: Int): StatusCodec[Unit] = status(Status.Custom(code, ""))

  /**
   * Creates a codec for the status `Status.Custom(code, reasonPhrase)`.
   * Decoding matches a response whose status equals that value, code and reason
   * phrase both.
   */
  def CustomStatus(code: Int, reasonPhrase: String): StatusCodec[Unit] =
    status(Status.Custom(code, reasonPhrase))
}
