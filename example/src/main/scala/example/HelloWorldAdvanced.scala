package example

import zhttp.http._
import zhttp.service.Server
import zio._

object HelloWorldAdvanced extends App {
  // Set a port
  private val PORT = 0

  private val fooBar: HttpApp[Any, Nothing] = Http.collect[Request] {
    case Method.GET -> !! / "foo" => Response.text("bar")
    case Method.GET -> !! / "bar" => Response.text("foo")
  }

  private val app = Http.collectZIO[Request] {
    case Method.GET -> !! / "random" => random.nextString(10).map(Response.text(_))
    case Method.GET -> !! / "utc"    => clock.currentDateTime.map(s => Response.text(s.toString))
  }

  private val server =
    Server.port(PORT) ++              // Setup port
      Server.paranoidLeakDetection ++ // Paranoid leak detection (affects performance)
      Server.app(fooBar ++ app) ++    // Setup the Http app
      Server.auto ++                  // Specifying a Transport type (by default Auto)
      Server.threads(4)               // Thread count for EventLoopGroup

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = {
    // Create a new server
    server.make
      .use(start =>
        // Waiting for the server to start
        console.putStrLn(s"Server started on port ${start.port}")

        // Ensures the server doesn't die after printing
          *> ZIO.never,
      )
      .exitCode
  }
}
