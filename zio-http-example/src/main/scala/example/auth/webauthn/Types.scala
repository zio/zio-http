package example.auth.webauthn

import java.util.Base64

object Types {
  type BufferSource = Array[Byte]
  type COSEAlgorithmIdentifier = Long
  type DOMString = String
  type USVString = String
  type UvmEntry = Seq[Long]

  // Base64url encoding utilities
  object Base64Url {
    private val encoder = Base64.getUrlEncoder.withoutPadding()
    private val decoder = Base64.getUrlDecoder

    def encode(data: Array[Byte]): String = encoder.encodeToString(data)
    def decode(data: String): Array[Byte] = decoder.decode(data)
  }
}
