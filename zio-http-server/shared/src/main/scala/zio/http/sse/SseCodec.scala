package zio.http.sse

import java.nio.charset.StandardCharsets

import zio.blocks.chunk.Chunk

/**
 * Native Server-Sent Events framing (WHATWG HTML, server-sent events).
 *
 * `encode` frames exactly one [[ServerSentEvent]] per call: optional
 * `event:` line, one `data:` line per data line, optional `id:`/`retry:`
 * lines, then the blank-line terminator. Callers stream events with
 * `Stream#flatMap` over [[encode]] so each event is framed as it flows —
 * never collected into one materialized [[Chunk]].
 */
object SseCodec {

  /**
   * Spec heartbeat: a comment line the client ignores, keeping intermediaries
   * from closing an idle stream.
   */
  val heartbeat: Chunk[Byte] =
    Chunk.fromArray(": ping\n\n".getBytes(StandardCharsets.UTF_8))

  /**
   * Frames one event to its exact wire bytes (UTF-8).
   */
  def encode(event: ServerSentEvent): Chunk[Byte] =
    Chunk.fromArray(render(event).getBytes(StandardCharsets.UTF_8))

  private def render(event: ServerSentEvent): String = {
    val out = new StringBuilder()
    event.event.foreach { name =>
      out.append("event: ").append(name).append('\n')
    }
    // SSE spec: a data value is split on CRLF, LF, and lone CR alike.
    val lines = event.data.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1)
    var i     = 0
    while (i < lines.length) {
      out.append("data: ").append(lines(i)).append('\n')
      i += 1
    }
    event.id.foreach { id =>
      out.append("id: ").append(id).append('\n')
    }
    event.retry.foreach { retry =>
      out.append("retry: ").append(retry.toMillis).append('\n')
    }
    out.append('\n')
    out.toString()
  }
}
