package zio.http.sse

import zio.blocks.streams.Stream

import zio.http.{Body, ContentType, Header, Response, Status}

/**
 * `text/event-stream` constructors.
 *
 * `Body`/`Response` ship in the external `http-model` binary dependency, so
 * these constructors cannot live on their companions. They are provided here
 * — in this repo, next to the server that serves them — as plain factories
 * plus implicit companion syntax (`Body.sse(...)`, `Response.sse(...)`),
 * which resolves through the implicit scope of the imported [[Sse]] object.
 */
object Sse {

  private val eventStreamContentType: ContentType =
    ContentType.parse("text/event-stream").fold(
      err => throw new IllegalArgumentException("Invalid SSE content type: " + err),
      identity,
    )

  /**
   * Builds an unknown-length event-stream body: each event is framed by
   * [[SseCodec.encode]] as the stream flows (one `flatMap` step per event),
   * so an unbounded event stream never lands on the heap at once. Downstream
   * (Todo 6 `sendStreamedBody`) chunks it by `maxFrameSize` with no
   * Content-Length.
   */
  def body(events: Stream[Nothing, ServerSentEvent]): Body =
    Body.fromStream(
      events.flatMap(event => Stream.fromChunk(SseCodec.encode(event))),
      eventStreamContentType,
    )

  /**
   * Builds a `200 OK` event-stream response (`Content-Type:
   * text/event-stream`, `Cache-Control: no-cache`) over a streamed body.
   */
  def response(events: Stream[Nothing, ServerSentEvent]): Response =
    Response(status = Status.Ok, body = body(events))
      .addHeader(Header.ContentType(eventStreamContentType))
      .addHeader(Header.CacheControl.NoCache)

  implicit final class BodySseOps(private val self: Body.type) {
    def sse(events: Stream[Nothing, ServerSentEvent]): Body =
      Sse.body(events)
  }

  implicit final class ResponseSseOps(private val self: Response.type) {
    def sse(events: Stream[Nothing, ServerSentEvent]): Response =
      Sse.response(events)
  }
}
