package example.auth.digest

import example.auth.digest.another._
import zio.Config.Secret
import zio._
import zio.http._

object DigestAuthenticationServer extends ZIOAppDefault {

  val userDatabase: Map[String, UserCredentials] =
    Map(
      "john"  -> UserCredentials("john", Secret("password123")),
      "jane"  -> UserCredentials("jane", Secret("secret456")),
      "admin" -> UserCredentials("admin", Secret("admin123")),
    )

  def getUserCredentials(username: String): Task[Option[UserCredentials]] =
    ZIO.succeed(userDatabase.get(username))

  val realm     = "Protected API"
  val algorithm = "MD5"

  def routes =
    Routes(
      Method.GET / Root ->
        Handler
          .fromResource("digest-auth-client.html")
          .orElse(
            handler { (_: Request) =>
              ZIO.fail(Response.internalServerError("Failed to load HTML file"))
            },
          ),

      // Protected profile route
      Method.GET / "profile" / "me" -> handler { (_: Request) =>
        ZIO.serviceWith[UserCredentials] { username =>
          Response.text(s"Hello $username! This is your profile.")
        }
      } @@ DigestAuthAspect(
        realm = "http-auth@example.org",
        getUserCredentials = getUserCredentials,
      ),

      // Protected admin route - only for admin user
      Method.GET / "admin" -> handler { (_: Request) =>
        ZIO.serviceWith[UserCredentials] { userCredentials =>
          if (userCredentials.username == "admin")
            Response.text(s"Welcome to admin panel, ${userCredentials.username}!")
          else
            Response.unauthorized(s"Access denied. User ${userCredentials.username} is not an admin.")
        }
      } @@ DigestAuthAspect(
        realm = "http-auth@example.org",
        getUserCredentials = getUserCredentials,
      ),

      // Public route (no authentication required)
      Method.GET / "public" -> handler { (_: Request) =>
        ZIO.succeed(Response.text("This is a public endpoint - no authentication required!"))
      },
    )

  override val run =
    Server.serve(routes).provide(Server.default, DigestHashService.live, NonceService.live, DigestAuthService.live)

}
