package example.auth.webauthn

import com.yubico.webauthn.AssertionRequest
import com.yubico.webauthn.data.PublicKeyCredentialCreationOptions

package object model {
  type RegistrationStartResponse   = PublicKeyCredentialCreationOptions
  type AuthenticationStartResponse = AssertionRequest

  type UserHandle = String
  type Challenge  = String
}
