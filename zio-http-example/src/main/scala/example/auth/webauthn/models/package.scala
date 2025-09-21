package example.auth.webauthn

import com.yubico.webauthn.AssertionRequest
import com.yubico.webauthn.data.PublicKeyCredentialCreationOptions

package object models {
  type RegistrationStartResponse = PublicKeyCredentialCreationOptions
  type AuthenticationStartResponse = AssertionRequest
}
