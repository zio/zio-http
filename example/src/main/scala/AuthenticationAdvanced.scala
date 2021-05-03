import zhttp.http.auth._
import zhttp.http.{ Method, _ }
import zhttp.service.Server
import zio._

import java.util.Base64
import scala.util.Try

object AuthenticationAdvanced extends App {

  case class AuthInfo(username: String)

  type AuthMiddleware = HttpAuthenticationMiddleware[Any, AuthInfo]

  private val users: Map[String, String] = Map(
    "John De Goes" -> "ZIO rocks!",
    "Adam Fraser" -> "I love ZIO!"
  )

  def authenticate(request: Request): Either[HttpError, AuthInfo] =
    for {
      headerValue <- request.getAuthorization.toRight(HttpError.Unauthorized())
      value <- headerValue.split(" ").map(_.trim).toList match {
        case scheme :: value :: Nil if scheme.equalsIgnoreCase("basic") => Right(value)
        case _ => Left(HttpError.Unauthorized())
      }
      decoded <- Try { new String(Base64.getDecoder.decode(value)) }
                    .toOption
                    .toRight(HttpError.Unauthorized())
      userNamePassword <- decoded.split(":").map(_.trim).toList match {
        case usr :: pass :: Nil => Right((usr -> pass))
        case _ => Left(HttpError.Unauthorized())
      }
      (user, password) = userNamePassword
      authenticated <- users.collectFirst {
        case (usr, pass) if usr.equalsIgnoreCase(user)
          && pass.equalsIgnoreCase(password) => usr
      } toRight(HttpError.Forbidden())
    } yield AuthInfo(authenticated)

  val authMiddleware: AuthMiddleware =
    HttpAuthenticationMiddleware.ofM { request =>
      ZIO.fromEither(authenticate(request))
         .map(AuthedRequest(_, request))
    }

/*
  //There is also a version to create non-effectful auth middlewares

  val authMiddleware: AuthMiddleware =
    HttpAuthenticationMiddleware.of { request =>
      authenticate(request).map(AuthedRequest(_, request))
    }
*/

  val anonymousApp: UHttpApp = Http.collect[Request] {
    case Method.GET -> Root / "health" => Response.ok
  }

  val protectedApp = Http.collectAuthed[AuthInfo] {
    case authed `@` (Method.GET -> Root / "whoami") =>
      Response.text(s"You are ${authed.username}")
  }

  val protectedAppM = Http.collectAuthedM[AuthInfo] {
    case authed `@` (Method.GET -> Root / "whoami-json") =>
      ZIO.succeed {
        Response.jsonString(s"""{"username":"${authed.username}"}""")
      }
  }

  val app: HttpApp[Any, HttpError] = anonymousApp +++
    (authMiddleware >>> (protectedApp +++ protectedAppM))

  // Run it like any simple app
  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    Server.start(8090, app).exitCode
}
