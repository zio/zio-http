package example

import zhttp.http._
import zhttp.service.server.ServerChannelFactory
import zhttp.service.{EventLoopGroup, Server}
import zio._

import scala.util.Try

object HelloWorldAdvanced extends App {
  // Set a port
  private val PORT = 8090

  private val fooBar: HttpApp[Any, Nothing] = HttpApp.collect {
    case Method.GET -> !! / "foo" => Response.text("bar")
    case Method.GET -> !! / "bar" => Response.text("foo")
  }

  private val app = HttpApp.collectM {
    case Method.GET -> !! / "random" => random.nextString(10).map(Response.text(_))
    case Method.GET -> !! / "utc"    => clock.currentDateTime.map(s => Response.text(s.toString))
  }

  private val server =
    Server.port(PORT) ++              // Setup port
      Server.paranoidLeakDetection ++ // Paranoid leak detection (affects performance)
      Server.app(fooBar +++ app)      // Setup the Http app

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = {
    // Configure thread count using CLI
    val nThreads: Int = args.headOption.flatMap(x => Try(x.toInt).toOption).getOrElse(0)

    // Create a new server
    server.make
      .use(_ =>
        // Waiting for the server to start
        console.putStrLn(s"Server started on port $PORT")

        // Ensures the server doesn't die after printing
          *> ZIO.never,
      )
      .provideCustomLayer(ServerChannelFactory.auto ++ EventLoopGroup.auto(nThreads))
      .exitCode
  }
}
