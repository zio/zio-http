# CSRF

```scala
package example

import zio.http.Middleware.{csrfGenerate, csrfValidate}
import zio.http._
import zio.http.Server
import zio._

object CSRF extends App {
  val privateApp = Http.collect[Request] { case Method.GET -> !! / "unsafeEndpoint" =>
    Response.text("secure info")
  } @@ csrfValidate() // Check for matching csrf header and cookie

  val publicApp = Http.collect[Request] { case Method.GET -> !! / "safeEndpoint" =>
    Response.text("hello")
  } @@ csrfGenerate() // set x-csrf token cookie

  val app                                                        = publicApp ++ privateApp
  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    Server.start(8090, app).exitCode
}

```