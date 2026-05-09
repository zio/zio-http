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
private[codec] trait StatusCodecs {
  def status(status: Status): StatusCodec[Unit] = HttpCodec.Status(SimpleCodec.Specified(status))

  val Continue: StatusCodec[Unit]                      = status(Status.Continue)
  val SwitchingProtocols: StatusCodec[Unit]            = status(Status.SwitchingProtocols)
  val Processing: StatusCodec[Unit]                    = status(Status.Processing)
  val Ok: StatusCodec[Unit]                            = status(Status.Ok)
  val Created: StatusCodec[Unit]                       = status(Status.Created)
  val Accepted: StatusCodec[Unit]                      = status(Status.Accepted)
  val NonAuthoritativeInformation: StatusCodec[Unit]   =
    status(Status.NonAuthoritativeInformation)
  val NoContent: StatusCodec[Unit]                     = status(Status.NoContent)
  val ResetContent: StatusCodec[Unit]                  = status(Status.ResetContent)
  val PartialContent: StatusCodec[Unit]                = status(Status.PartialContent)
  val MultiStatus: StatusCodec[Unit]                   = status(Status.MultiStatus)
  val MultipleChoices: StatusCodec[Unit]               = status(Status.MultipleChoices)
  val MovedPermanently: StatusCodec[Unit]              = status(Status.MovedPermanently)
  val Found: StatusCodec[Unit]                         = status(Status.Found)
  val SeeOther: StatusCodec[Unit]                      = status(Status.SeeOther)
  val NotModified: StatusCodec[Unit]                   = status(Status.NotModified)
  val UseProxy: StatusCodec[Unit]                      = status(Status(305))
  val TemporaryRedirect: StatusCodec[Unit]             = status(Status.TemporaryRedirect)
  val PermanentRedirect: StatusCodec[Unit]             = status(Status.PermanentRedirect)
  val BadRequest: StatusCodec[Unit]                    = status(Status.BadRequest)
  val Unauthorized: StatusCodec[Unit]                  = status(Status.Unauthorized)
  val PaymentRequired: StatusCodec[Unit]               = status(Status.PaymentRequired)
  val Forbidden: StatusCodec[Unit]                     = status(Status.Forbidden)
  val NotFound: StatusCodec[Unit]                      = status(Status.NotFound)
  val MethodNotAllowed: StatusCodec[Unit]              = status(Status.MethodNotAllowed)
  val NotAcceptable: StatusCodec[Unit]                 = status(Status.NotAcceptable)
  val ProxyAuthenticationRequired: StatusCodec[Unit]   =
    status(Status.ProxyAuthenticationRequired)
  val RequestTimeout: StatusCodec[Unit]                = status(Status.RequestTimeout)
  val Conflict: StatusCodec[Unit]                      = status(Status.Conflict)
  val Gone: StatusCodec[Unit]                          = status(Status.Gone)
  val LengthRequired: StatusCodec[Unit]                = status(Status.LengthRequired)
  val PreconditionFailed: StatusCodec[Unit]            = status(Status.PreconditionFailed)
  val RequestEntityTooLarge: StatusCodec[Unit]         = status(Status.PayloadTooLarge)
  val RequestUriTooLong: StatusCodec[Unit]             = status(Status.UriTooLong)
  val UnsupportedMediaType: StatusCodec[Unit]          = status(Status.UnsupportedMediaType)
  val RequestedRangeNotSatisfiable: StatusCodec[Unit]  =
    status(Status.RangeNotSatisfiable)
  val ExpectationFailed: StatusCodec[Unit]             = status(Status.ExpectationFailed)
  val MisdirectedRequest: StatusCodec[Unit]            = status(Status.MisdirectedRequest)
  val UnprocessableEntity: StatusCodec[Unit]           = status(Status.UnprocessableEntity)
  val Locked: StatusCodec[Unit]                        = status(Status(423))
  val FailedDependency: StatusCodec[Unit]              = status(Status(424))
  val UnorderedCollection: StatusCodec[Unit]           = status(Status(425))
  val UpgradeRequired: StatusCodec[Unit]               = status(Status.UpgradeRequired)
  val PreconditionRequired: StatusCodec[Unit]          = status(Status.PreconditionRequired)
  val TooManyRequests: StatusCodec[Unit]               = status(Status.TooManyRequests)
  val RequestHeaderFieldsTooLarge: StatusCodec[Unit]   =
    status(Status.RequestHeaderFieldsTooLarge)
  val InternalServerError: StatusCodec[Unit]           = status(Status.InternalServerError)
  val NotImplemented: StatusCodec[Unit]                = status(Status.NotImplemented)
  val BadGateway: StatusCodec[Unit]                    = status(Status.BadGateway)
  val ServiceUnavailable: StatusCodec[Unit]            = status(Status.ServiceUnavailable)
  val GatewayTimeout: StatusCodec[Unit]                = status(Status.GatewayTimeout)
  val HttpVersionNotSupported: StatusCodec[Unit]       =
    status(Status.HttpVersionNotSupported)
  val VariantAlsoNegotiates: StatusCodec[Unit]         = status(Status(506))
  val InsufficientStorage: StatusCodec[Unit]           = status(Status.InsufficientStorage)
  val NotExtended: StatusCodec[Unit]                   = status(Status(510))
  val NetworkAuthenticationRequired: StatusCodec[Unit] =
    status(Status.NetworkAuthenticationRequired)
  def CustomStatus(code: Int): StatusCodec[Unit]       = status(Status(code))

  def CustomStatus(code: Int, reasonPhrase: String): StatusCodec[Unit] =
    status(Status(code))
}
