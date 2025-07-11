package example.auth.bearer.jwt.refresh

import pdi.jwt.{Jwt, JwtAlgorithm, JwtClaim}
import zio._
import zio.http._
import zio.json._

import java.time.Clock
import java.util.UUID
import scala.util.Try

/**
 * This is an example to demonstrate bearer Authentication middleware with refresh token support.
 * The Server has 3 routes:
 * 1. Login route - returns both access and refresh tokens
 * 2. Refresh route - exchanges refresh token for new access token
 * 3. Protected route - requires valid access token
 */
object AuthenticationServer extends ZIOAppDefault {
  implicit val clock: Clock = Clock.systemUTC

  // Secret Authentication key
  val SECRET_KEY = "secretKey"

  // Token response case class
  case class TokenResponse(accessToken: String, refreshToken: String, tokenType: String = "Bearer", expiresIn: Int = 300)

  // Refresh token request case class
  case class RefreshTokenRequest(refreshToken: String)

  // JSON codecs
  implicit val tokenResponseEncoder: JsonEncoder[TokenResponse] = DeriveJsonEncoder.gen[TokenResponse]
  implicit val refreshTokenRequestDecoder: JsonDecoder[RefreshTokenRequest] = DeriveJsonDecoder.gen[RefreshTokenRequest]

  // In-memory storage for refresh tokens (in production, use a proper database)
  private val refreshTokenStore: Ref[Map[String, String]] = Unsafe.unsafe { implicit unsafe =>
    Runtime.default.unsafe.run(Ref.make(Map.empty[String, String])).getOrThrow()
  }

  def jwtEncode(username: String, key: String, expirationSeconds: Long = 300): String =
    Jwt.encode(JwtClaim(subject = Some(username)).issuedNow.expiresIn(expirationSeconds), key, JwtAlgorithm.HS512)

  def jwtDecode(token: String, key: String): Try[JwtClaim] =
    Jwt.decode(token, key, Seq(JwtAlgorithm.HS512))

  def generateRefreshToken(): String = UUID.randomUUID().toString

  val bearerAuthWithContext: HandlerAspect[Any, String] =
    HandlerAspect.interceptIncomingHandler(Handler.fromFunctionZIO[Request] { request =>
      request.header(Header.Authorization) match {
        case Some(Header.Authorization.Bearer(token)) =>
          ZIO
            .fromTry(jwtDecode(token.value.asString, SECRET_KEY))
            .orElseFail(Response.unauthorized("Invalid or expired token!"))
            .flatMap(claim => ZIO.fromOption(claim.subject).orElseFail(Response.badRequest("Missing subject claim!")))
            .map(u => (request, u))

        case _ => ZIO.fail(Response.unauthorized.addHeaders(Headers(Header.WWWAuthenticate.Bearer(realm = "Access"))))
      }
    })

  def routes: Routes[Any, Response] =
    Routes(
      // A route that is accessible only via a jwt token
      Method.GET / "profile" / "me" -> handler { (_: Request) =>
        ZIO.serviceWith[String](name => Response.text(s"Welcome $name!"))
      } @@ bearerAuthWithContext,

      // A login route that returns both access and refresh tokens
      Method.POST / "login" ->
        handler { (request: Request) =>
          for {
            body <- request.body.asString.orDie
            loginData <- ZIO.fromEither(body.fromJson[Map[String, String]]).orElseFail(Response.badRequest("Invalid JSON"))
            username <- ZIO.fromOption(loginData.get("username")).orElseFail(Response.badRequest("Missing username"))
            password <- ZIO.fromOption(loginData.get("password")).orElseFail(Response.badRequest("Missing password"))
            response <-
              if (password.reverse.hashCode == username.hashCode) {
                val accessToken = jwtEncode(username, SECRET_KEY, 300) // 5 minutes
                val refreshToken = generateRefreshToken()
                for {
                  _ <- refreshTokenStore.update(_.updated(refreshToken, username))
                  tokenResponse = TokenResponse(accessToken, refreshToken)
                } yield Response.json(tokenResponse.toJson)
              } else {
                ZIO.succeed(Response.unauthorized("Invalid username or password."))
              }
          } yield response
        },

      // Refresh token route
      Method.POST / "refresh" ->
        handler { (request: Request) =>
          for {
            body <- request.body.asString.orDie
            refreshRequest <- ZIO.fromEither(body.fromJson[RefreshTokenRequest]).orElseFail(Response.badRequest("Invalid JSON"))
            tokenStore <- refreshTokenStore.get
            username <- ZIO.fromOption(tokenStore.get(refreshRequest.refreshToken)).orElseFail(Response.unauthorized("Invalid refresh token"))
            newAccessToken = jwtEncode(username, SECRET_KEY, 300)
            newRefreshToken = generateRefreshToken()
            _ <- refreshTokenStore.update(store => store.removed(refreshRequest.refreshToken).updated(newRefreshToken, username))
            tokenResponse = TokenResponse(newAccessToken, newRefreshToken)
          } yield Response.json(tokenResponse.toJson)
        },

      // Legacy GET login route for backward compatibility
      Method.GET / "login" ->
        handler { (request: Request) =>
          val form = request.body.asMultipartForm.orElseFail(Response.badRequest)
          for {
            username <- form
              .map(_.get("username"))
              .flatMap(ff => ZIO.fromOption(ff).orElseFail(Response.badRequest("Missing username field!")))
              .flatMap(ff => ZIO.fromOption(ff.stringValue).orElseFail(Response.badRequest("Missing username value!")))
            password <- form
              .map(_.get("password"))
              .flatMap(ff => ZIO.fromOption(ff).orElseFail(Response.badRequest("Missing password field!")))
              .flatMap(ff => ZIO.fromOption(ff.stringValue).orElseFail(Response.badRequest("Missing password value!")))
          } yield
            if (password.reverse.hashCode == username.hashCode)
              Response.text(jwtEncode(username, SECRET_KEY))
            else
              Response.unauthorized("Invalid username or password.")
        },
    ) @@ Middleware.debug

  override val run = Server.serve(routes).provide(Server.default)
}