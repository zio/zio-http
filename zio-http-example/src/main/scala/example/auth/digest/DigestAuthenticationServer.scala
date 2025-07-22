package example.auth.digest

import example.auth.digest.core.DigestAuthHandlerAspect.{UserCredentials, Username}
import example.auth.digest.core.QualityOfProtection.AuthInt
import example.auth.digest.core._
import zio.Config.Secret
import zio._
import zio.http._
import zio.schema._
import zio.schema.codec.JsonCodec.schemaBasedBinaryCodec

object DigestAuthenticationServer extends ZIOAppDefault {

  def extractUser(request: Request): Option[String] = {
    request.header(Header.Authorization) match {
      case Some(authHeader: Header.Authorization.Digest) => Some(authHeader.username)
      case _                                             => None
    }
  }

  case class User(username: String, password: Secret, email: String)

  val userDatabase: Map[String, User] =
    Map(
      "john"  -> User("john", Secret("password123"), "john@example.com"),
      "jane"  -> User("jane", Secret("secret456"), "jane@example.com"),
      "admin" -> User("admin", Secret("admin123"), "admin@company.com"),
    )

  def getUserCredentials(username: String): Task[Option[UserCredentials]] =
    ZIO.succeed(userDatabase.get(username).map(user => UserCredentials(Username(user.username), user.password)))

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
        {
          for {
            username <- ZIO.service[Username]
            user = userDatabase(username.value)
          } yield Response.text(s"Hello ${user.username}! This is your profile. Email: ${user.email}")

        }
      } @@ DigestAuthHandlerAspect(
        realm = "http-auth@example.org",
        getUserCredentials = getUserCredentials,
      ),

      // New protected route for updating email
      Method.PUT / "profile" / "email" -> handler { (req: Request) =>
        for {
          userCredentials <- ZIO.service[Username]
          user = userDatabase(userCredentials.value)
          updateRequest <- req.body
            .to[UpdateEmailRequest]
            .mapError(error => Response.badRequest(s"Invalid JSON: $error"))
          // In a real application, you would persist this change to a database
          _             <- Console.printLine(s"User ${user.username} updated email to: ${user.email}").orDie
        } yield Response.text(
          s"Email updated successfully for user ${user.username}! New email: ${updateRequest.email}",
        )
      } @@ DigestAuthHandlerAspect(
        realm = "http-auth@example.org",
        getUserCredentials = getUserCredentials,
        qop = List(AuthInt),
      ),

      // Protected admin route - only for admin user
      Method.GET / "admin" -> handler { (_: Request) =>
        ZIO.serviceWith[Username] { userCredentials =>
          val user = userDatabase(userCredentials.value)
          if (userCredentials.value == "admin")
            Response.text(s"Welcome to admin panel, ${user.username}! Admin email: ${user.email}")
          else
            Response.unauthorized(s"Access denied. User ${user.username} is not an admin.")
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
