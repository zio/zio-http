package example.auth.digest.another

import zio._
import zio.Config.Secret
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.util.Base64

trait NonceService {
  def generateNonce(timestamp: Long, etag: Option[String] = None): UIO[String]
  def validateNonce(nonce: String, maxAge: Duration, etag: Option[String] = None): UIO[Boolean]
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

    private def computeHash(timestamp: Long, etag: Option[String], secretData: String): Array[Byte] = {
      val mac       = Mac.getInstance("HmacSHA256")
      val secretKey = new SecretKeySpec(secretData.getBytes("UTF-8"), "HmacSHA256")
      mac.init(secretKey)

      val etagValue = etag.getOrElse("")
      val input     = s"$timestamp:$etagValue"
      mac.doFinal(input.getBytes("UTF-8"))
    }

    private def bytesToHex(bytes: Array[Byte]): String = {
      bytes.map(b => String.format("%02x", b & 0xff)).mkString
    }

    private def hexToBytes(hex: String): Option[Array[Byte]] = {
      try {
        if (hex.length % 2 != 0) None
        else Some(hex.grouped(2).map(Integer.parseInt(_, 16).toByte).toArray)
      } catch {
        case _: Exception => None
      }
    }

    def generateNonce(timestamp: Long, etag: Option[String] = None): UIO[String] =
      ZIO.succeed {
        val preciseTimestamp = timestamp

        // Compute hash: H(timestamp ":" ETag ":" secret-data)
        val hashBytes = computeHash(preciseTimestamp, etag, secret.stringValue)
        val hashHex   = bytesToHex(hashBytes)

        val nonceContent = s"$preciseTimestamp:$hashHex"

        // Base64 encode the complete nonce
        Base64.getEncoder.encodeToString(nonceContent.getBytes("UTF-8"))
      }

    def validateNonce(nonce: String, maxAge: Duration, etag: Option[String] = None): UIO[Boolean] =
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
                computeHash(timestamp, etag, secret.stringValue)

              // Convert provided hash back to bytes for constant-time comparison
              hexToBytes(providedHashHex) match {
                case Some(providedHashBytes) =>
                  constantTimeEquals(expectedHashBytes, providedHashBytes)
                case None                    =>
                  false
              }
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
