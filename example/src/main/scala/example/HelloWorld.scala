package example

import zhttp.http._
import zhttp.service.Server
import zhttp.service.server.content.compression.CompressionOptions._
import zio._
object HelloWorld extends App {

  // Create HTTP route
  val app: HttpApp[Any, Nothing] = Http.collect[Request] {
    case Method.GET -> !! / "text" => Response.text("Hello World!")
    case Method.GET -> !! / "json" => Response.json("""{"greetings": "Hello World!"}""")
  }

  val middleware: Middleware[Any, Nothing, Request, Response, Request, Response] =
    Middleware.intercept[Request, Response](identity)((response, _) => response.withCompressionOptions(gzip, deflate))

  // Run it like any simple app
  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    Server.start(8090, app).exitCode
}
