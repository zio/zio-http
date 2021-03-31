import zhttp.http._
import zhttp.service._
import zhttp.service.server.ServerChannelFactory
import zio._
import zio.stream.ZStream

import scala.util.Try

object HelloWorldAdvanced extends App {
  // Set a port
  private val PORT = 8090

  private val fooBar: HttpApp[Any, Nothing] = HttpApp.collect {
    case Method.GET -> Root / "foo" => Response.text("bar")
    case Method.GET -> Root / "bar" => Response.text("foo")
  }

  private def streamSize[R, E, A](stream: ZStream[R, E, A]): ZIO[R, E, Long] =
    stream.mapChunks(c => Chunk(c.size)).fold(0L)(_ + _)

  private val app = HttpApp.collectM {
    case Method.GET -> Root / "random"             => random.nextString(10).map(Response.text)
    case Method.GET -> Root / "utc"                =>
      clock.currentDateTime.map(s => Response.text(s.toString))
    case req @ Method.POST -> Root / "file-upload" =>
      req.content match {
        case fd: HttpData.MultipartFormData =>
          val attributes = fd.attributes.map { case (name, attr) =>
            streamSize(attr.content).map(size => s"$name: $size")
          }
          val files      = fd.files.map { case (name, file) => streamSize(file.content).map(size => s"$name: $size") }
          ZIO.collectAllParN(3)(attributes ++ files).map(sizes => Response.text(sizes.mkString(",")))
        case _                              => UIO.succeed(Response.status(Status.BAD_REQUEST))
      }
  }

  private val server =
    Server.port(PORT) ++             // Setup port
      Server.disableLeakDetection ++ // Paranoid leak detection (affects performance)
      Server.app(fooBar +++ app)     // Setup the Http app

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
