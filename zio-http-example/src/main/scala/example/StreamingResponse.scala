//> using dep "dev.zio::zio-http:3.4.1"
//> using dep "dev.zio::zio-streams:2.1.18"

package example

import zio.{http, _}

import zio.stream.ZStream

import zio.http._

/**
 * Example to encode content using a ZStream
 */
object StreamingResponse extends ZIOAppDefault {
  // Starting the server (for more advanced startup configuration checkout `HelloWorldAdvanced`)
  def run = Server.serve(routes).provide(Server.default)

  // Create a message as a Chunk[Byte]
  def message = Chunk.fromArray("Hello world !\r\n".getBytes(Charsets.Http))

  def routes: Routes[Any, Response] = Routes(
    // Simple (non-stream) based route
    Method.GET / "health" -> handler(Response.ok),

    // ZStream powered response
    Method.GET / "stream" ->
      handler(
        http.Response(
          status = Status.Ok,
          body = Body.fromStream(ZStream.fromChunk(message), message.length.toLong), // Encoding content using a ZStream
        ),
      ),
  )
}
