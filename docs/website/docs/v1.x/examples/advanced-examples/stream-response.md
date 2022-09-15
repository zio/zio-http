# Streaming Response

```scala
import zio.http._
import zio.http.Server
import zio.stream.ZStream
import zio._

/**
 * Example to encode content using a ZStream
 */
object StreamingResponse extends App {
  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = {

    // Starting the server (for more advanced startup configuration checkout `HelloWorldAdvanced`)
    Server.start(8090, app.silent).exitCode
  }

  // Create a message as a Chunk[Byte]
  val message                    = Chunk.fromArray("Hello world !\r\n".getBytes(HTTP_CHARSET))
  // Use `Http.collect` to match on route
  val app: HttpApp[Any, Nothing] = Http.collect[Request] {

    // Simple (non-stream) based route
    case Method.GET -> !! / "health" => Response.ok

    // ZStream powered response
    case Method.GET -> !! / "stream" =>
      Response(
        status = Status.OK,
        headers = Headers.contentLength(message.length.toLong),
        body = Body.fromStream(ZStream.fromChunk(message)), // Encoding content using a ZStream
      )
  }
}
```