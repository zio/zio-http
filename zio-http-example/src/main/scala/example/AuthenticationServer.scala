package example

import pdi.jwt.{Jwt, JwtAlgorithm, JwtClaim}
import zio._
import zio.http.Middleware.bearerAuth
import zio.http._
import zio.http.model.{Method, Status}

import java.time.Clock

object AuthenticationServer extends ZIOAppDefault {

  /**
   * This is an example to demonstrate barer Authentication middleware. The
   * Server has 2 routes. The first one is for login,Upon a successful login, it
   * will return a jwt token for accessing protected routes. The second route is
   * a protected route that is accessible only if the request has a valid jwt
   * token. AuthenticationClient example can be used to makes requests to this
   * server.
   */

  // Secret Authentication key
  val SECRET_KEY = "secretKey"

  implicit val clock: Clock = Clock.systemUTC

  // Helper to encode the JWT token
  def jwtEncode(username: String): String = {
    val json  = s"""{"user": "${username}"}"""
    val claim = JwtClaim {
      json
    }.issuedNow.expiresIn(300)
    Jwt.encode(claim, SECRET_KEY, JwtAlgorithm.HS512)
  }

  // Helper to decode the JWT token
  def jwtDecode(token: String): Option[JwtClaim] = {
    Jwt.decode(token, SECRET_KEY, Seq(JwtAlgorithm.HS512)).toOption
  }

  // Http app that is accessible only via a jwt token
  def user: UHttpApp = Http.collect[Request] { case Method.GET -> !! / "user" / name / "greet" =>
    Response.text(s"Welcome to the ZIO party! ${name}")
  } @@ bearerAuth(jwtDecode(_).isDefined)

  // App that let's the user login
  // Login is successful only if the password is the reverse of the username
  def login: UHttpApp = Http.collect[Request] { case Method.GET -> !! / "login" / username / password =>
    if (password.reverse.hashCode == username.hashCode) Response.text(jwtEncode(username))
    else Response.text("Invalid username or password.").setStatus(Status.Unauthorized)
  }

  // Composing all the HttpApps together
  val app: UHttpApp = login ++ user

  // Run it like any simple app
  override val run = Server.serve(app).provide(Server.default)
}
