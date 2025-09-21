package example.auth.webauthn.core

import example.auth.webauthn.model._
import zio._

sealed trait AuthenticationError                           extends Throwable
case class NoRegistrationRequestFound(username: String)    extends AuthenticationError
case class UserVerificationFailed(username: String)        extends AuthenticationError
case class NoAuthenticationRequestFound(challenge: String) extends AuthenticationError

trait WebAuthenticationService {
  def startRegistration(username: String): IO[AuthenticationError, RegistrationStartResponse]
  def finishRegistration(request: RegistrationFinishRequest): IO[AuthenticationError, RegistrationFinishResponse]
  def startAuthentication(username: Option[String]): IO[AuthenticationError, AuthenticationStartResponse]
  def finishAuthentication(request: AuthenticationFinishRequest): IO[AuthenticationError, AuthenticationFinishResponse]
}
