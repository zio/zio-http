package example

import zhttp.http.{HttpData, Method, _}
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
  // Does not use chunked transfer encoding

  val videoFileHttp       = Http.fromFile(new File("src/main/resources/TestVideoFile.mp4"))
  val textFileHttp        = Http.fromFile(new File("src/main/resources/TestFile.txt"))
  val nonExistentFilePath = Http.fromFile(new File("src/main/resources/NonExistent.txt"))

  // Create HTTP route
  val app = Http.collectHttp[Request] {
    case Method.GET -> !! / "health" => Http.ok
    case Method.GET -> !! / "file"   => Http.fromData(content)
    case Method.GET -> !! / "video"  => videoFileHttp
    case Method.GET -> !! / "text"   => textFileHttp
    case Method.GET -> !! / "error"  => nonExistentFilePath
  }

  // Run it like any simple app
  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    Server.start(8090, app.silent).exitCode
}
