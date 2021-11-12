package example

import zhttp.http.{HttpApp, Method, Response, _}
import zhttp.service.Server
import zhttp.service.server.Transport
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
    case Method.GET -> !! / "random" => random.nextString(10).map(Response.text)
    case Method.GET -> !! / "utc"    => clock.currentDateTime.map(s => Response.text(s.toString))
  }

  private val server =
    Server.port(PORT) ++                  // Setup port
      Server.paranoidLeakDetection ++     // Paranoid leak detection (affects performance)
      Server.app(fooBar +++ app) ++       // Setup the Http app
      Server.transport(Transport.Auto) ++ // (Server.epoll, Server.kqueue, Server.uring, Server.auto)
      Server.nio // ONLY if we are overriding earlier transport settings (nio/epoll/kQueue/uring/auto) last one takes precedence

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = {
    // Configure thread count using CLI
    val nThreads: Int = args.headOption.flatMap(x => Try(x.toInt).toOption).getOrElse(0)

    // Create a new server
    (server ++ Server.threads(nThreads)).make
      .use(_ =>
        // Waiting for the server to start
        console.putStrLn(s"Server started on port $PORT")

        // Ensures the server doesn't die after printing
          *> ZIO.never,
      )
      .exitCode
  }
}
