---
id: streaming-file
title: "Streaming File Example"
---

This code showcases the utilization of ZIO HTTP to enable file streaming in an HTTP server.

```scala mdoc
import java.io.File
import java.nio.file.Paths

import zio._

import zio.stream.ZStream

import zio.http._

object FileStreaming extends ZIOAppDefault {

  // Create HTTP route
  val app = Http.collectHttp[Request] {
    case Method.GET -> Root / "health" => Handler.ok.toHttp

    // Read the file as ZStream
    // Uses the blocking version of ZStream.fromFile
    case Method.GET -> Root / "blocking" => Handler.fromStream(ZStream.fromPath(Paths.get("README.md"))).toHttp

    // Uses netty's capability to write file content to the Channel
    // Content-type response headers are automatically identified and added
    // Adds content-length header and does not use Chunked transfer encoding
    case Method.GET -> Root / "video" => Http.fromFile(new File("src/main/resources/TestVideoFile.mp4"))
    case Method.GET -> Root / "text"  => Http.fromFile(new File("src/main/resources/TestFile.txt"))
  }

  // Run it like any simple app
  val run =
    Server.serve(app.withDefaultErrorResponse).provide(Server.default)
}

```