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

package zio.http.model.headers.values

/**
 * Proxy-Authorization: <type> <credentials>
 *
 * <type> - AuthenticationScheme
 *
 * <credentials> - The resulting string is base64 encoded
 *
 * Example
 *
 * Proxy-Authorization: Basic YWxhZGRpbjpvcGVuc2VzYW1l
 */
final case class ProxyAuthorization(authenticationScheme: AuthenticationScheme, credential: String)

/**
 * The HTTP Proxy-Authorization request header contains the credentials to
 * authenticate a user agent to a proxy server, usually after the server has
 * responded with a 407 Proxy Authentication Required status and the
 * Proxy-Authenticate header.
 */
object ProxyAuthorization {

  def fromProxyAuthorization(proxyAuthorization: ProxyAuthorization): String =
    s"${proxyAuthorization.authenticationScheme.name} ${proxyAuthorization.credential}"

  def toProxyAuthorization(value: String): Either[String, ProxyAuthorization] = {
    value.split("\\s+") match {
      case Array(authorization, credential) if authorization.nonEmpty && credential.nonEmpty =>
        AuthenticationScheme.toAuthenticationScheme(authorization).map { authenticationScheme =>
          ProxyAuthorization(authenticationScheme, credential)
        }
      case _ => Left("Invalid Proxy-Authorization header")
    }
  }
}
