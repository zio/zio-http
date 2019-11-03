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

case class StatusCode(value: Int) extends AnyVal {
  def isInformation: Boolean = value / 100 == 1
  def isSuccess: Boolean = value / 100 == 2
  def isRedirect: Boolean = value / 100 == 3
  def isClientError: Boolean = value / 100 == 4
  def isServerError: Boolean = value / 100 == 5
}

object StatusCode {
  val Continue = StatusCode(100)
  val SwitchingProtocols = StatusCode(101)
  val OK = StatusCode(200)
  val Created = StatusCode(201)
  val Accepted = StatusCode(202)
  val NonAuthoritativeInformation = StatusCode(203)
  val NoContent = StatusCode(204)
  val ResetContent = StatusCode(205)
  val PartialContent = StatusCode(206)
  val MultipleChoices = StatusCode(300)
  val MovedPermanently = StatusCode(301)
  val Found = StatusCode(302)
  val SeeOther = StatusCode(303)
  val NotModified = StatusCode(304)
  val UseProxy = StatusCode(305)
  val TemporaryRedirect = StatusCode(307)
  val BadRequest = StatusCode(400)
  val Unauthorized = StatusCode(401)
  val PaymentRequired = StatusCode(402)
  val Forbidden = StatusCode(403)
  val NotFound = StatusCode(404)
  val MethodNotAllowed = StatusCode(405)
  val NotAcceptable = StatusCode(406)
  val ProxyAuthenticationRequired = StatusCode(407)
  val RequestTimeout = StatusCode(408)
  val Conflict = StatusCode(409)
  val Gone = StatusCode(410)
  val LengthRequired = StatusCode(411)
  val PreconditionFailed = StatusCode(412)
  val PayloadTooLarge = StatusCode(413)
  val URITooLong = StatusCode(414)
  val UnsupportedMediaType = StatusCode(415)
  val RangeNotSatisfiable = StatusCode(416)
  val ExpectationFailed = StatusCode(417)
  val UpgradeRequired = StatusCode(426)
  val InternalServerError = StatusCode(500)
  val NotImplemented = StatusCode(501)
  val BadGateway = StatusCode(502)
  val ServiceUnavailable = StatusCode(503)
  val GatewayTimeout = StatusCode(504)
  val HTTPVersionNotSupported = StatusCode(505)
}
