//> using dep "dev.zio::zio-http:3.4.1"

package example.auth

import zio._

import zio.http._

object CookieBasedAuthentication extends ZIOAppDefault {
  val route: Routes[Ref[Map[String, String]], Nothing] =
    Routes(
      Method.GET / "login"          -> handler {
        for {
          sessionId <- ZIO.randomWith(_.nextUUID).map(_.toString)
          _         <-
            ZIO.serviceWithZIO[Ref[Map[String, String]]](
              _.update(_ + (sessionId -> "admin")),
            )
        } yield Response.ok.addCookie(
          Cookie.Response(
            name = "session_id",
            content = sessionId,
            domain = Some("localhost"),
            path = Some(Path.root),
            maxAge = Some(30.second),
            isSecure = true,
            isHttpOnly = true,
            sameSite = Some(Cookie.SameSite.Strict),
          ),
        )
      } @@ Middleware.basicAuth("admin", "admin"),
      Method.GET / "logout"         -> handler {
        Response.ok.addCookie(Cookie.clear("session_id"))
      },
      Method.GET / "profile" / "me" -> handler { (req: Request) =>
        {
          for {
            sessionId <- ZIO.fromOption(req.cookie("session_id").map(_.content))
            user      <- ZIO.serviceWithZIO[Ref[Map[String, String]]](_.get.map(_.get(sessionId))).some
          } yield Response.text(s"Welcome $user!")
        }.orElseFail(Response.unauthorized("You are not authorized to view this page"))
      },
    )

  def run =
    Server
      .serve(route)
      .provide(
        Server.default,
        ZLayer.fromZIO(Ref.make(Map.empty[String, String])),
      )
}
