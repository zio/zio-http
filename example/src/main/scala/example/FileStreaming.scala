package example

import zhttp.http.{HttpData, Method, Response, _}
import zhttp.service.Server
import zio._
import zio.stream.ZStream

import java.nio.file.Paths

object FileStreaming extends ZIOAppDefault {
  // Read the file as ZStream
  val content = HttpData.fromStream {
    ZStream.fromPath(Paths.get("README.md"))
  }

  // Create HTTP route
  val app = Http.collect[Request] {
    case Method.GET -> !! / "health" => Response.ok
    case Method.GET -> !! / "file"   => Response(data = content)
  }

  // Run it like any simple app
  val run =
    Server.start(8090, app.silent)
}
