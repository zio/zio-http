package zio.http.model

final case class Authorization(
  scheme: AuthenticationScheme,
  credentials: Credentials
) {
  override def toString = s"Authorization: $scheme $credentials"
}
