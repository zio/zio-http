package example.auth.webauthn

sealed trait RegistrationError                      extends Throwable with Serializable with Product
case class NoRegistrationRequest(username: String)  extends RegistrationError
case class UserVerificationFailed(username: String) extends RegistrationError

sealed trait AuthenticationError extends Throwable
case class NoAuthenticationRequest(challenge: String) extends RegistrationError
