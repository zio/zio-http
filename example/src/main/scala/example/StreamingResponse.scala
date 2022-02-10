package example

import zhttp.http._
import zhttp.service.Server
import zio._
import zio.stream.ZStream

/**
 * Example to encode content using a ZStream
 */
object StreamingResponse extends ZIOAppDefault {
  // Starting the server (for more advanced startup configuration checkout `HelloWorldAdvanced`)
  def run = Server.start(8090, app)

  // Create a message as a Chunk[Byte]
  def message                    = Chunk.fromArray("Hello world !\r\n".getBytes(HTTP_CHARSET))
  // Use `Http.collect` to match on route
  def app: HttpApp[Any, Nothing] = Http.collect[Request] {

    // Simple (non-stream) based route
    case Method.GET -> !! / "health" => Response.ok

    // ZStream powered response
    case Method.GET -> !! / "stream" =>
      Response(
        status = Status.OK,
        headers = Headers.contentLength(message.length.toLong),
        data = HttpData.fromStream(ZStream.fromChunk(message)), // Encoding content using a ZStream
      )
  }
}
