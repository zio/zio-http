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
 * The HTTP Proxy-Authenticate response header defines the authentication method
 * that should be used to gain access to a resource behind a proxy server. It
 * authenticates the request to the proxy server, allowing it to transmit the
 * request further.
 *
 * @param scheme
 *   Authentication type
 * @param realm
 *   A description of the protected area, the realm. If no realm is specified,
 *   clients often display a formatted host name instead.
 */
final case class ProxyAuthenticate(scheme: AuthenticationScheme, realm: Option[String])

object ProxyAuthenticate {
  def toProxyAuthenticate(value: String): Either[String, ProxyAuthenticate] = {
    val parts = value.split(" realm=").map(_.trim).filter(_.nonEmpty)
    parts match {
      case Array(authScheme, realm) => toProxyAuthenticate(authScheme, Some(realm))
      case Array(authScheme)        => toProxyAuthenticate(authScheme, None)
      case _                        => Left("Invalid Proxy-Authenticate header")
    }
  }

  def fromProxyAuthenticate(proxyAuthenticate: ProxyAuthenticate): String = proxyAuthenticate match {
    case ProxyAuthenticate(scheme, Some(realm)) => s"${scheme.name} realm=$realm"
    case ProxyAuthenticate(scheme, None)        => s"${scheme.name}"
  }

  private def toProxyAuthenticate(authScheme: String, realm: Option[String]): Either[String, ProxyAuthenticate] =
    AuthenticationScheme.toAuthenticationScheme(authScheme).map(ProxyAuthenticate(_, realm))
}
