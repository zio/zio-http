package example.auth.webauthn2
import com.yubico.webauthn.data.PublicKeyCredentialCreationOptions
import models._
import zio._



trait RegistrationService {
  def startRegistration(request: RegistrationStartRequest): IO[RegistrationError, RegistrationStartResponse]
  def finishRegistration(
                          request: RegistrationFinishRequest,
                          credentialCreationOptions: PublicKeyCredentialCreationOptions
                        ): IO[RegistrationError, RegistrationFinishResponse]
}