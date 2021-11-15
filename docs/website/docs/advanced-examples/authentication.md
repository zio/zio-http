# Authentication

```scala
import pdi.jwt.{Jwt, JwtAlgorithm, JwtClaim}
import zhttp.http.{Method, _}
import zhttp.service.Server
import zio._

import java.time.Clock

object Authentication extends App {
  // Secret Authentication key
  val SECRET_KEY = "secretKey"

  implicit val clock: Clock = Clock.systemUTC

  // Helper to encode the JWT token
  def jwtEncode(username: String): String = {
    val json  = s"""{"user": "${username}"}"""
    val claim = JwtClaim { json }.issuedNow.expiresIn(60)
    Jwt.encode(claim, SECRET_KEY, JwtAlgorithm.HS512)
  }

  // Helper to decode the JWT token
  def jwtDecode(token: String): Option[JwtClaim] = {
    Jwt.decode(token, SECRET_KEY, Seq(JwtAlgorithm.HS512)).toOption
  }

  // Authentication middleware
  // Takes in a Failing HttpApp and a Succeed HttpApp which are 
  // called based on Authentication success or failure
  // For each request tries to read the `X-ACCESS-TOKEN` header
  // Validates JWT Claim
  def authenticate[R, E](fail: HttpApp[R, E], success: JwtClaim => HttpApp[R, E]): HttpApp[R, E] = 
    Http.flatten {
        HttpApp.fromFunction {
        _.getHeader("X-ACCESS-TOKEN")
            .flatMap(header => jwtDecode(header.value.toString))
            .fold[HttpApp[R, E]](fail)(success)
        }
    }

  // Http app that requires a JWT claim
  def user(claim: JwtClaim): UHttpApp = Http.collect {
    case Method.GET -> !! / "user" / name / "greet" => 
        Response.text(s"Welcome to the ZIO party! ${name}")
    case Method.GET -> !! / "user" / "expiration"   => 
        Response.text(s"Expires in: ${claim.expiration.getOrElse(-1L)}")
  }

  // App that let's the user login
  // Login is successful only if the password is the reverse of the username
  def login: UHttpApp = Http.collect { case Method.GET -> !! / "login" / username / password =>
    if (password.reverse == username) Response.text(jwtEncode(username))
    else Response.fromHttpError(HttpError.Unauthorized("Invalid username of password\n"))
  }

  // Composing all the HttpApps together
  val app: UHttpApp = login +++ authenticate(HttpApp.forbidden("Not allowed!"), user)

  // Run it like any simple app
  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    Server.start(8090, app).exitCode
}

```