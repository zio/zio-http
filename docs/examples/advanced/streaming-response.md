---
id: streaming-response
title: "Streaming Response Example"
sidebar_label: "Streaming Response"
---

```scala mdoc
import zio.{http, _}
import zio.stream.ZStream
import zio.http._

/**
 * Example to encode content using a ZStream
 */
object StreamingResponse extends ZIOAppDefault {
  // Starting the server (for more advanced startup configuration checkout `HelloWorldAdvanced`)
  def run = Server.serve(app).provide(Server.default)

  // Create a message as a Chunk[Byte]
  def message                    = Chunk.fromArray("Hello world !\r\n".getBytes(Charsets.Http))
  // Use `Http.collect` to match on route
  def app: HttpApp[Any, Nothing] = Http.collect[Request] {

    // Simple (non-stream) based route
    case Method.GET -> !! / "health" => Response.ok

    // ZStream powered response
    case Method.GET -> !! / "stream" =>
      http.Response(
        status = Status.Ok,
        headers = Headers(Header.ContentLength(message.length.toLong)),
        body = Body.fromStream(ZStream.fromChunk(message)), // Encoding content using a ZStream
      )
  }
}

```