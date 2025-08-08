package example.auth.digest

import example.auth.digest.core.QualityOfProtection.AuthInt
import example.auth.digest.core._
import zio._
import zio.http._
import zio.schema._
import zio.schema.codec.JsonCodec.schemaBasedBinaryCodec

object DigestAuthenticationServer extends ZIOAppDefault {
  def routes: Routes[DigestAuthService & UserService, Nothing] =
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
            user <- ZIO.service[User]
          } yield Response.text(
            s"Hello ${user.username}! This is your profile: \n Username: ${user.username} \n Email: ${user.email}",
          )

        }
      } @@ DigestAuthHandlerAspect(realm = "User Profile"),

      // Protected route for updating email
      Method.PUT / "profile" / "email" ->
        Handler.fromZIO(ZIO.service[UserService]).flatMap { userService =>
          handler { (req: Request) =>
            for {
              user          <- ZIO.service[User]
              updateRequest <- req.body
                .to[UpdateEmailRequest]
                .mapError(error => Response.badRequest(s"Invalid JSON (UpdateEmailRequest): $error"))
              _             <- userService
                .updateEmail(user.username, updateRequest.email)
                .logError(s"Failed to update email for user ${user.username}")
                .mapError(_ => Response.internalServerError(s"Failed to update email!"))
            } yield Response.text(
              s"Email updated successfully for user ${user.username}! New email: ${updateRequest.email}",
            )
          } @@ DigestAuthHandlerAspect(realm = "User Profile", qop = List(AuthInt))
        },

      // Protected admin route - only for admin user
      Method.GET / "admin" -> handler { (_: Request) =>
        ZIO.serviceWith[User] { user =>
          if (user.username == "admin")
            Response.text(s"Welcome to admin panel, ${user.username}! Admin email: ${user.email}")
          else
            Response.unauthorized(s"Access denied. User ${user.username} is not an admin.")
        }
      } @@ DigestAuthHandlerAspect(realm = "Admin Area"),

      // Public route (no authentication required)
      Method.GET / "public" -> handler { (_: Request) =>
        ZIO.succeed(Response.text("This is a public endpoint - no authentication required!"))
      },
    )

  override val run =
    Server
      .serve(routes)
      .provide(Server.default, NonceService.live, DigestAuthService.live, UserService.live, DigestService.live)

}

case class UpdateEmailRequest(email: String)

object UpdateEmailRequest {
  implicit val schema: Schema[UpdateEmailRequest] = DeriveSchema.gen
}
