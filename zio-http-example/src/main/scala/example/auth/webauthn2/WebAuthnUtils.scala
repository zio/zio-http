package example.auth.webauthn2

import com.yubico.webauthn.data.ByteArray

import java.security.SecureRandom

/**
 * Utility functions for WebAuthn operations
 */
object WebAuthnUtils {

  def generateChallenge(): ByteArray = {
    val bytes = new Array[Byte](32)
    new SecureRandom().nextBytes(bytes)
    new ByteArray(bytes)
  }

  def generateUserHandle(username: String): Array[Byte] = {
    // Generate a unique handle combining username and random bytes
    val random = new Array[Byte](16)
    new SecureRandom().nextBytes(random)
    val combined = username.getBytes() ++ random
    java.security.MessageDigest.getInstance("SHA-256").digest(combined).take(32)
  }

  def base64UrlEncode(bytes: Array[Byte]): String =
    java.util.Base64.getUrlEncoder.withoutPadding.encodeToString(bytes)

  def base64UrlDecode(str: String): Array[Byte] =
    java.util.Base64.getUrlDecoder.decode(str)
}