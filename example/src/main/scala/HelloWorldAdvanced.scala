import zhttp.http._
import zhttp.service._
import zhttp.service.server.Transport.Auto
import zio._

object HelloWorldAdvanced extends App {
  // Set a port
  private val PORT = 8090

  private val fooBar: HttpApp[Any, Nothing] = HttpApp.collect {
    case Method.GET -> !! / "foo" => Response.text("bar")
    case Method.GET -> !! / "bar" => Response.text("foo")
  }

  private val app = HttpApp.collectM {
    case Method.GET -> !! / "random" => random.nextString(10).map(Response.text)
    case Method.GET -> !! / "utc"    => clock.currentDateTime.map(s => Response.text(s.toString))
  }

  private val server =
    Server.port(PORT) ++              // Setup port
      Server.paranoidLeakDetection ++ // Paranoid leak detection (affects performance)
      Server.app(fooBar +++ app) ++   // Setup the Http app
      Server.transport(Auto) ++       // (Server.epoll, Server.kqueue, Server.uring, Server.auto)
      Server.numThreads(1)            // number of threads for event loop

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = {
    // Create a new server
    server.make
      .use(_ =>
        // Waiting for the server to start
        console.putStrLn(s"Server started on port $PORT")

        // Ensures the server doesn't die after printing
          *> ZIO.never,
      )
      .exitCode
  }
}
