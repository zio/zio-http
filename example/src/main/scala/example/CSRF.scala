package example

import zhttp.http.Middleware.{csrfGenerate, csrfValidate}
import zhttp.http._
import zhttp.service.Server
import zio.{App, ExitCode, UIO, URIO}

object CSRF extends App {
  val privateApp: HttpApp[Any, Nothing] = Http.collect[Request] { case Method.GET -> !! / "unsafeEndpoint" =>
    Response.text("secure info")
  } @@ csrfValidate() // Check for matching csrf header and cookie

  val publicApp: HttpApp[Any, Nothing] = Http.collect[Request] { case Method.GET -> !! / "safeEndpoint" =>
    Response.text("hello")
  } @@ csrfGenerate(UIO("Secret")) // set x-csrf token cookie

  val app: HttpApp[Any, Nothing]                                 = publicApp ++ privateApp
  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    Server.start(8092, app).exitCode
}
