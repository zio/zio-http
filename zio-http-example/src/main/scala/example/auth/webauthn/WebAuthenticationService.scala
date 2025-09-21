package example.auth.webauthn
import zio._
import example.auth.webauthn.models._

trait WebAuthenticationService {
  def startRegistration(username: String): Task[RegistrationStartResponse]

  def finishRegistration(request: RegistrationFinishRequest): Task[RegistrationFinishResponse]

  def startAuthentication(username: Option[String]): Task[AuthenticationStartResponse]

  def finishAuthentication(request: AuthenticationFinishRequest): Task[AuthenticationFinishResponse]
}

