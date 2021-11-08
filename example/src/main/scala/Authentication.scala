import pdi.jwt.{Jwt, JwtAlgorithm, JwtClaim}
import zhttp.http._
import zhttp.http.middleware.HttpMiddleware.jwt
import zhttp.service.Server
import zio._

import java.time.Clock
object Authentication extends App {
  // Secret Authentication key
  val SECRET_KEY                          = "secretKey"
  implicit val clock: Clock               = Clock.systemUTC
  // Helper to encode the JWT token
  def jwtEncode(username: String): String = {
    val json  = s"""{"user": "${username}"}"""
    val claim = JwtClaim { json }.issuedNow.expiresIn(300)
    Jwt.encode(claim, SECRET_KEY, JwtAlgorithm.HS512)
  }

  // Final httpApp
  val user: UHttpApp = HttpApp.collect { case Method.GET -> !! / "user" / name / "greet" =>
    Response.text(s"Welcome to the ZIO party! ${name}")
  }

  // App that let's the user login
  // Login is successful only if the password is the reverse of the username
  def login: UHttpApp = HttpApp.collect { case Method.GET -> !! / "login" / username / password =>
    if (password.reverse == username) Response.text(jwtEncode(username))
    else Response.fromHttpError(HttpError.Unauthorized("Invalid username of password\n"))
  }

  // Composing all the HttpApps together
  val app: UHttpApp = login +++ user @@ jwt(SECRET_KEY)

  // Run it like any simple app
  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    Server.start(8090, app).exitCode
}
