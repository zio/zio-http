package example

import zhttp.http._
import zhttp.service.server.ServerChannelFactory
import zhttp.service.{EventLoopGroup, Server}
import zio.{Console, Scope, ZIO, ZIOAppDefault}

object TestApp extends ZIOAppDefault {

  // Create HTTP route
  val app: HttpApp[Any, Nothing] = Http.collect[Request] {
    case Method.GET -> !! / "text"       => Response.text("Hello World!")
    case Method.GET -> !! / "json"       => Response.json("""{"greetings": "Hello World!"}""")
    case req @ Method.POST -> !! / "bug" => Response(data = req.data)
  }

  private val server =
    Server.paranoidLeakDetection ++ // Paranoid leak detection (affects performance)
      Server.enableObjectAggregator(1024) ++
      Server.app(app)               // Setup the Http app

  // Run it like any simple app
  override val run =
    server.make
      .flatMap(start =>
        // Waiting for the server to start
        Console.printLine(s"Server started on port ${start.port}")

        // Ensures the server doesn't die after printing
          *> ZIO.never,
      )
      .provide(ServerChannelFactory.auto, EventLoopGroup.auto(), Scope.default)
}
