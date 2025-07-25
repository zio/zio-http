package example.auth.bearer.session

import zio._
import zio.http._

import java.nio.charset.StandardCharsets
import scala.io.Source

/**
 * This is an example to demonstrate cookie-based authentication middleware with
 * a web client. The Server has both API routes and serves a web client
 * interface loaded from resources.
 *
 * File structure:
 *   - src/main/resources/auth-client.html (the web client)
 *
 * Routes:
 *   - GET / -> Web client interface
 *   - GET /login -> Login endpoint
 *   - GET /profile/me -> Protected route
 *   - GET /logout -> Logout endpoint
 */

/**
 * Authentication service that manages sessions and provides routes
 */
class AuthenticationService private (private val sessionStore: Ref[Map[String, String]]) {

  // Session cookie name
  val SESSION_COOKIE_NAME = "session_id"

  // Session timeout in seconds
  val SESSION_TIMEOUT = 300

  private def generateSessionId(): UIO[String] =
    ZIO.randomWith(_.nextUUID).map(_.toString)

  def createSession(username: String): UIO[String] = {
    for {
      sessionId <- generateSessionId()
      _         <- sessionStore.update(_ + (sessionId -> username))
    } yield sessionId
  }

  def getSession(sessionId: String): UIO[Option[String]] =
    sessionStore.get.map(_.get(sessionId))

  def removeSession(sessionId: String): UIO[Unit] =
    sessionStore.update(_ - sessionId)

  val cookieAuthWithContext: HandlerAspect[Any, String] =
    HandlerAspect.interceptIncomingHandler(Handler.fromFunctionZIO[Request] { request =>
      request.cookie(SESSION_COOKIE_NAME) match {
        case Some(cookie) =>
          getSession(cookie.content).flatMap {
            case Some(username) =>
              ZIO.succeed((request, username))
            case None           =>
              ZIO.fail(Response.unauthorized("Invalid or expired session!"))
          }
        case None         =>
          ZIO.fail(Response.unauthorized("No session cookie found!"))
      }
    })

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

  def routes: Routes[Any, Response] =
    Routes(
      // Serve the web client interface from resources
      Method.GET / Root -> handler { (_: Request) =>
        for {
          html <- loadHtmlFromResources("/auth-client.html").orDie
        } yield Response(
          status = Status.Ok,
          headers = Headers(Header.ContentType(MediaType.text.html)),
          body = Body.fromString(html),
        )
      },

      // A route that is accessible only via a valid session cookie
      Method.GET / "profile" / "me" -> handler { (_: Request) =>
        ZIO.serviceWith[String](name => Response.text(s"Welcome $name!"))
      } @@ cookieAuthWithContext,

      // A login route that is successful only if the password is the reverse of the username
      Method.POST / "login" ->
        handler { (request: Request) =>
          val form = request.body.asURLEncodedForm.orElseFail(Response.badRequest("Invalid form data"))
          for {
            username  <- form
              .map(_.get("username"))
              .flatMap(ff => ZIO.fromOption(ff).orElseFail(Response.badRequest("Missing username field!")))
              .flatMap(ff => ZIO.fromOption(ff.stringValue).orElseFail(Response.badRequest("Missing username value!")))
            password  <- form
              .map(_.get("password"))
              .flatMap(ff => ZIO.fromOption(ff).orElseFail(Response.badRequest("Missing password field!")))
              .flatMap(ff => ZIO.fromOption(ff.stringValue).orElseFail(Response.badRequest("Missing password value!")))
            sessionId <- createSession(username)
            sessionCookie = Cookie.Response(
              name = SESSION_COOKIE_NAME,
              content = sessionId,
              maxAge = Some(SESSION_TIMEOUT seconds),
              isHttpOnly = true,
              isSecure = false, // Set to true in production with HTTPS
              sameSite = Some(Cookie.SameSite.Strict),
            )
          } yield
            if (password.reverse.hashCode == username.hashCode) {
              Response
                .text(s"Login successful! Session created for $username")
                .addCookie(sessionCookie)
            } else
              Response.unauthorized("Invalid username or password.")
        },

      // A logout route that clears the session
      Method.GET / "logout" ->
        handler { (request: Request) =>
          request.cookie(SESSION_COOKIE_NAME) match {
            case Some(cookie) =>
              ZIO.service[String] *>
                removeSession(cookie.content) *>
                ZIO.succeed(
                  Response
                    .text("Logged out successfully!")
                    .addCookie(Cookie.clear(SESSION_COOKIE_NAME)),
                )
            case None         =>
              ZIO.succeed(Response.text("No active session found."))
          }
        } @@ cookieAuthWithContext,
    ) @@ Middleware.debug
}

object AuthenticationService {
  def make: UIO[AuthenticationService] =
    Ref.make(Map.empty[String, String]).map(new AuthenticationService(_))
}

object CookieAuthenticationServerWithResource extends ZIOAppDefault {

  override val run = {
    for {
      _ <- Console.printLine(" Starting Cookie Authentication Server with Resource Loading...")
      _ <- Console.printLine(" Web Client available at: http://localhost:8080")
      _ <- Console.printLine(" API endpoints:")
      _ <- Console.printLine("   - GET /login (with form data)")
      _ <- Console.printLine("   - GET /profile/me (protected)")
      _ <- Console.printLine("   - GET /logout (protected)")
    } yield ()
  }.flatMap(_ => AuthenticationService.make)
    .flatMap(authService => Server.serve(authService.routes).provide(Server.default))
}
