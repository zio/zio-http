package example.auth.bearer.jwt.refresh.core

import pdi.jwt._
import pdi.jwt.algorithms.JwtHmacAlgorithm
import zio.Config.Secret
import zio._

import java.security.SecureRandom
import java.time.Clock

trait JwtTokenService {
  def issueTokens(username: String, email: String, roles: Set[String]): UIO[TokenResponse]
  def verifyAccessToken(token: String): Task[UserInfo]
  def refreshTokens(refreshToken: String): Task[TokenResponse]
  def revokeRefreshToken(refreshToken: String): UIO[Unit]
}

case class RefreshTokenData(username: String, email: String, roles: Set[String], expiresAt: Long)

case class JwtTokenServiceLive(
  secretKey: Secret,
  accessTokenTTL: Duration,
  refreshTokenTTL: Duration,
  algorithm: JwtHmacAlgorithm,
  refreshTokenStore: Ref[Map[String, RefreshTokenData]],
) extends JwtTokenService {
  implicit val clock: Clock = Clock.systemUTC

  override def issueTokens(username: String, email: String, roles: Set[String]): UIO[TokenResponse] =
    for {
      accessToken  <- ZIO.succeed(generateAccessToken(username, email, roles))
      refreshToken <- generateRefreshToken(username, email, roles)
    } yield TokenResponse(
      accessToken = accessToken,
      refreshToken = refreshToken,
      tokenType = "Bearer",
      expiresIn = accessTokenTTL.toSeconds.toInt,
    )

  override def verifyAccessToken(token: String): Task[UserInfo] =
    ZIO
      .fromTry(Jwt.decode(token, secretKey.stringValue, Seq(algorithm)))
      .filterOrFail(_.isValid)(new Exception("Token expired"))
      .map(_.toJson)
      .map(UserInfo.codec.decodeJson(_).toOption)
      .someOrFail(new Exception("Invalid token"))

  override def refreshTokens(refreshToken: String): Task[TokenResponse] =
    for {
      store     <- refreshTokenStore.get
      tokenData <- ZIO
        .fromOption(store.get(refreshToken))
        .orElseFail(new Exception("Invalid refresh token"))
      _         <- ZIO.when(tokenData.expiresAt < java.lang.System.currentTimeMillis()) {
        ZIO.fail(new Exception("Refresh token expired"))
      }
      // Revoke old refresh token and issue new tokens
      _         <- refreshTokenStore.update(_ - refreshToken)
      newTokens <- issueTokens(tokenData.username, tokenData.email, tokenData.roles)
    } yield newTokens

  override def revokeRefreshToken(refreshToken: String): UIO[Unit] =
    refreshTokenStore.update(_ - refreshToken)

  private def generateAccessToken(username: String, email: String, roles: Set[String]): String =
    Jwt.encode(
      claim = JwtClaim(subject = Some(username)).issuedNow
        .expiresIn(accessTokenTTL.toSeconds)
        .++(("roles", roles))
        .++(("email", email)),
      key = secretKey.stringValue,
      algorithm = algorithm,
    )

  private def generateRefreshToken(username: String, email: String, roles: Set[String]): UIO[String] =
    for {
      tokenId <- generateSecureToken
      expiresAt = java.lang.System.currentTimeMillis() + refreshTokenTTL.toMillis
      _ <- refreshTokenStore.update(
        _.updated(tokenId, RefreshTokenData(username, email, roles, expiresAt)),
      )
    } yield tokenId

  private def generateSecureToken: UIO[String] =
    ZIO.succeed {
      val random = new SecureRandom()
      val bytes  = new Array[Byte](32)
      random.nextBytes(bytes)
      java.util.Base64.getUrlEncoder.withoutPadding.encodeToString(bytes)
    }

}

object JwtTokenService {
  def live(
    secretKey: Secret,
    accessTokenTTL: Duration = 5.minutes,
    refreshTokenTTL: Duration = 7.days,
    algorithm: JwtHmacAlgorithm = JwtAlgorithm.HS512,
  ): ZLayer[Any, Nothing, JwtTokenService] =
    ZLayer.fromZIO {
      for {
        store <- Ref.make(Map.empty[String, RefreshTokenData])
      } yield JwtTokenServiceLive(secretKey, accessTokenTTL, refreshTokenTTL, algorithm, store)
    }
}
