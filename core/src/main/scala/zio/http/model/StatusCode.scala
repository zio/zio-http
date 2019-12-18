/*
 *
 *  Copyright 2017-2019 John A. De Goes and the ZIO Contributors
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package zio.http.model

final case class StatusCode(value: Int) extends AnyVal {
  def isInformation: Boolean = value / 100 == 1
  def isSuccess: Boolean     = value / 100 == 2
  def isRedirect: Boolean    = value / 100 == 3
  def isClientError: Boolean = value / 100 == 4
  def isServerError: Boolean = value / 100 == 5
}

final object StatusCode {
  final val Continue                    = StatusCode(100)
  final val SwitchingProtocols          = StatusCode(101)
  final val OK                          = StatusCode(200)
  final val Created                     = StatusCode(201)
  final val Accepted                    = StatusCode(202)
  final val NonAuthoritativeInformation = StatusCode(203)
  final val NoContent                   = StatusCode(204)
  final val ResetContent                = StatusCode(205)
  final val PartialContent              = StatusCode(206)
  final val MultipleChoices             = StatusCode(300)
  final val MovedPermanently            = StatusCode(301)
  final val Found                       = StatusCode(302)
  final val SeeOther                    = StatusCode(303)
  final val NotModified                 = StatusCode(304)
  final val UseProxy                    = StatusCode(305)
  final val TemporaryRedirect           = StatusCode(307)
  final val BadRequest                  = StatusCode(400)
  final val Unauthorized                = StatusCode(401)
  final val PaymentRequired             = StatusCode(402)
  final val Forbidden                   = StatusCode(403)
  final val NotFound                    = StatusCode(404)
  final val MethodNotAllowed            = StatusCode(405)
  final val NotAcceptable               = StatusCode(406)
  final val ProxyAuthenticationRequired = StatusCode(407)
  final val RequestTimeout              = StatusCode(408)
  final val Conflict                    = StatusCode(409)
  final val Gone                        = StatusCode(410)
  final val LengthRequired              = StatusCode(411)
  final val PreconditionFailed          = StatusCode(412)
  final val PayloadTooLarge             = StatusCode(413)
  final val URITooLong                  = StatusCode(414)
  final val UnsupportedMediaType        = StatusCode(415)
  final val RangeNotSatisfiable         = StatusCode(416)
  final val ExpectationFailed           = StatusCode(417)
  final val UpgradeRequired             = StatusCode(426)
  final val InternalServerError         = StatusCode(500)
  final val NotImplemented              = StatusCode(501)
  final val BadGateway                  = StatusCode(502)
  final val ServiceUnavailable          = StatusCode(503)
  final val GatewayTimeout              = StatusCode(504)
  final val HTTPVersionNotSupported     = StatusCode(505)
}
