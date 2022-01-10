package example

import zhttp.http._
import zhttp.http.middleware.Middleware
import zhttp.http.middleware.Middleware.codec
import zhttp.service.Server
import zio._

object Codec extends App {

  // create an Http
  val http: Http[Any, Nothing, String, String]                         = Http.collect[String] {
    case "0" => "zero"
    case "1" => "one"
  }
  // Middleware to transform above http to HttpApp
  val mid: Middleware[Any, Nothing, String, String, Request, Response] =
    codec[Request, String](
      decoder = in => in.url.path.asString.filterNot(_ == '/'),
      encoder = x => Response.text(x),
    )
  // Create HTTP route
  val app: HttpApp[Any, Nothing]                                       = http @@ mid

  // Run it like any simple app
  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    Server.start(8090, app.silent).exitCode
}
