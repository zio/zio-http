package example.auth.bearer.opaque

import zio.Config.Secret
import zio._

import zio.http._

import example.auth.bearer.opaque.core.AuthHandlerAspect._
import example.auth.bearer.opaque.core._

object AuthenticationServer extends ZIOAppDefault {

  def routes: Routes[TokenService with UserService, Response] =
    Routes(
      Method.GET / Root             ->
        Handler.fromResource("opaque-bearer-token-auth-client.html").orElse {
          Handler.internalServerError("Failed to load HTML file")
        },
      Method.GET / "profile" / "me" -> handler { (_: Request) =>
        ZIO.serviceWith[User](user =>
          Response.text(
            s"Welcome ${user.username}! " +
              s"This is your profile: \n Username: ${user.username} \n Email: ${user.email}",
          ),
        )
      } @@ authenticate,
      Method.POST / "login"         ->
        handler { (request: Request) =>
          val form = request.body.asURLEncodedForm.orElseFail(Response.badRequest)
          for {
            username <- form
              .map(_.get("username"))
              .flatMap(ff => ZIO.fromOption(ff).orElseFail(Response.badRequest("Missing username field!")))
              .flatMap(ff => ZIO.fromOption(ff.stringValue).orElseFail(Response.badRequest("Missing username value!")))
            password <- form
              .map(_.get("password"))
              .flatMap(ff => ZIO.fromOption(ff).orElseFail(Response.badRequest("Missing password field!")))
              .flatMap(ff => ZIO.fromOption(ff.stringValue).orElseFail(Response.badRequest("Missing password value!")))
            users    <- ZIO.service[UserService]
            user     <- users.getUser(username).orElseFail(Response.unauthorized(s"Username or password is incorrect."))
            tokenService <- ZIO.service[TokenService]
            response     <-
              if (user.password == Secret(password))
                tokenService.create(username).map(Response.text)
              else ZIO.fail(Response.unauthorized("Username or password is incorrect."))
          } yield response
        },
      Method.POST / "logout"        ->
        Handler.fromZIO(ZIO.service[TokenService]).flatMap { tokenService =>
          handler { (request: Request) =>
            request.header(Header.Authorization) match {
              case Some(Header.Authorization.Bearer(token)) =>
                tokenService.validate(token.stringValue).flatMap {
                  case Some(username) =>
                    tokenService.revoke(username).as(Response.text("Logged out successfully!"))
                  case None           =>
                    ZIO.fail(Response.unauthorized("Invalid or expired token!"))
                }
              case _                                        =>
                ZIO.fail(
                  Response.unauthorized.addHeaders(Headers(Header.WWWAuthenticate.Bearer(realm = "Access"))),
                )
            }
          } @@ authenticate.as[Unit](())
        },
    ) @@ Middleware.debug

  override val run =
    Server.serve(routes).provide(Server.default, TokenService.inmemory, UserService.live)

}
