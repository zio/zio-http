package example.auth.digest.core

import zio._
import zio.Config.Secret
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.util.Base64

trait NonceService {
  def generateNonce(timestamp: Long): UIO[String]
  def validateNonce(nonce: String, maxAge: Duration): UIO[Boolean]
  def isNonceUsed(nonce: String, nc: String): UIO[Boolean]
  def markNonceUsed(nonce: String, nc: String): UIO[Unit]
}

object NonceService {

  final case class NonceServiceLive(
    usedNonce: Ref[Map[String, Set[String]]],
    secret: Secret,
  ) extends NonceService {

    private def constantTimeEquals(a: Array[Byte], b: Array[Byte]): Boolean = {
      if (a.length != b.length) false
      else {
        var result = 0
        for (i <- a.indices) {
          result |= a(i) ^ b(i)
        }
        result == 0
      }
    }

    private def computeHash(timestamp: Long, secretKey: Secret): Array[Byte] = {
      val mac = Mac.getInstance("HmacSHA256")
      mac.init(new SecretKeySpec(secretKey.stringValue.getBytes("UTF-8"), "HMAC-SHA256"))

      val input = s"$timestamp"
      Array.copyOf(mac.doFinal(input.getBytes("UTF-8")), 16)
    }

    def generateNonce(timestamp: Long): UIO[String] =
      ZIO.succeed {
        val hashBytes    = computeHash(timestamp, secret)
        val base64Hash   = Base64.getEncoder.encodeToString(hashBytes)
        val nonceContent = s"$timestamp:$base64Hash"

        Base64.getEncoder.encodeToString(nonceContent.getBytes("UTF-8"))
      }

    def validateNonce(nonce: String, maxAge: Duration): UIO[Boolean] =
      ZIO.succeed {
        try {
          val decoded = new String(Base64.getDecoder.decode(nonce), "UTF-8")
          val parts   = decoded.split(":", 2)

          if (parts.length != 2) {
            false
          } else {
            val providedHashHex = parts(1)
            val timestamp       = parts(0).toLong

            // Verify timestamp is within allowed age
            val now              = java.lang.System.currentTimeMillis()
            val isTimestampValid = (now - timestamp) <= maxAge.toMillis

            if (!isTimestampValid) {
              false
            } else {
              val expectedHashBytes =
                computeHash(timestamp, secret)

              // Convert provided hash back to bytes for constant-time comparison
              constantTimeEquals(expectedHashBytes, Base64.getDecoder.decode(providedHashHex))
            }
          }
        } catch {
          case _: Exception => false
        }
      }

    def isNonceUsed(nonce: String, nc: String): UIO[Boolean] =
      usedNonce.get.map(_.get(nonce).exists(_.contains(nc)))

    def markNonceUsed(nonce: String, nc: String): UIO[Unit] =
      usedNonce.update { nonces =>
        val existing = nonces.getOrElse(nonce, Set.empty)
        nonces.updated(nonce, existing + nc)
      }
  }

  val live: ULayer[NonceService] = ZLayer {
    for {
      usedNoncesRef <- Ref.make[Map[String, Set[String]]](Map.empty)
      secretValue   <- ZIO.succeed(sys.env.getOrElse("NONCE_SECRET", "MY_SERVER_SECRET"))
      secret = Secret(secretValue)
    } yield NonceServiceLive(usedNoncesRef, secret)
  }

}
