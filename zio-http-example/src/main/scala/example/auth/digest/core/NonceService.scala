package example.auth.digest.core
import example.auth.digest.core.NonceError.{InvalidNonce, NonceAlreadyUsed, NonceExpired}
import zio.Config.Secret
import zio._

import java.util.Base64
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

// NonceService-specific error types
sealed trait NonceError extends Serializable with Product
object NonceError {
  case class NonceExpired(nonce: String)                 extends NonceError
  case class NonceAlreadyUsed(nonce: String, nc: String) extends NonceError
  case class InvalidNonce(nonce: String)                 extends NonceError
}

trait NonceService {
  def generateNonce: UIO[String]
  def validateNonce(nonce: String, maxAge: Duration): ZIO[Any, NonceError, Unit]
  def isNonceUsed(nonce: String, nc: String): ZIO[Any, NonceError, Unit]
  def markNonceUsed(nonce: String, nc: String): ZIO[Any, NonceError, Unit]
}

object NonceService {
  final case class NonceServiceLive(
    // Now tracking highest nc value per nonce instead of all nc values
    usedNonces: Ref[Map[String, Int]],
    secret: Secret,
  ) extends NonceService {

    private val HASH_ALGORITHM = "HmacSHA256"
    private val HASH_LENGTH    = 16
    private val NC_RADIX       = 16 // RFC 2617 specifies the hexadecimal format

    def generateNonce: UIO[String] =
      Clock.currentTime(TimeUnit.MILLISECONDS).map { timestamp =>
        val hash    = Base64.getEncoder.encodeToString(createHash(timestamp))
        val content = s"$timestamp:$hash"
        Base64.getEncoder.encodeToString(content.getBytes("UTF-8"))
      }

    def validateNonce(nonce: String, maxAge: Duration): ZIO[Any, NonceError, Unit] =
      ZIO.fromEither {
        try {
          val decoded = new String(Base64.getDecoder.decode(nonce), "UTF-8")
          val parts   = decoded.split(":", 2)
          if (parts.length != 2) {
            Left(InvalidNonce(nonce))
          } else {
            val timestamp         = parts(0).toLong
            val providedHash      = Base64.getDecoder.decode(parts(1))
            val isWithinTimeLimit = java.lang.System.currentTimeMillis() - timestamp <= maxAge.toMillis
            if (!isWithinTimeLimit) {
              Left(NonceExpired(nonce))
            } else if (!constantTimeEquals(createHash(timestamp), providedHash)) {
              Left(InvalidNonce(nonce))
            } else {
              Right(())
            }
          }
        } catch {
          case _: Exception => Left(InvalidNonce(nonce))
        }
      }
    def isNonceUsed(nonce: String, nc: String): ZIO[Any, NonceError, Unit]         =
      for {
        ncValue       <- ZIO.attempt(Integer.parseInt(nc, NC_RADIX)).mapError(_ => InvalidNonce(nonce))
        usedNoncesMap <- usedNonces.get
        _             <- usedNoncesMap.get(nonce) match {
          case Some(lastUsedNc) if ncValue <= lastUsedNc =>
            ZIO.fail(NonceAlreadyUsed(nonce, nc))
          case _                                         =>
            ZIO.unit
        }
      } yield ()

    def markNonceUsed(nonce: String, nc: String): ZIO[Any, NonceError, Unit] =
      for {
        ncValue <- ZIO
          .attempt(Integer.parseInt(nc, NC_RADIX))
          .mapError(_ => NonceError.InvalidNonce(nonce))
        _       <- usedNonces.update { nonces =>
          val currentMax = nonces.getOrElse(nonce, 0)
          nonces.updated(nonce, math.max(currentMax, ncValue))
        }
      } yield ()

    private def createHash(timestamp: Long): Array[Byte] = {
      val mac = Mac.getInstance(HASH_ALGORITHM)
      mac.init(new SecretKeySpec(secret.stringValue.getBytes("UTF-8"), HASH_ALGORITHM))
      mac.doFinal(timestamp.toString.getBytes("UTF-8")).take(HASH_LENGTH)
    }

    private def constantTimeEquals(a: Array[Byte], b: Array[Byte]): Boolean =
      a.length == b.length && a.zip(b).map { case (x, y) => x ^ y }.fold(0)(_ | _) == 0
  }

  val live: ULayer[NonceService] = ZLayer.fromZIO {
    for {
      usedNoncesRef <- Ref.make(Map.empty[String, Int])
      secretValue = sys.env.getOrElse("NONCE_SECRET", "MY_SERVER_SECRET")
    } yield NonceServiceLive(usedNoncesRef, Secret(secretValue))
  }
}
