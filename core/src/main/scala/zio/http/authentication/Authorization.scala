package zio.http.authentication

final case class Authorization(
  scheme: AuthenticationScheme,
  credentials: Credentials
) {
  override def toString = s"Authorization: $scheme $credentials"
}
