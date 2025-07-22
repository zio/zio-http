package example.auth.digest

import example.auth.digest.core.DigestAuthHandlerAspect.UserCredentials
import example.auth.digest.core.QualityOfProtection.AuthInt
import example.auth.digest.core._
import zio.Config.Secret
import zio._
import zio.http._
import zio.schema._
import zio.schema.codec.JsonCodec.schemaBasedBinaryCodec

object DigestAuthenticationServer extends ZIOAppDefault {

  val userDatabase: Map[String, UserCredentials] =
    Map(
      "john"  -> UserCredentials("john", Secret("password123"), "john@example.com"),
      "jane"  -> UserCredentials("jane", Secret("secret456"), "jane@example.com"),
      "admin" -> UserCredentials("admin", Secret("admin123"), "admin@company.com"),
    )

  def getUserCredentials(username: String): Task[Option[UserCredentials]] =
    ZIO.succeed(userDatabase.get(username))

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
        ZIO.serviceWith[UserCredentials] { userCredentials =>
          Response.text(s"Hello ${userCredentials.username}! This is your profile. Email: ${userCredentials.email}")
        }
      } @@ DigestAuthHandlerAspect(
        realm = "http-auth@example.org",
        getUserCredentials = getUserCredentials,
      ),

      // New protected route for updating email
      Method.PUT / "profile" / "email" -> handler { (req: Request) =>
        for {
          userCredentials <- ZIO.service[UserCredentials]
          updateRequest   <- req.body
            .to[UpdateEmailRequest]
            .mapError(error => Response.badRequest(s"Invalid JSON: $error"))
          // In a real application, you would persist this change to a database
          _ <- Console.printLine(s"User ${userCredentials.username} updated email to: ${updateRequest.email}").orDie
        } yield Response.text(
          s"Email updated successfully for user ${userCredentials.username}! New email: ${updateRequest.email}",
        )
      } @@ DigestAuthHandlerAspect(
        realm = "http-auth@example.org",
        getUserCredentials = getUserCredentials,
        qop = List(AuthInt),
      ),

      // Protected admin route - only for admin user
      Method.GET / "admin" -> handler { (_: Request) =>
        ZIO.serviceWith[UserCredentials] { userCredentials =>
          if (userCredentials.username == "admin")
            Response.text(s"Welcome to admin panel, ${userCredentials.username}! Admin email: ${userCredentials.email}")
          else
            Response.unauthorized(s"Access denied. User ${userCredentials.username} is not an admin.")
        }
      } @@ DigestAuthHandlerAspect(
        realm = "http-auth@example.org",
        getUserCredentials = getUserCredentials,
      ),

      // Public route (no authentication required)
      Method.GET / "public" -> handler { (_: Request) =>
        ZIO.succeed(Response.text("This is a public endpoint - no authentication required!"))
      },
    )

  override val run =
    Server.serve(routes).provide(Server.default, HashService.live, NonceService.live, DigestAuthService.live)

}

case class UpdateEmailRequest(email: String)

object UpdateEmailRequest {
  implicit val schema: Schema[UpdateEmailRequest] = DeriveSchema.gen
}
