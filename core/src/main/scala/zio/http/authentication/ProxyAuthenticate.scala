package zio.http.authentication

final case class ProxyAuthenticate(scheme: AuthenticationScheme, realm: Realm) {
  override def toString = s"ProxyAuthenticate: $scheme $realm"
}
