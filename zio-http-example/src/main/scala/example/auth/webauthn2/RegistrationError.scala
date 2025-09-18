package example.auth.webauthn2

sealed trait RegistrationError extends Throwable
case class SessionNotFound(message: String) extends RegistrationError
case class RegistrationFailed(message: String) extends RegistrationError
case class InvalidRequest(message: String) extends RegistrationError
