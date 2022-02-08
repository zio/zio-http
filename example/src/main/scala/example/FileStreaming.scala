package example

import zhttp.http._
import zhttp.service.Server
import zio.stream.ZStream
import zio.{App, ExitCode, URIO}

import java.io.File
import java.nio.file.Paths

object FileStreaming extends App {

  // Create HTTP route
  val app = Http.collectHttp[Request] {
    case Method.GET -> !! / "health" => Http.ok

    // Read the file as ZStream
    // Uses the blocking version of ZStream.fromFile
    case Method.GET -> !! / "blocking" => Http.fromStream(ZStream.fromFile(Paths.get("README.md")))

    // Uses netty's capability to write file content to the Channel
    // Content-type response headers are automatically identified and added
    // Does not use Chunked transfer encoding
    case Method.GET -> !! / "video" => Http.fromFile(new File("src/main/resources/TestVideoFile.mp4"))
    case Method.GET -> !! / "text"  => Http.fromFile(new File("src/main/resources/TestFile.txt"))
  }

  // Run it like any simple app
  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    Server.start(8090, app).exitCode
}
