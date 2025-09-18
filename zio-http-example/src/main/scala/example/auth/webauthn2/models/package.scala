package example.auth.webauthn2

import com.yubico.webauthn.data.PublicKeyCredentialCreationOptions

package object models {
  type RegistrationStartResponse = PublicKeyCredentialCreationOptions
}
