import zhttp.http.auth._
import zhttp.http.{Method, _}
import zhttp.service.Server
import zio._

object AuthenticationAdvanced extends App {

  // just an example. could be a set of claims for example.
  case class AuthInfo(username: String)

  // implement a simple way to authenticate a request (it is returning a simple Either here,
  // but it could be an effectful computation returning ZIO[R, E, AuthInfo])
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

  val authMiddleware: Auth[Any, HttpError, AuthInfo] =
    Auth.customM { request =>
      ZIO.fromEither(authenticate(request))
    }

  //There is also a version to create non-effectful auth middlewares

//  val authMiddleware: Auth[Any, HttpError, AuthInfo] =
//    Auth.custom { request =>
//      authenticate(request)
//    }

  // An anonymous app is defined as always, collecting simple requests
  val anonymousApp: UHttpApp =
    Http.collect[Request] { case Method.GET -> Root / "health" =>
      Response.ok
    }

  // A regular protected app is collecting authed requests and returning a simple response
  val protectedApp: Http[Any, Nothing, AuthedRequest[AuthInfo], UResponse] =
    Http.collect[AuthedRequest[AuthInfo]] { case authed `@` (Method.GET -> Root / "whoami") =>
      Response.text(s"You are ${authed.username}")
    }

  // An effectful protected app is collecting authed requests and returning a ZIO[R, E, Some_Response]
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
