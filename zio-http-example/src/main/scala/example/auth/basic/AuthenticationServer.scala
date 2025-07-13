package example.auth.basic

import zio.Config.Secret
import zio._
import zio.http._

import java.nio.charset.StandardCharsets
import scala.io.Source

/**
 * This is an example to demonstrate basic Authentication middleware that passes
 * the authenticated username to the handler. The server has routes that can
 * access the authenticated user's information through the context.
 */
object AuthenticationServer extends ZIOAppDefault {

  // Sample user database
  case class User(username: String, password: Secret, email: String, role: String)

  val users = Map(
    "john"  -> User("john", Secret("secret123"), "john@example.com", "user"),
    "jane"  -> User("jane", Secret("password456"), "jane@example.com", "user"),
    "admin" -> User("admin", Secret("admin123"), "admin@example.com", "admin"),
  )

  // Custom basic auth that passes the full User object to the handler
  val basicAuthWithUserContext: HandlerAspect[Any, User] =
    HandlerAspect.interceptIncomingHandler(Handler.fromFunctionZIO[Request] { request =>
      request.header(Header.Authorization) match {
        case Some(Header.Authorization.Basic(username, password)) =>
          users.get(username) match {
            case Some(user) if user.password == password =>
              ZIO.succeed((request, user))
            case _                                       =>
              ZIO.fail(
                Response
                  .unauthorized("Invalid username or password")
                  .addHeaders(Headers(Header.WWWAuthenticate.Basic(realm = Some("Access")))),
              )
          }
        case _                                                    =>
          ZIO.fail(
            Response
              .unauthorized("Authentication required")
              .addHeaders(Headers(Header.WWWAuthenticate.Basic(realm = Some("Access")))),
          )
      }
    })

  def routes: Routes[Any, Response] =
    Routes(
      // Serve the web client interface from resources
      Method.GET / Root -> handler { (_: Request) =>
        for {
          html <- loadHtmlFromResources("/basic-auth-client.html").orDie
        } yield Response(
          status = Status.Ok,
          headers = Headers(Header.ContentType(MediaType.text.html)),
          body = Body.fromString(html),
        )
      },

      // Public route - no authentication required
      Method.GET / "public" -> handler { (_: Request) =>
        ZIO.succeed(Response.text("This is a public endpoint accessible to everyone"))
      },

      // Route that uses the full User object
      Method.GET / "profile" / "me" -> handler { (_: Request) =>
        ZIO.serviceWith[User](user =>
          Response.text(s"Welcome ${user.username}!\nEmail: ${user.email}\nRole: ${user.role}"),
        )
      } @@ basicAuthWithUserContext,

      // Admin-only route
      Method.GET / "admin" / "users" -> handler { (_: Request) =>
        ZIO.serviceWith[User] { user =>
          if (user.role != "admin")
            Response.forbidden("Admin access required")
          else {
            // List all users for admin
            val userList = users.values.map(u => s"${u.username} (${u.email}) - Role: ${u.role}").mkString("\n")
            Response.text(s"User List (accessed by ${user.username}):\n$userList")
          }
        }
      } @@ basicAuthWithUserContext,
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
