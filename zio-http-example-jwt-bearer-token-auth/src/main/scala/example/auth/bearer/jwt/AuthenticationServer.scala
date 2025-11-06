package example.auth.bearer.jwt

import zio.Config.Secret
import zio._

import zio.http._

import example.auth.bearer.jwt.core.AuthMiddleware.jwtAuth
import example.auth.bearer.jwt.core._
import pdi.jwt.JwtAlgorithm

object AuthenticationServer extends ZIOAppDefault {
  def routes: Routes[UserService & JwtTokenService, Response] =
    Routes(
      // Serve the web client interface from resources
      Method.GET / Root ->
        Handler
          .fromResource("jwt-bearer-token-auth-client.html")
//          .fromResource("jwt-bearer-token-auth-client-simple.html")
          .orElse(
            Handler.internalServerError("Failed to load HTML file"),
          ),

      // A route that is accessible only via a jwt token
      Method.GET / "profile" / "me" -> handler { (_: Request) =>
        ZIO.serviceWith[UserInfo](user =>
          Response.text(
            s"Welcome ${user.username}!" +
              s"\nHere is your profile:\nEmail: ${user.email}",
          ),
        )
      } @@ jwtAuth(realm = "User Profile"),

      // A login route that is successful only if the password is the reverse of the username
      Method.POST / "login"          ->
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
            form         <- request.body.asURLEncodedForm.orElseFail(Response.badRequest)
            username     <- extractFormField(form, "username")
            password     <- extractFormField(form, "password")
            tokenService <- ZIO.service[JwtTokenService]
            user         <- ZIO
              .serviceWithZIO[UserService](_.getUser(username))
              .orElseFail(unauthorizedResponse)
            response     <-
              if (user.password == Secret(password))
                tokenService.issue(username, user.email, user.roles).map(Response.text(_))
              else
                ZIO.fail(unauthorizedResponse)
          } yield response
        },
      Method.GET / "admin" / "users" ->
        Handler.fromZIO(ZIO.service[UserService]).flatMap { userService =>
          Handler.fromZIO {
            ZIO.serviceWithZIO[UserInfo] { info =>
              if (info.roles.contains("admin")) userService.getUsers.map { users =>
                val userList = users.map(u => s"${u.username} (${u.email}) - Role: ${u.roles}").mkString("\n")
                Response.text(s"User List:\n$userList")
              }
              else
                ZIO.fail(Response.unauthorized(s"Access denied. User ${info.username} is not an admin."))
            }
          } @@ jwtAuth(realm = "Admin Area")
        },
      Method.GET / "public"          -> handler { (_: Request) =>
        ZIO.succeed(Response.text("This is a public endpoint - no authentication required!"))
      },
    ) @@ Middleware.debug

  override val run =
    System
      .envOrElse("JWT_SECRET_KEY", "my-secret")
      .map(Secret(_))
      .flatMap { secret =>
        val tokenService =
          JwtTokenService.live(secret, tokenTTL = 30.minutes, algorithm = JwtAlgorithm.HS512);
        Server.serve(routes).provide(Server.default, UserService.live, tokenService)
      }

}
