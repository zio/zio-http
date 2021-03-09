import zhttp.http._
import zhttp.service.Server
import zio._

object HelloWorld extends App {
  val app = Http.collect[Request] {
    case Method.GET -> Root / "text" => Response.text("Hello World!")
    case Method.GET -> Root / "json" => Response.jsonString("""{"greetings": "Hello World!"}""")
  }

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    Server.start(8090, app.silent).exitCode
}
