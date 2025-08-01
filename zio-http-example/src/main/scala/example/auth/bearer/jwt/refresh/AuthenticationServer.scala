package example.auth.bearer.jwt.refresh

import pdi.jwt.{Jwt, JwtAlgorithm, JwtClaim}
import zio._
import zio.http._
import zio.json._

import java.nio.charset.StandardCharsets
import java.time.Clock
import java.util.UUID
import scala.io.Source
import scala.util.Try

/**
 * This is an example to demonstrate bearer Authentication middleware with refresh token support.
 * The Server has 3 routes:
 * 1. Login route - accepts form-encoded data and returns both access and refresh tokens
 * 2. Refresh route - accepts form-encoded data and exchanges refresh token for new access token
 * 3. Protected route - requires valid access token
 */
object AuthenticationServer extends ZIOAppDefault {
  implicit val clock: Clock = Clock.systemUTC

  // Secret Authentication key
  val SECRET_KEY = "secretKey"

  // Token response case class
  case class TokenResponse(accessToken: String, refreshToken: String, tokenType: String = "Bearer", expiresIn: Int = 300)

  // JSON codecs
  implicit val tokenResponseEncoder: JsonEncoder[TokenResponse] = DeriveJsonEncoder.gen[TokenResponse]

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

      // Serve the web client interface from resources
      Method.GET / Root -> handler { (_: Request) =>
        for {
          html <- loadHtmlFromResources("/jwt-client-with-refresh-token.html").orDie
        } yield Response(
          status = Status.Ok,
          headers = Headers(Header.ContentType(MediaType.text.html)),
          body = Body.fromString(html),
        )
      },

      // A route that is accessible only via a jwt token
      Method.GET / "profile" / "me" -> handler { (_: Request) =>
        ZIO.serviceWith[String](name => Response.text(s"Welcome $name!"))
      } @@ bearerAuthWithContext,

      // A login route that accepts form-encoded data and returns both access and refresh tokens
      Method.POST / "login" ->
        handler { (request: Request) =>
          for {
            form <- request.body.asURLEncodedForm.orElseFail(Response.badRequest("Expected form-encoded data"))
            username <- ZIO.fromOption(form.get("username").flatMap(_.stringValue)).orElseFail(Response.badRequest("Missing username"))
            password <- ZIO.fromOption(form.get("password").flatMap(_.stringValue)).orElseFail(Response.badRequest("Missing password"))
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

      // Refresh token route (now accepts form-encoded data)
      Method.POST / "refresh" ->
        handler { (request: Request) =>
          for {
            form <- request.body.asURLEncodedForm.orElseFail(Response.badRequest("Expected form-encoded data"))
            refreshToken <- ZIO.fromOption(form.get("refreshToken").flatMap(_.stringValue)).orElseFail(Response.badRequest("Missing refreshToken"))
            tokenStore <- refreshTokenStore.get
            username <- ZIO.fromOption(tokenStore.get(refreshToken)).orElseFail(Response.unauthorized("Invalid refresh token"))
            newAccessToken = jwtEncode(username, SECRET_KEY, 300)
            newRefreshToken = generateRefreshToken()
            _ <- refreshTokenStore.update(store => store.removed(refreshToken).updated(newRefreshToken, username))
            tokenResponse = TokenResponse(newAccessToken, newRefreshToken)
          } yield Response.json(tokenResponse.toJson)
        },

    ) @@ Middleware.debug

  override val run = Server.serve(routes).provide(Server.default)


  /**
   * Loads HTML content from the resources directory
   */
  def loadHtmlFromResources(resourcePath: String): ZIO[Any, Throwable, String] = {
    ZIO.attempt {
      val inputStream = getClass.getResourceAsStream(resourcePath)
      if (inputStream == null) throw new RuntimeException(s"Resource not found: $resourcePath")

      val source = Source.fromInputStream(inputStream, StandardCharsets.UTF_8.name())
      try source.mkString
      finally {
        source.close()
        inputStream.close()
      }
    }
  }

}
