import zhttp.http._
import zhttp.service._
import zio._
import zio.stream._

import java.nio.file.{Paths => JPaths}

object FileStreaming extends App {
  // Read the file as ZStream
  val content = HttpData.fromStream {
    ZStream.fromFile(JPaths.get("../README.md"))
  }

  // Create HTTP route
  val app = HttpApp.collect {
    case Method.GET -> Root / "health" => Response.ok
    case Method.GET -> Root / "file"   => Response.http(content = content)
  }

  // Run it like any simple app
  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    Server.start(8090, HttpApp(app.asHttp.silent)).exitCode
}
