import zhttp.http._
import zhttp.service._
import zio._
import zio.stream._

object FileStreaming extends App {
  // Read the file as ZStream
  val content = HttpData.fromStream {
    ZStream.fromFile(java.nio.file.Path.of("../README.md"))
  }

  // Create HTTP route
  val app = Http.collect {
    case Method.GET -> Root / "health" => Response.ok
    case Method.GET -> Root / "file"   => Response.http(content = content)
  }

  // Run it like any simple app
  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    Server.start(8090, app.silent).exitCode
}
