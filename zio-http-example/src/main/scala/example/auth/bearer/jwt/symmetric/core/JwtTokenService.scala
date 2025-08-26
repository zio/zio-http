package example.auth.bearer.jwt.symmetric.core

import pdi.jwt.algorithms.JwtHmacAlgorithm
import pdi.jwt.{Jwt, JwtAlgorithm, JwtClaim}
import zio.Config.Secret
import zio._

import java.time.Clock

trait JwtTokenService {
  def issue(username: String): UIO[String]
  def verify(token: String): Task[String]
}

case class JwtAuthServiceLive(
  secretKey: Secret,
  tokenTTL: Duration,
  algorithm: JwtHmacAlgorithm,
) extends JwtTokenService {
  implicit val clock: Clock = Clock.systemUTC

  override def issue(username: String): UIO[String] =
    ZIO.succeed {
      Jwt.encode(
        JwtClaim(subject = Some(username)).issuedNow.expiresIn(tokenTTL.toSeconds),
        secretKey.stringValue,
        algorithm,
      )
    }

  override def verify(token: String): Task[String] =
    ZIO
      .fromTry(
        Jwt.decode(token, secretKey.stringValue, Seq(algorithm)),
      )
      .map(_.subject)
      .some
      .orElse(ZIO.fail(new Exception("Invalid token")))
}

object JwtTokenService {
  def live(
    secretKey: Secret,
    tokenTTL: Duration = 15.minutes,
    algorithm: JwtHmacAlgorithm = JwtAlgorithm.HS512,
  ): ULayer[JwtAuthServiceLive] =
    ZLayer.succeed(JwtAuthServiceLive(secretKey, tokenTTL, algorithm))
}
