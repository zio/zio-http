package example.auth.bearer.jwt.core

import java.time.Clock

import zio.Config.Secret
import zio._

import pdi.jwt._
import pdi.jwt.algorithms.JwtHmacAlgorithm

trait JwtTokenService {
  def issue(username: String, email: String, roles: Set[String]): UIO[String]
  def verify(token: String): Task[UserInfo]
}

case class JwtAuthServiceLive(
  secretKey: Secret,
  tokenTTL: Duration,
  algorithm: JwtHmacAlgorithm,
) extends JwtTokenService {
  implicit val clock: Clock = Clock.systemUTC

  override def issue(username: String, email: String, roles: Set[String]): UIO[String] =
    ZIO.succeed {
      Jwt.encode(
        claim = JwtClaim(subject = Some(username)).issuedNow
          .expiresIn(tokenTTL.toSeconds)
          .++(("roles", roles))
          .++(("email", email)),
        key = secretKey.stringValue,
        algorithm = algorithm,
      )
    }

  override def verify(token: String): ZIO[Any, Throwable, UserInfo] =
    ZIO
      .fromTry(Jwt.decode(token, secretKey.stringValue, Seq(algorithm)))
      .filterOrFail(_.isValid)(new Exception("Token expired"))
      .map(_.toJson)
      .map(UserInfo.codec.decodeJson(_).toOption)
      .someOrFail(new Exception("Invalid token"))
}

object JwtTokenService {
  def live(
    secretKey: Secret,
    tokenTTL: Duration = 15.minutes,
    algorithm: JwtHmacAlgorithm = JwtAlgorithm.HS512,
  ): ULayer[JwtAuthServiceLive] =
    ZLayer.succeed(JwtAuthServiceLive(secretKey, tokenTTL, algorithm))
}
