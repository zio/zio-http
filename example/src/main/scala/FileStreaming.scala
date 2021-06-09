import zhttp.http._
import zhttp.service._
import zio._
import zio.stream._

object FileStreaming extends App {
  // Read the file as InputStream
  val content =
    HttpData.fromStream(ZStream.fromInputStream(getClass.getClassLoader.getResourceAsStream("SampleFile.txt")))

  // Create HTTP route
  val app = HttpApp.collect {
    case Method.GET -> Root / "health" => Response.ok
    case Method.GET -> Root / "file"   => Response.http(content = content)
  }

  // Run it like any simple app
  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    Server.start(8090, app.silent).exitCode
}
