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
  // Takes in a Failing HttpApp and a Succeed HttpApp which are called based on Authentication success or failure
  // For each request tries to read the `X-ACCESS-TOKEN` header
  // Validates JWT Claim
  def authenticate[R, E](
    fail: Http[R, E, Request, Response[R, E]],
    success: JwtClaim => Http[R, E, Request, Response[R, E]],
  ): Http[R, E, Request, Response[R, E]] = Http.flatten {
    Http
      .fromFunction[Request] {
        _.getHeader("X-ACCESS-TOKEN")
          .flatMap(header => jwtDecode(header.value.toString))
          .fold[Http[R, E, Request, Response[R, E]]](fail)(success)
      }
  }

  // Http app that requires a JWT claim
  def user(claim: JwtClaim) = Http.collect[Request] {
    case Method.GET -> Root / "user" / name / "greet" => Response.text(s"Welcome to the ZIO party! ${name}")
    case Method.GET -> Root / "user" / "expiration"   => Response.text(s"Expires in: ${claim.expiration.getOrElse(-1L)}")
  }

  // App that let's the user login
  // Login is successful only if the password is the reverse of the username
  def login: UHttpApp = HttpApp.collect[Any, Nothing] { case Method.GET -> Root / "login" / username / password =>
    if (password.reverse == username) Response.text(jwtEncode(username))
    else Response.fromHttpError(HttpError.Unauthorized("Invalid username of password\n"))
  }

  // Composing all the HttpApps together
  val app: UHttpApp = new HttpApp[Any, Nothing](
    login.asHttp +++ authenticate(Http.succeed(HttpError.Forbidden("Not allowed!").toResponse), user),
  )

  // Run it like any simple app
  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    Server.start(8090, app).exitCode
}
