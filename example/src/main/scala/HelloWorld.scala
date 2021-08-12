import zhttp.http._
import zhttp.service.Server
import zio._

import java.net.URLDecoder

object HelloWorld extends App {

  // Create HTTP route
  val app: HttpApp[Any, Nothing] = HttpApp.collect {
    case Method.GET -> Root / "text"         => Response.text("Hello World!")
    case Method.GET -> Root / "json"         => Response.jsonString("""{"greetings": "Hello World!"}""")
    case Method.GET -> Root / "greet" / name => Response.text(s"Greetings ${URLDecoder.decode(name, "UTF-8")}!")
  }

  // Run it like any simple app
  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    Server.start(8090, app.silent).exitCode
}
