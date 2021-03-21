import zhttp.http._
import zhttp.service._
import zio._

object HelloWorldAdvanced extends App {
  // Set a port
  private val PORT = 8090

  // Create an Http app
  private val app = Http.collectM[Request] {
    case Method.GET -> Root / "text"   => UIO(Response.text("Hello World!"))
    case Method.GET -> Root / "random" => random.nextInt.map(i => Response.text(i.toString))
  }

  private val server =
    Server.port(PORT) ++             // Setup port
      Server.disableLeakDetection ++ // Disable leak detection for better performance
      Server.app(app)                // Setup the Http app

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = {
    // Configure thread count using CLI
    val nThreads: Int = args.headOption.flatMap(_.toIntOption).getOrElse(0)

    // Create a new server
    server.make
      .use(_ =>
        // Waiting for the server to start
        console.putStrLn(s"Server started on port ${PORT}")

        // Ensures the server doesn't die after printing
        *> ZIO.never,
      )
      .exitCode
      .provideCustomLayer(EventLoopGroup.auto(nThreads))
  }
}
