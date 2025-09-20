package example.auth.webauthn2.models

import com.yubico.webauthn.data.ByteArray

case class UserCredential(
  credentialId: ByteArray,
  publicKeyCose: ByteArray,
  signatureCount: Long,
  username: String,
  userHandle: ByteArray,
)
