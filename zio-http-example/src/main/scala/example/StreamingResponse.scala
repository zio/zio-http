package example

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
  def message = Chunk.fromArray("Hello world !\r\n".getBytes(Charsets.Http))

  def app: HttpApp[Any] = Routes(
    // Simple (non-stream) based route
    Method.GET / "health" -> handler(Response.ok),

    // ZStream powered response
    Method.GET / "stream" ->
      handler(
        http.Response(
          status = Status.Ok,
          headers = Headers(Header.ContentLength(message.length.toLong)),
          body = Body.fromStream(ZStream.fromChunk(message)), // Encoding content using a ZStream
        ),
      ),
  ).toHttpApp
}
