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
