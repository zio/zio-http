package example.auth.digest.core

import zio.Config.Secret
import zio._

import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

trait NonceService {
  def generateNonce(timestamp: Long): UIO[String]
  def validateNonce(nonce: String, maxAge: Duration): UIO[Boolean]
  def isNonceUsed(nonce: String, nc: String): UIO[Boolean]
  def markNonceUsed(nonce: String, nc: String): UIO[Unit]
}

object NonceService {

  final case class NonceServiceLive(
    usedNonces: Ref[Map[String, Set[String]]],
    secret: Secret,
  ) extends NonceService {
    private val HASH_ALGORITHM = "HmacSHA256"
    private val HASH_LENGTH    = 16

    private def constantTimeEquals(a: Array[Byte], b: Array[Byte]): Boolean =
      a.length == b.length && a.zip(b).map { case (x, y) => x ^ y }.fold(0)(_ | _) == 0

    private def createHash(timestamp: Long): Array[Byte] = {
      val mac = Mac.getInstance(HASH_ALGORITHM)
      mac.init(new SecretKeySpec(secret.stringValue.getBytes("UTF-8"), HASH_ALGORITHM))
      mac.doFinal(timestamp.toString.getBytes("UTF-8")).take(HASH_LENGTH)
    }

    def generateNonce(timestamp: Long): UIO[String] = ZIO.succeed {
      val hash    = Base64.getEncoder.encodeToString(createHash(timestamp))
      val content = s"$timestamp:$hash"
      Base64.getEncoder.encodeToString(content.getBytes("UTF-8"))
    }

    def validateNonce(nonce: String, maxAge: Duration): UIO[Boolean] = ZIO.succeed {
      try {
        val decoded = new String(Base64.getDecoder.decode(nonce), "UTF-8")
        val parts   = decoded.split(":", 2)

        if (parts.length != 2) false
        else {
          val timestamp         = parts(0).toLong
          val providedHash      = Base64.getDecoder.decode(parts(1))
          val isWithinTimeLimit = java.lang.System.currentTimeMillis() - timestamp <= maxAge.toMillis

          isWithinTimeLimit && constantTimeEquals(createHash(timestamp), providedHash)
        }
      } catch {
        case _: Exception => false
      }
    }

    def isNonceUsed(nonce: String, nc: String): UIO[Boolean] =
      usedNonces.get.map(_.get(nonce).exists(_.contains(nc)))

    def markNonceUsed(nonce: String, nc: String): UIO[Unit] =
      usedNonces.update(nonces => nonces.updated(nonce, nonces.getOrElse(nonce, Set.empty) + nc))
  }

  val live: ULayer[NonceService] = ZLayer.fromZIO {
    for {
      usedNoncesRef <- Ref.make(Map.empty[String, Set[String]])
      secretValue = sys.env.getOrElse("NONCE_SECRET", "MY_SERVER_SECRET")
    } yield NonceServiceLive(usedNoncesRef, Secret(secretValue))
  }
}
