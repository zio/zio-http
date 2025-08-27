package example.auth.bearer.jwt.symmetric

import example.auth.bearer.jwt.symmetric.core.AuthMiddleware.{Claim, jwtAuth, jwtAuthClaim}
import example.auth.bearer.jwt.symmetric.core._
import pdi.jwt.JwtAlgorithm
import zio.Config.Secret
import zio._
import zio.http._

object AuthenticationServer extends ZIOAppDefault {
  def routes: Routes[JwtTokenService & UserService & JwtTokenServiceClaim, Response] =
    Routes(
      // Serve the web client interface from resources
      Method.GET / Root ->
        Handler
          .fromResource("symmetric-jwt-client.html")
          .orElse(
            Handler.internalServerError("Failed to load HTML file"),
          ),

      // A route that is accessible only via a jwt token
      Method.GET / "profile" / "me" -> handler { (_: Request) =>
        ZIO.serviceWith[User](user =>
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
            tokenService <- ZIO.service[JwtTokenServiceClaim]
            user         <- ZIO
              .serviceWithZIO[UserService](_.getUser(username))
              .orElseFail(unauthorizedResponse)
            response     <-
              if (user.password == Secret(password))
                tokenService.issue(username, UserRole.Admin, user.email).map(Response.text(_))
              else
                ZIO.fail(unauthorizedResponse)
          } yield response
        },
      Method.GET / "admin" / "users" ->
        Handler.fromZIO(ZIO.service[UserService]).flatMap { userService =>
          Handler.fromZIO {
            ZIO.serviceWithZIO[Claim] { claim: Claim =>
              if (claim.role == UserRole.Admin) {
                userService.getUsers.map { users =>
                  val userList = users.map(u => s"${u.username} (${u.email}) - Role: ${u.role}").mkString("\n")
                  Response.text(s"User List:\n$userList")
                }
              } else {
                ZIO.fail(
                  Response.unauthorized(s"Access denied. User ${claim.username} is not an admin."),
                )
              }
            }
          } @@ jwtAuthClaim(realm = "Admin Area")
        },
//      Method.GET / "admin"  -> handler { (_: Request) =>
//        ZIO.serviceWith[User] { user =>
//          if (user.role == UserRole.Admin)
//            Response.text(s"Welcome to admin panel, ${user.username}! Admin email: ${user.email}")
//          else
//            Response.unauthorized(s"Access denied. User ${user.username} is not an admin.")
//        }
//      } @@ jwtAuth(realm = "Admin Area"),
      Method.GET / "public"          -> handler { (_: Request) =>
        ZIO.succeed(Response.text("This is a public endpoint - no authentication required!"))
      },
    ) @@ Middleware.debug

  override val run =
    System
      .envOrElse("JWT_SECRET_KEY", "my-secret")
      .map(Secret(_))
      .map { secret =>
        val tokenService      = JwtTokenService.live(secret, tokenTTL = 30.minutes, algorithm = JwtAlgorithm.HS512);
        val tokenServiceClaim =
          JwtTokenServiceClaim.live(secret, tokenTTL = 30.minutes, algorithm = JwtAlgorithm.HS512);
        (secret, tokenService, tokenServiceClaim)
      }
      .flatMap { case (_, tokenService, tokenServiceClaim) =>
        Server.serve(routes).provide(Server.default, tokenService, UserService.live, tokenServiceClaim)
      }

}
