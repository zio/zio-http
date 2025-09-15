package example.auth.webauthn2.models

import com.yubico.webauthn.data.ByteArray

/**
 * Storage models for WebAuthn credentials
 */
case class StoredCredential(
                             credentialId: ByteArray,
                             publicKeyCose: ByteArray,
                             signatureCount: Long,
                             username: String,
                             userHandle: ByteArray // Added to support discoverable passkeys
                           )