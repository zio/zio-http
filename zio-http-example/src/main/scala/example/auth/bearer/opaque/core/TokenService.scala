package example.auth.bearer.opaque.core

import zio._

import java.security.SecureRandom
import java.time.Instant

trait TokenService {
  def create(username: String): UIO[String]
  def validate(token: String): UIO[Option[String]]
  def cleanup(): UIO[Unit]
  def revoke(username: String): UIO[Unit]
}

object TokenService {
  private val CLEANUP_INTERVAL = 60.seconds

  val inmemory: ZLayer[Any, Nothing, InmemoryTokenService] =
    ZLayer.scoped(
      for {
        service <- Ref.make(Map.empty[String, TokenInfo]).map(new InmemoryTokenService(_))
        _       <- service.cleanup().repeat(Schedule.spaced(CLEANUP_INTERVAL)).forkScoped
      } yield service,
    )
}

case class TokenInfo(username: String, expiresAt: Instant)

class InmemoryTokenService(tokenStorage: Ref[Map[String, TokenInfo]]) extends TokenService {

  private val TOKEN_LIFETIME = 300.seconds

  override def create(username: String): UIO[String] =
    for {
      token <- generateSecureToken
      now   <- Clock.instant
      _     <- tokenStorage.update { tokens =>
        tokens + (token -> TokenInfo(
          username = username,
          expiresAt = now.plusSeconds(TOKEN_LIFETIME.toSeconds),
        ))
      }
    } yield token

  override def validate(token: String): UIO[Option[String]] =
    tokenStorage.modify { tokens =>
      tokens.get(token) match {
        case Some(tokenInfo) if tokenInfo.expiresAt.isAfter(Instant.now()) =>
          (Some(tokenInfo.username), tokens)
        case Some(_)                                                       =>
          // Token expired, remove it
          (None, tokens - token)
        case None                                                          =>
          (None, tokens)
      }
    }

  override def cleanup(): UIO[Unit] =
    tokenStorage.update {
      _.filter { case (_, tokenInfo) =>
        tokenInfo.expiresAt.isAfter(Instant.now())
      }
    }

  override def revoke(username: String): UIO[Unit] =
    tokenStorage.update {
      _.filter { case (_, tokenInfo) =>
        tokenInfo.username != username
      }
    }

  private def generateSecureToken: UIO[String] =
    ZIO.succeed {
      val random = new SecureRandom()
      val bytes  = new Array[Byte](32)
      random.nextBytes(bytes)
      java.util.Base64.getUrlEncoder.withoutPadding.encodeToString(bytes)
    }
}
