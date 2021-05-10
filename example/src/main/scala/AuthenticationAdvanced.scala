import zhttp.http.auth._
import zhttp.http.{Method, _}
import zhttp.service.Server
import zio._

object AuthenticationAdvanced extends App {

  case class AuthInfo(username: String)

  def authenticate(request: Request): Either[HttpError, AuthInfo] =
    for {
      headerValue <- request.getAuthorization.toRight(HttpError.Unauthorized())

      // ... validate header value against user store
      // or return Left(HttpError.Unauthorized())

      authInfo <-
        // User is authenticated,
        // but is he authorized to performed the action?
        if (headerValue.equalsIgnoreCase("basic jdg"))
          Right(AuthInfo("Jdg"))
        else
          Left(HttpError.Forbidden())

    } yield authInfo

  val authMiddleware: Http[Any, HttpError, Request, AuthedRequest[AuthInfo]] =
    Auth.customM { request =>
      ZIO.fromEither(authenticate(request))
    }

  //There is also a version to create non-effectful auth middlewares

  /*val authMiddleware: Auth[Any, HttpError, AuthInfo] =
    Auth.custom { request =>
      authenticate(request)
    }*/

  val anonymousApp: UHttpApp =
    Http.collect[Request] { case Method.GET -> Root / "health" =>
      Response.ok
    }

  val protectedApp: Http[Any, Nothing, AuthedRequest[AuthInfo], UResponse] =
    Http.collect[AuthedRequest[AuthInfo]] { case authed `@` (Method.GET -> Root / "whoami") =>
      Response.text(s"You are ${authed.username}")
    }

  val protectedAppM: Http[Any, Nothing, AuthedRequest[AuthInfo], UResponse] =
    Http.collectM[AuthedRequest[AuthInfo]] { case authed `@` (Method.GET -> Root / "whoami-json") =>
      ZIO.succeed {
        Response.jsonString(s"""{"username":"${authed.username}"}""")
      }
    }

  val app: Http[Any, HttpError, Request, UResponse] = anonymousApp +++
    authMiddleware(protectedApp +++ protectedAppM)

  // Run it like any simple app
  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    Server.start(8090, app).exitCode
}
