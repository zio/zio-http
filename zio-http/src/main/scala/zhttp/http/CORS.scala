package zhttp.http

final case class CORSConfig(
  anyOrigin: Boolean = false,
  anyMethod: Boolean = true,
  allowCredentials: Boolean = false,
  allowedOrigins: String => Boolean = _ => false,
  allowedMethods: Option[Set[Method]] = None,
  allowedHeaders: Option[Set[String]] = Some(Set("Content-Type", "Authorization", "*")),
  exposedHeaders: Option[Set[String]] = Some(Set("*")),
)

object CORS {
  def DefaultCORSConfig = CORSConfig(anyOrigin = true, allowCredentials = true)
}
