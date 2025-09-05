//> using dep "dev.zio::zio-http:3.4.1"

package example

import java.io.File
import java.nio.file.Paths

import zio._

import zio.stream.ZStream

import zio.http._

object FileStreaming extends ZIOAppDefault {

  // Create HTTP route
  val app = Routes(
    Method.GET / "health" -> Handler.ok,

    // Read the file as ZStream
    // Uses the blocking version of ZStream.fromFile
    Method.GET / "blocking" -> Handler.fromStreamChunked(ZStream.fromPath(Paths.get("README.md"))),

    // Uses netty's capability to write file content to the Channel
    // Content-type response headers are automatically identified and added
    // Adds content-length header and does not use Chunked transfer encoding
    Method.GET / "video" -> Handler.fromFile(new File("src/main/resources/TestVideoFile.mp4")),
    Method.GET / "text"  -> Handler.fromFile(new File("src/main/resources/TestFile.txt")),
  ).sandbox

  // Run it like any simple app
  val run =
    Server.serve(app).provide(Server.default)
}
