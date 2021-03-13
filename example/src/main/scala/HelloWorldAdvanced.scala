import zhttp.http._
import zhttp.service.{EventLoopGroup, Server}
import zio._

import scala.util.Try

object HelloWorldAdvanced extends App {
  val app = Http.collect[Request] {
    case Method.GET -> Root / "text" => Response.text("Hello World!")
    case Method.GET -> Root / "json" => Response.jsonString("""{"greetings": "Hello World!"}""")
  }

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = {
    val nThreads: Int = args.headOption.flatMap(i => Try(i.toInt).toOption).getOrElse(0)

    Server.make(app).flatMap(_.start(8090) *> ZIO.never).exitCode.provideCustomLayer(EventLoopGroup.auto(nThreads))
  }
}
