package example.auth.session.cookie

import example.auth.session.cookie.core.CookieAuthMiddleware.{SESSION_COOKIE_NAME, cookieAuth}
import example.auth.session.cookie.core.{SessionService, UserService}
import zio.Config.Secret
import zio._
import zio.http._

/**
 * This is an example to demonstrate cookie-based authentication middleware with
 * a web client. The Server has both API routes and serves a web client
 * interface loaded from resources.
 *
 * Routes:
 *   - GET / -> Web client interface
 *   - GET /login -> Login endpoint
 *   - GET /profile/me -> Protected route
 *   - GET /logout -> Logout endpoint
 */
object CookieAuthenticationServer extends ZIOAppDefault {
  val SESSION_LIFETIME = 300

  def routes: Routes[SessionService & UserService, Nothing] =
    Routes(
      Method.GET / Root             ->
        Handler
          .fromResource("auth-client.html")
          .orElse(
            Handler.internalServerError("Failed to load HTML file"),
          ),
      Method.GET / "profile" / "me" -> handler { (_: Request) =>
        ZIO.serviceWith[String](name => Response.text(s"Welcome $name!"))
      } @@ cookieAuth,
      Method.POST / "login"         ->
        handler { (request: Request) =>
          val form = request.body.asURLEncodedForm.orElseFail(Response.badRequest("Invalid form data"))
          for {
            username       <- form
              .map(_.get("username"))
              .flatMap(ff => ZIO.fromOption(ff).orElseFail(Response.badRequest("Missing username field!")))
              .flatMap(ff => ZIO.fromOption(ff.stringValue).orElseFail(Response.badRequest("Missing username value!")))
            password       <- form
              .map(_.get("password"))
              .flatMap(ff => ZIO.fromOption(ff).orElseFail(Response.badRequest("Missing password field!")))
              .flatMap(ff => ZIO.fromOption(ff.stringValue).orElseFail(Response.badRequest("Missing password value!")))
            sessionService <- ZIO.service[SessionService]
            sessionId      <- sessionService.createSession(username)
            sessionCookie = Cookie.Response(
              name = SESSION_COOKIE_NAME,
              content = sessionId,
              maxAge = Some(SESSION_LIFETIME.seconds),
              isHttpOnly = true,
              isSecure = false, // Set to true in production with HTTPS
              sameSite = Some(Cookie.SameSite.Strict),
            )
            users <- ZIO.service[UserService]
            user <- users
              .getUser(username)
              .orElseFail(
                Response.unauthorized("Invalid username or password."),
              )
            res  <-
              if (user.password == Secret(password)) {
                ZIO.succeed(
                  Response
                    .text(s"Login successful! Session created for $username")
                    .addCookie(sessionCookie),
                )
              } else
                ZIO.fail(Response.unauthorized("Invalid username or password."))
          } yield res
        },
      Method.GET / "logout"         ->
        Handler.fromZIO(ZIO.service[SessionService]).flatMap { sessionService =>
          handler { (request: Request) =>
            request.cookie(SESSION_COOKIE_NAME) match {
              case Some(cookie) =>
                sessionService.removeSession(cookie.content) *>
                  ZIO.succeed(
                    Response
                      .text("Logged out successfully!")
                      .addCookie(Cookie.clear(SESSION_COOKIE_NAME)),
                  )
              case None         =>
                ZIO.succeed(Response.text("No active session found."))
            }
          } @@ cookieAuth.as(())
        },
    ) @@ Middleware.debug

  override val run = {
    for {
      _ <- Console.printLine(" Starting Cookie Authentication Server with Resource Loading...")
      _ <- Console.printLine(" Web Client available at: http://localhost:8080")
      _ <- Console.printLine(" API endpoints:")
      _ <- Console.printLine("   - GET /login (with form data)")
      _ <- Console.printLine("   - GET /profile/me (protected)")
      _ <- Console.printLine("   - GET /logout (protected)")
    } yield ()
  }.flatMap(_ => SessionService.make)
    .flatMap(authService => Server.serve(routes).provide(Server.default, SessionService.live, UserService.live))
}
