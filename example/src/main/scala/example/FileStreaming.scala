package example

import zhttp.http.{HttpApp, HttpData, Method, Response, _}
import zhttp.service.Server
import zio.stream.ZStream
import zio.{App, ExitCode, URIO}

import java.nio.file.Paths

object FileStreaming extends App {
  // Read the file as ZStream
  val content = HttpData.fromStream {
    ZStream.fromFile(Paths.get("README.md"))
  }

  // Create HTTP route
  val app = HttpApp.collect {
    case Method.GET -> !! / "health" => Response.ok
    case Method.GET -> !! / "file"   => Response(data = content)
  }

  // Run it like any simple app
  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    Server.start(8090, app.silent).exitCode
}
