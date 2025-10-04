package example.auth.webauthn.model

import com.yubico.webauthn.data.ByteArray

case class UserCredential(
  userHandle: ByteArray,
  credentialId: ByteArray,
  publicKeyCose: ByteArray,
  signatureCount: Long,
)
