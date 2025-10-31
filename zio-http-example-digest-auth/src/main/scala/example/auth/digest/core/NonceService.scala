package example.auth.digest.core

import java.util.Base64
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

import scala.math.Ordering.Implicits.infixOrderingOps

import zio.Config.Secret
import zio._

import NonceError._

sealed trait NonceError extends Throwable
object NonceError {
  case class NonceExpired(nonce: String)               extends NonceError
  case class NonceAlreadyUsed(nonce: String, nc: NC)   extends NonceError
  case class InvalidNonce(nonce: String)               extends NonceError
  case class NonceOutOfSequence(nonce: String, nc: NC) extends NonceError
}

trait NonceService {
  def generateNonce: UIO[String]
  def validateNonce(nonce: String, maxAge: Duration): ZIO[Any, NonceError, Unit]
  def isNonceUsed(nonce: String, nc: NC): ZIO[Any, NonceError, Unit]
  def markNonceUsed(nonce: String, nc: NC): UIO[Unit]
}

object NonceService {
  final case class NonceServiceLive(
    usedNonces: Ref[Map[String, NC]],
    secret: Secret,
  ) extends NonceService {

    private val HASH_ALGORITHM = "HmacSHA256"
    private val HASH_LENGTH    = 16

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

    def isNonceUsed(nonce: String, nc: NC): ZIO[Any, NonceError, Unit] =
      for {
        usedNoncesMap <- usedNonces.get // Fixed typo
        _             <- usedNoncesMap.get(nonce) match {
          case Some(lastUsedNc) if nc <= lastUsedNc                 =>
            ZIO.fail(NonceAlreadyUsed(nonce, nc))
          case Some(lastUsedNc) if nc.value != lastUsedNc.value + 1 =>
            ZIO.fail(NonceOutOfSequence(nonce, nc))
          case _                                                    =>
            ZIO.unit
        }
      } yield ()

    def markNonceUsed(nonce: String, nc: NC): UIO[Unit] =
      usedNonces.update { nonces =>
        val currentMax = nonces.getOrElse(nonce, NC(0))
        nonces.updated(nonce, currentMax max nc)
      }

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
      usedNoncesRef <- Ref.make(Map.empty[String, NC])
      secretValue = sys.env.getOrElse("NONCE_SECRET", "MY_SERVER_SECRET")
    } yield NonceServiceLive(usedNoncesRef, Secret(secretValue))
  }
}
