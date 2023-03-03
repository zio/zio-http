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

sealed trait ProxyAuthorization {
  val value: String
}

/**
 * The HTTP Proxy-Authorization request header contains the credentials to
 * authenticate a user agent to a proxy server, usually after the server has
 * responded with a 407 Proxy Authentication Required status and the
 * Proxy-Authenticate header.
 */
object ProxyAuthorization {

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
  final case class ValidProxyAuthorization(authenticationScheme: AuthenticationScheme, credential: String)
      extends ProxyAuthorization {
    override val value = s"${authenticationScheme.name} ${credential}"
  }

  case object InvalidProxyAuthorization extends ProxyAuthorization {
    override val value: String = ""
  }

  def fromProxyAuthorization(proxyAuthorization: ProxyAuthorization): String = {
    proxyAuthorization.value
  }

  def toProxyAuthorization(value: String): ProxyAuthorization = {
    value.split("\\s+") match {
      case Array(authorization, credential) if !authorization.isEmpty && !credential.isEmpty =>
        val authenticationScheme = AuthenticationScheme.toAuthenticationScheme(authorization)
        if (authenticationScheme != AuthenticationScheme.Invalid) {
          ValidProxyAuthorization(authenticationScheme, credential)
        } else InvalidProxyAuthorization
      case _ => InvalidProxyAuthorization
    }
  }
}
