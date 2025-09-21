package example.auth.webauthn.model

import com.yubico.webauthn.data.ByteArray

case class UserCredential(
  credentialId: ByteArray,
  publicKeyCose: ByteArray,
  signatureCount: Long,
  userHandle: ByteArray,
)
