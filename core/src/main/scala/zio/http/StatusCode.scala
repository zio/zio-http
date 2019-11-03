/*
 * Copyright 2017-2019 John A. De Goes and the ZIO Contributors
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

trait StatusCode {
  val Continue = 100
  val SwitchingProtocols = 101
  val OK = 200
  val Created = 201
  val Accepted = 202
  val NonAuthoritativeInformation = 203
  val NoContent = 204
  val ResetContent = 205
  val PartialContent = 206
  val MultipleChoices = 300
  val MovedPermanently = 301
  val Found = 302
  val SeeOther = 303
  val NotModified = 304
  val UseProxy = 305
  val TemporaryRedirect = 307
  val BadRequest = 400
  val Unauthorized = 401
  val PaymentRequired = 402
  val Forbidden = 403
  val NotFound = 404
  val MethodNotAllowed = 405
  val NotAcceptable = 406
  val ProxyAuthenticationRequired = 407
  val RequestTimeout = 408
  val Conflict = 409
  val Gone = 410
  val LengthRequired = 411
  val PreconditionFailed = 412
  val PayloadTooLarge = 413
  val URITooLong = 414
  val UnsupportedMediaType = 415
  val RangeNotSatisfiable = 416
  val ExpectationFailed = 417
  val UpgradeRequired = 426
  val InternalServerError = 500
  val NotImplemented = 501
  val BadGateway = 502
  val ServiceUnavailable = 503
  val GatewayTimeout = 504
  val HTTPVersionNotSupported = 505
}
