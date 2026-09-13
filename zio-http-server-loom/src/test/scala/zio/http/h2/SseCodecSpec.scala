package zio.http.h2

import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicInteger

import scala.concurrent.duration.{Duration, MILLISECONDS}

import zio._
import zio.blocks.chunk.Chunk
import zio.blocks.streams.Stream
import zio.test._

import zio.http.{Body, Response, Status}
import zio.http.sse.Sse._
import zio.http.sse.{ServerSentEvent, SseCodec}

/**
 * Todo 8: native SSE codec. `SseCodec.encode` frames one [[ServerSentEvent]]
 * per call (data split on \n into multiple `data:` lines, optional
 * `event:`/`id:`/`retry:` lines, blank-line terminator); `Body.sse` maps the
 * event stream per-event so payloads are never materialized into one Chunk.
 */
object SseCodecSpec extends ZIOSpecDefault {

  private val Utf8 = StandardCharsets.UTF_8

  private def bytes(s: String): Chunk[Byte] =
    Chunk.fromArray(s.getBytes(Utf8))

  private def text(chunk: Chunk[Byte]): String =
    new String(chunk.toArray[Byte], Utf8)

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("SseCodecSpec")(
      test("multiline event encodes to exact spec framing") {
        val event   = ServerSentEvent("a\nb", Some("update"), Some("1"))
        val encoded = SseCodec.encode(event)
        assertTrue(text(encoded) == "event: update\ndata: a\ndata: b\nid: 1\n\n")
      },
      test("heartbeat is a spec comment line") {
        assertTrue(text(SseCodec.heartbeat) == ": ping\n\n")
      },
      test("CRLF and CR in data are line breaks per spec") {
        val event = ServerSentEvent("a\r\nb\rc")
        assertTrue(text(SseCodec.encode(event)) == "data: a\ndata: b\ndata: c\n\n")
      },
      test("empty data still emits a data line") {
        assertTrue(text(SseCodec.encode(ServerSentEvent(""))) == "data: \n\n")
      },
      test("unicode payload round-trips byte-exact") {
        val data  = "héllo-世界-🎉"
        val event = ServerSentEvent(data)
        assertTrue(text(SseCodec.encode(event)) == s"data: $data\n\n")
      },
      test("retry renders in milliseconds per spec") {
        val event = ServerSentEvent("x", retry = Some(Duration(1500, MILLISECONDS)))
        assertTrue(text(SseCodec.encode(event)) == "data: x\nretry: 1500\n\n")
      },
      test("huge single event framing survives downstream chunking") {
        val data    = "z" * (1024 * 1024)
        val encoded = text(SseCodec.encode(ServerSentEvent(data)))
        assertTrue(
          encoded.startsWith("data: "),
          encoded.endsWith("\n\n"),
          encoded.length == data.length + "data: \n\n".length,
        )
      },
      test("Body.sse emits per-event without materializing") {
        val total                                    = 100
        val pulls                                    = new AtomicInteger(0)
        val events: Stream[Nothing, ServerSentEvent] =
          Stream.unfold(0) { i =>
            if (i >= total) None
            else {
              pulls.incrementAndGet()
              Some((ServerSentEvent(s"e$i"), i + 1))
            }
          }
        val body                                     = Body.sse(events)
        val firstLen                                 = text(SseCodec.encode(ServerSentEvent("e0"))).length
        val head                                     = body.toStream.take(firstLen).runCollect match {
          case Right(chunk) => chunk
          case Left(_)      => Chunk.empty[Byte]
        }
        assertTrue(
          text(head) == text(SseCodec.encode(ServerSentEvent("e0"))),
          pulls.get() > 0,
          pulls.get() < total,
          body.toStream.knownChunk.isEmpty,
          body.length.isEmpty,
        )
      },
      test("Body.sse concatenates per-event encodings in order") {
        val events   = List(
          ServerSentEvent("a", Some("one"), Some("1")),
          ServerSentEvent("b\nc", None, Some("2")),
          ServerSentEvent("d", retry = Some(Duration(250, MILLISECONDS))),
        )
        val body     = Body.sse(Stream.fromIterable(events))
        val actual   = body.toStream.runCollect match {
          case Right(chunk) => text(chunk)
          case Left(_)      => ""
        }
        val expected = events.map(e => text(SseCodec.encode(e))).mkString
        assertTrue(actual == expected)
      },
      test("Response.sse carries event-stream headers and streamed body") {
        val events   = List(ServerSentEvent("hi", Some("greet"), Some("7")))
        val response = Response.sse(Stream.fromIterable(events))
        val headers  = response.headers.toList.map { case (k, v) => k.toLowerCase -> v }
        val actual   = response.body.toStream.runCollect match {
          case Right(chunk) => text(chunk)
          case Left(_)      => ""
        }
        assertTrue(
          response.status == Status.Ok,
          headers.contains("content-type" -> "text/event-stream"),
          headers.exists { case (k, v) => k == "cache-control" && v.contains("no-cache") },
          actual == "event: greet\ndata: hi\nid: 7\n\n",
          response.body.length.isEmpty,
        )
      },
    )
}
