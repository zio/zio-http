package zio.http.model.headers.values

/**
 * The HTTP Proxy-Authenticate response header defines the authentication method
 * that should be used to gain access to a resource behind a proxy server. It
 * authenticates the request to the proxy server, allowing it to transmit the
 * request further.
 */
sealed trait ProxyAuthenticate

object ProxyAuthenticate {

  /**
   * @param scheme
   *   Authentication type
   * @param realm
   *   A description of the protected area, the realm. If no realm is specified,
   *   clients often display a formatted host name instead.
   */
  final case class ValidProxyAuthenticate(scheme: AuthenticationScheme, realm: Option[String]) extends ProxyAuthenticate

  case object InvalidProxyAuthenticate extends ProxyAuthenticate

  def toProxyAuthenticate(value: String): ProxyAuthenticate = {
    val parts = value.split(" realm=").map(_.trim).filter(_.nonEmpty)
    parts match {
      case Array(authScheme, realm) => toProxyAuthenticate(authScheme, Some(realm))
      case Array(authScheme)        => toProxyAuthenticate(authScheme, None)
      case _                        => InvalidProxyAuthenticate
    }
  }

  def fromProxyAuthenticate(proxyAuthenticate: ProxyAuthenticate): String = proxyAuthenticate match {
    case ValidProxyAuthenticate(scheme, Some(realm)) => s"${scheme.name} realm=$realm"
    case ValidProxyAuthenticate(scheme, None)        => s"${scheme.name}"
    case InvalidProxyAuthenticate                    => ""
  }

  private def toProxyAuthenticate(authScheme: String, realm: Option[String]): ProxyAuthenticate = {
    val scheme = AuthenticationScheme.toAuthenticationScheme(authScheme)
    if (scheme != AuthenticationScheme.Invalid) ValidProxyAuthenticate(scheme, realm)
    else InvalidProxyAuthenticate
  }

}
