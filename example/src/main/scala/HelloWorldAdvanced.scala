import zhttp.experiment.{ContentDecoder, Part}
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
    case Method.GET -> !! / "foo" => Response.text("bar")
    case Method.GET -> !! / "bar" => Response.text("foo")
  }

  val app = HttpApp.collectM {
    case Method.GET -> !! / "random" => random.nextString(10).map(Response.text)
    case Method.GET -> !! / "utc"    => clock.currentDateTime.map(s => Response.text(s.toString))
  }

  private val app2 = HttpApp.collect { case req @ Method.POST -> !! / "fileupload" =>
    val httpData = HttpData.fromStream(
      ZStream.unwrap(
        req
          .decodeContent(ContentDecoder.Multipart)
          .map(file => {
            println("developer")
            println(file)
            file.flatMap {
              case Part.FileData(content, _) => {
                println("came here")
                content
              }
              case Part.Attribute(_, _)      => ???
            }
          }),
      ),
    )
    Response(data = httpData)
  }

  private val server =
    Server.port(PORT) ++              // Setup port
      Server.paranoidLeakDetection ++ // Paranoid leak detection (affects performance)
      Server.app(fooBar +++ app2)     // Setup the Http app

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
