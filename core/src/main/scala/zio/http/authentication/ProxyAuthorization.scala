package zio.http.authentication

final case class ProxyAuthorization(scheme: AuthenticationScheme, credentials: Credentials) {
  override def toString = s"ProxyAuthorization: $scheme $credentials"
}
