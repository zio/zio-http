package example

import zio._
import zio.http._
import zio.http.service.{EventLoopGroup, ServerChannelFactory}

import scala.util.Try

object HelloWorldAdvanced extends ZIOAppDefault {
  // Set a port
  private val PORT = 0

  private val fooBar: HttpApp[Any, Nothing] = Http.collect[Request] {
    case Method.GET -> !! / "foo" => Response.text("bar")
    case Method.GET -> !! / "bar" => Response.text("foo")
  }

  private val app = Http.collectZIO[Request] {
    case Method.GET -> !! / "random" => Random.nextString(10).map(Response.text(_))
    case Method.GET -> !! / "utc"    => Clock.currentDateTime.map(s => Response.text(s.toString))
  }

  private val server =
    Server.port(PORT) ++              // Setup port
      Server.paranoidLeakDetection ++ // Paranoid leak detection (affects performance)
      Server.app(fooBar ++ app)       // Setup the Http app

  val run = ZIOAppArgs.getArgs.flatMap { args =>
    // Configure thread count using CLI
    val nThreads: Int = args.headOption.flatMap(x => Try(x.toInt).toOption).getOrElse(0)

    // Create a new server
    server.make
      .flatMap(start =>
        // Waiting for the server to start
        Console.printLine(s"Server started on port ${start.port}")

        // Ensures the server doesn't die after printing
          *> ZIO.never,
      )
      .provide(ServerChannelFactory.auto, EventLoopGroup.auto(nThreads), Scope.default)
  }
}
