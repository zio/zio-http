package zhttp.http.middleware

import io.netty.handler.codec.http.HttpHeaderNames
import zhttp.http.Method

final case class CORSConfig(
  anyOrigin: Boolean = false,
  anyMethod: Boolean = true,
  allowCredentials: Boolean = false,
  allowedOrigins: String => Boolean = _ => false,
  allowedMethods: Option[Set[Method]] = None,
  allowedHeaders: Option[Set[String]] = Some(
    Set(HttpHeaderNames.CONTENT_TYPE.toString, HttpHeaderNames.AUTHORIZATION.toString, "*"),
  ),
  exposedHeaders: Option[Set[String]] = Some(Set("*")),
)

object CORS {
  def DefaultCORSConfig = CORSConfig(anyOrigin = true, allowCredentials = true)
}
