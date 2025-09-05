//> using dep "dev.zio::zio-http:3.4.1"
//> using dep "com.github.jwt-scala::jwt-core:10.0.4"

package example

import java.time.Clock

import scala.util.Try

import zio._

import zio.http._

import pdi.jwt.{Jwt, JwtAlgorithm, JwtClaim}

/**
 * This is an example to demonstrate bearer Authentication middleware. The
 * Server has 2 routes. The first one is for login, Upon a successful login, it
 * will return a jwt token for accessing protected routes. The second route is a
 * protected route that is accessible only if the request has a valid jwt token.
 * AuthenticationClient example can be used to makes requests to this server.
 */
object AuthenticationServer extends ZIOAppDefault {
  implicit val clock: Clock = Clock.systemUTC

  // Secret Authentication key
  val SECRET_KEY = "secretKey"

  def jwtEncode(username: String, key: String): String =
    Jwt.encode(JwtClaim(subject = Some(username)).issuedNow.expiresIn(300), key, JwtAlgorithm.HS512)

  def jwtDecode(token: String, key: String): Try[JwtClaim] =
    Jwt.decode(token, key, Seq(JwtAlgorithm.HS512))

  val bearerAuthWithContext: HandlerAspect[Any, String] =
    HandlerAspect.interceptIncomingHandler(Handler.fromFunctionZIO[Request] { request =>
      request.header(Header.Authorization) match {
        case Some(Header.Authorization.Bearer(token)) =>
          ZIO
            .fromTry(jwtDecode(token.value.asString, SECRET_KEY))
            .orElseFail(Response.badRequest("Invalid or expired token!"))
            .flatMap(claim => ZIO.fromOption(claim.subject).orElseFail(Response.badRequest("Missing subject claim!")))
            .map(u => (request, u))

        case _ => ZIO.fail(Response.unauthorized.addHeaders(Headers(Header.WWWAuthenticate.Bearer(realm = "Access"))))
      }
    })

  def routes: Routes[Any, Response] =
    Routes(
      // A route that is accessible only via a jwt token
      Method.GET / "profile" / "me" -> handler { (_: Request) =>
        ZIO.serviceWith[String](name => Response.text(s"Welcome $name!"))
      } @@ bearerAuthWithContext,

      // A login route that is successful only if the password is the reverse of the username
      Method.GET / "login" ->
        handler { (request: Request) =>
          val form = request.body.asMultipartForm.orElseFail(Response.badRequest)
          for {
            username <- form
              .map(_.get("username"))
              .flatMap(ff => ZIO.fromOption(ff).orElseFail(Response.badRequest("Missing username field!")))
              .flatMap(ff => ZIO.fromOption(ff.stringValue).orElseFail(Response.badRequest("Missing username value!")))
            password <- form
              .map(_.get("password"))
              .flatMap(ff => ZIO.fromOption(ff).orElseFail(Response.badRequest("Missing password field!")))
              .flatMap(ff => ZIO.fromOption(ff.stringValue).orElseFail(Response.badRequest("Missing password value!")))
          } yield
            if (password.reverse.hashCode == username.hashCode)
              Response.text(jwtEncode(username, SECRET_KEY))
            else
              Response.unauthorized("Invalid username or password.")
        },
    ) @@ Middleware.debug

  override val run = Server.serve(routes).provide(Server.default)
}
