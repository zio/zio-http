package example.auth.digest

import DigestAuthentication.{generateNonce, validateDigest}
import zio._
import zio.http._

object DigestAuthenticationServer extends ZIOAppDefault {

  // Simple in-memory user store (username -> password)
  val users = Map(
    "john"  -> "password123",
    "jane"  -> "secret456",
    "admin" -> "admin123",
  )

  val realm     = "Protected API"
  val algorithm = "MD5"

  // Digest authentication middleware
  val digestAuthMiddleware: HandlerAspect[Any, String] =
    HandlerAspect.interceptIncomingHandler(Handler.fromFunctionZIO[Request] { request =>
      request.header(Header.Authorization) match {
        case Some(header: Header.Authorization.Digest) =>
          // Check if user exists and validate digest
          users.get(header.username) match {
            case Some(password)
                if validateDigest(
                  header.response,
                  header.username,
                  header.realm,
                  header.uri,
                  header.algorithm,
                  header.qop,
                  header.cnonce,
                  header.nonce,
                  header.nc,
                  header.userhash,
                  password,
                  request.method,
                ) =>
              // The server expects the next nc to be higher
              ZIO.succeed((request, header.username))
            case _ =>
              ZIO.fail(
                Response.unauthorized.addHeader(
                  Header.WWWAuthenticate.Digest(
                    realm = Some(realm),
                    nonce = Some(generateNonce()),
                    algorithm = Some(algorithm),
                    qop = Some("auth"),
                  ),
                ),
              )
          }

        case _ =>
          val nonce = generateNonce()
          ZIO.fail(
            Response.unauthorized.addHeader(
              Header.WWWAuthenticate.Digest(
                realm = Some(realm),
                nonce = Some(nonce),
                algorithm = Some(algorithm),
                qop = Some("auth"),
              ),
            ),
          )
      }
    })

  def routes: Routes[Any, Response] =
    Routes(
      // Protected profile route
      Method.GET / "profile" / "me" -> handler { (_: Request) =>
        ZIO.serviceWith[String] { username =>
          Response.text(s"Hello $username! This is your profile.")
        }
      } @@ digestAuthMiddleware,

      // Protected admin route - only for admin user
      Method.GET / "admin" -> handler { (_: Request) =>
        ZIO.serviceWith[String] { username =>
          if (username == "admin")
            Response.text(s"Welcome to admin panel, $username!")
          else
            Response.unauthorized(s"Access denied. User $username is not an admin.")
        }
      } @@ digestAuthMiddleware,

      // Public route (no authentication required)
      Method.GET / "public" -> handler { (_: Request) =>
        ZIO.succeed(Response.text("This is a public endpoint - no authentication required!"))
      },
    )

  override val run =
    Server.serve(routes).provide(Server.default)
}
