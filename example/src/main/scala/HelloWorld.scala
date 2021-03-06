import io.netty.incubator.channel.uring.IOUring
import io.netty.util.ResourceLeakDetector
import zhttp.http._
import zhttp.service.server.Server
import zio._

object HelloWorld extends App {
  val app                                                        = Http.route {
    case Method.GET -> Root / "text" => Response.text("Hello World!")
    case Method.GET -> Root / "json" => Response.jsonString("""{"greetings": "Hello World!"}""")
  }
  ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.DISABLED)
  println(s"Here: ${IOUring.isAvailable}")
  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    Server.start(8090, app).exitCode
}
