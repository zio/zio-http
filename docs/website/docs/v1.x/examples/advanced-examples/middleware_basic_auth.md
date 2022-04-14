# Basic Authentication

```scala
package example

import zhttp.http.Middleware.basicAuth
import zhttp.http._
import zhttp.service.Server
import zio.{App, ExitCode, URIO}

object BasicAuth extends ZIOAppDefault {

  // Http app that requires a JWT claim
  val user: UHttpApp = Http.collect[Request] { case Method.GET -> !! / "user" / name / "greet" =>
    Response.text(s"Welcome to the ZIO party! ${name}")
  }

  // Composing all the HttpApps together
  val app: UHttpApp = user @@ basicAuth("admin", "admin")

  // Run it like any simple app
  override val run =
    Server.start(8090, app)
}

```