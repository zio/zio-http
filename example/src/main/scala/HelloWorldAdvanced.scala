import zhttp.http._
import zhttp.service._
import zhttp.service.server.ServerChannelFactory
import zio._

object HelloWorldAdvanced extends App {
  // Set a port
  private val PORT = 8090

  private val fooBar = Http.collect {
    case Method.GET -> Root / "foo" => Response.text("bar")
    case Method.GET -> Root / "bar" => Response.text("foo")
  }

  private val app = Http.collectM {
    case req @ Method.GET -> Root / "random" => req.discardContent &> random.nextString(10).map(Response.text)
    case req @ Method.GET -> Root / "utc"    =>
      req.discardContent &> clock.currentDateTime.map(s => Response.text(s.toString))

    case req @ Method.POST -> Root / "upload" =>
      req.headers.find(h => h.lowercaseEquals(Header.contentTypeJson)) match {
        case Some(_) =>
          req.getBodyAsString.map(echoText => echoText.map(Response.text).getOrElse(Response.status(Status.OK)))
        case None    =>
          req.contentSize.map(length => Response.text(s"done: ${length}"))
      }
  }

  private val server =
    Server.port(PORT) ++             // Setup port
      Server.disableLeakDetection ++ // Paranoid leak detection (affects performance)
      Server.streamingRequests ++    // Get the request body as a stream
      Server.maxRequestSize(100 * 1024) ++
      Server.app(fooBar <> app)      // Setup the Http app

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = {
    // Configure thread count using CLI
    val nThreads: Int = args.headOption.flatMap(_.toIntOption).getOrElse(0)

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
