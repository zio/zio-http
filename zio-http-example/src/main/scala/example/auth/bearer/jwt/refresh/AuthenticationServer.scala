package example.auth.bearer.jwt.refresh

import example.auth.bearer.jwt.refresh.core.AuthMiddleware.jwtAuth
import example.auth.bearer.jwt.refresh.core._
import pdi.jwt.JwtAlgorithm
import zio.Config.Secret
import zio._
import zio.http._
import zio.json._

object AuthenticationServer extends ZIOAppDefault {
  def routes: Routes[UserService & JwtTokenService, Response] =
    Routes(
      // Serve the web client interface from resources
      Method.GET / Root ->
        Handler
          .fromResource("jwt-client-with-refresh-token.html")
//          .fromResource("jwt-client-with-refresh-token-simple.html")
          .orElse(
            Handler.internalServerError("Failed to load HTML file")
          ),

      // Protected route that requires authentication
      Method.GET / "profile" / "me" -> handler { (_: Request) =>
        ZIO.serviceWith[UserInfo](user =>
          Response.text(
            s"Welcome ${user.username}!" +
              s"\nHere is your profile:\nEmail: ${user.email}"
          )
        )
      } @@ jwtAuth(realm = "User Profile"),

      // Login route that returns both access and refresh tokens
      Method.POST / "login" ->
        handler { (request: Request) =>
          def extractFormField(form: Form, fieldName: String): ZIO[Any, Response, String] =
            ZIO
              .fromOption(form.get(fieldName).flatMap(_.stringValue))
              .orElseFail(Response.badRequest(s"Missing $fieldName"))

          val unauthorizedResponse =
            Response
              .unauthorized("Invalid username or password.")
              .addHeaders(Headers(Header.WWWAuthenticate.Bearer("User Login")))

          for {
            form <- request.body.asURLEncodedForm.orElseFail(Response.badRequest)
            username <- extractFormField(form, "username")
            password <- extractFormField(form, "password")
            userService <- ZIO.service[UserService]
            tokenService <- ZIO.service[JwtTokenService]
            user <- userService
              .validateCredentials(username, password)
              .orElseFail(unauthorizedResponse)
            tokens <- tokenService.issueTokens(username, user.email, user.roles)
            response = Response.json(tokens.toJson)
          } yield response
        },

      // Refresh token route
      Method.POST / "refresh" ->
        handler { (request: Request) =>
          for {
            form <- request.body.asURLEncodedForm.orElseFail(Response.badRequest("Expected form-encoded data"))
            refreshToken <- ZIO
              .fromOption(form.get("refreshToken").flatMap(_.stringValue))
              .orElseFail(Response.badRequest("Missing refreshToken"))
            tokenService <- ZIO.service[JwtTokenService]
            newTokens <- tokenService
              .refreshTokens(refreshToken)
              .orElseFail(Response.unauthorized("Invalid or expired refresh token"))
            response = Response.json(newTokens.toJson)
          } yield response
        },

      // Admin route to list all users
      Method.GET / "admin" / "users" ->
        Handler.fromZIO(ZIO.service[UserService]).flatMap { userService =>
          Handler.fromZIO {
            ZIO.serviceWithZIO[UserInfo] { info: UserInfo =>
              if (info.roles.contains("admin")) {
                userService.getUsers.map { users =>
                  val userList = users.map(u => s"${u.username} (${u.email}) - Roles: ${u.roles.mkString(", ")}").mkString("\n")
                  Response.text(s"User List:\n$userList")
                }
              } else {
                ZIO.fail(Response.forbidden(s"Access denied. User ${info.username} is not an admin."))
              }
            }
          } @@ jwtAuth(realm = "Admin Area")
        },

      // Public endpoint (no authentication required)
      Method.GET / "public" -> handler { (_: Request) =>
        ZIO.succeed(Response.text("This is a public endpoint - no authentication required!"))
      },

      // Logout route (revokes refresh token)
      Method.POST / "logout" ->
        handler { (request: Request) =>
          for {
            form <- request.body.asURLEncodedForm.orElseFail(Response.badRequest("Expected form-encoded data"))
            refreshToken <- ZIO
              .fromOption(form.get("refreshToken").flatMap(_.stringValue))
              .orElseFail(Response.badRequest("Missing refreshToken"))
            tokenService <- ZIO.service[JwtTokenService]
            _ <- tokenService.revokeRefreshToken(refreshToken)
          } yield Response.text("Logged out successfully")
        }
    ) @@ Middleware.debug

  override val run =
    System
      .envOrElse("JWT_SECRET_KEY", "my-secret")
      .map(Secret(_))
      .flatMap { secret =>
        val tokenService = JwtTokenService.live(
          secretKey = secret,
          accessTokenTTL = 10.seconds,
          refreshTokenTTL = 7.days,
          algorithm = JwtAlgorithm.HS512
        )
        Server.serve(routes).provide(
          Server.default,
          UserService.live,
          tokenService
        )
      }
}