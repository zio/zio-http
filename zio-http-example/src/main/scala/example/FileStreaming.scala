package example

import zio._
import zio.http._
import zio.http.model.Method
import zio.stream.ZStream

import java.io.File
import java.nio.file.Paths

object FileStreaming extends ZIOAppDefault {

  // Create HTTP route
  val app = Route.collectHandler[Request] {
    case Method.GET -> !! / "health" => Handler.ok

    // Read the file as ZStream
    // Uses the blocking version of ZStream.fromFile
    case Method.GET -> !! / "blocking" => Handler.fromStream(ZStream.fromPath(Paths.get("README.md")))

    // Uses netty's capability to write file content to the Channel
    // Content-type response headers are automatically identified and added
    // Adds content-length header and does not use Chunked transfer encoding
    case Method.GET -> !! / "video" => Handler.fromFile(new File("src/main/resources/TestVideoFile.mp4"))
    case Method.GET -> !! / "text"  => Handler.fromFile(new File("src/main/resources/TestFile.txt"))
  }

  // Run it like any simple app
  val run =
    Server.serve(app.withDefaultErrorResponse).provide(Server.default)
}
