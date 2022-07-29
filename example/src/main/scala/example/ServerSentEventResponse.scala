package example

import zhttp.http._
import zhttp.service.Server
import zio._
import zio.stream.ZStream
import zhttp.http.HttpData.ServerSentEvent._

/**
 * Example to encode content using a ZStream
 */
object ServerSentEventResponse extends ZIOAppDefault {
  // Starting the server (for more advanced startup configuration checkout `HelloWorldAdvanced`)
  def run = Server.start(8090, app)

  // Create a message as a Chunk[Byte]
  def message                    = Chunk.fromArray("Hello world !\r\n".getBytes(HTTP_CHARSET))

  // Create a stream of Server-Sent-Events
  def stream = ZStream.repeat(Event("my-data", Some("message"), Some("my-id"), None)).schedule(Schedule.spaced(1.seconds) && Schedule.recurs(10))

  // Use `Http.collect` to match on route
  def app: HttpApp[Any, Nothing] = Http.collect[Request] {



    // Simple (non-stream) based route
    case Method.GET -> !! / "health" => Response.ok

    // ZStream powered response
    case Method.GET -> !! / "stream" =>
      Response(
        status = Status.Ok,
        headers = Headers.contentType(s"text/event-stream; charset=utf-8") ++ Headers.cacheControl("no-cache") ++ Headers.connection("keep-alive") ++ Headers.accessControlAllowOrigin("*"),
        data = HttpData.fromEventStream(stream), // Encoding content using a ZStream
      )
  }
}
