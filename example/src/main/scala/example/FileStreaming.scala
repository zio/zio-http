package example

import zhttp.http.{HttpData, Method, Response, _}
import zhttp.service.Server
import zio.stream.ZStream
import zio.{App, ExitCode, URIO}

import java.io.File
import java.nio.file.Paths

object FileStreaming extends App {
  // Read the file as ZStream
  val content = HttpData.fromStream {
    ZStream.fromFile(Paths.get("README.md"))
  }

  // Uses netty's capability to write file content to the Channel
  // Content-type response headers are automatically identified and added
  // Does not use Chunked transfer encoding
  val videoFileContent = HttpData.fromFile(new File("src/main/resources/TestVideoFile.mp4"))
  val textFileContent  = HttpData.fromFile(new File("src/main/resources/TestFile.txt"))

  // Create HTTP route
  val app = Http.collect[Request] {
    case Method.GET -> !! / "health" => Response.ok
    case Method.GET -> !! / "file"   => Response(data = content)
    case Method.GET -> !! / "video"  => Response(data = videoFileContent)
    case Method.GET -> !! / "text"   => Response(data = textFileContent)
  }

  // Run it like any simple app
  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    Server.start(8090, app.silent).exitCode
}
