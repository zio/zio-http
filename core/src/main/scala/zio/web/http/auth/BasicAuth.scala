package zio.web.http.auth

import java.nio.charset.StandardCharsets

import zio.web.http.model.StatusCode
import zio.web.http.{ HttpHeaders, Patch }

object BasicAuth {
  sealed trait AuthResult

  object AuthResult {
    case object Granted extends AuthResult
    case object Denied  extends AuthResult
  }

  final case class AuthParams(realm: String, user: String, password: String)

  object AuthParams {

    def create(realm: String, header: String): Option[AuthParams] =
      if (!header.startsWith("Basic")) {
        None
      } else {
        val data = header.drop("Basic ".length).trim

        // TODO: base64 decode data
        val decoded = new String(java.util.Base64.getDecoder().decode(data), StandardCharsets.UTF_8)
        val split   = decoded.split(":")

        if (split.length == 2) {
          val username = split(0)
          val password = split(1)
          Some(AuthParams(realm, username, password))
        } else {
          None
        }
      }
  }

  private val unauthorized: Patch = Patch.setStatus(StatusCode.Unauthorized)

  val forbidden: Patch = Patch.setStatus(StatusCode.Forbidden)

  def unauthorized(realm: String): Patch = unauthorized + Patch.addHeaders(
    HttpHeaders(Map("WWW-Authenticate" -> s"""Basic realm=\"$realm\""""))
  )
}
