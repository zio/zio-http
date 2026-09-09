package zio.http.h2

import java.nio.charset.StandardCharsets

import scala.annotation.experimental
import scala.concurrent.duration.{Duration, MILLISECONDS}

import zio._
import zio.blocks.chunk.Chunk
import zio.test._

import zio.http.sse.{ServerSentEvent, SseCodec}

/**
 * MAJOR-3 grammar tables for the native SSE codec.
 *
 * SCOPE NOTE (decoder-absence verdict): there is NO SSE event-stream
 * parser/decoder anywhere in this repo. Evidence:
 *   - `SseCodec`
 *     (`zio-http-server/shared/src/main/scala/zio/http/sse/SseCodec.scala`)
 *     exposes only `encode` and `heartbeat` — no `decode`/`parse` member.
 *   - repo-wide `SseCodec.` usages are encode-only (`Sse.body`, `SseCodecSpec`,
 *     `SseDelayIntegrationSpec`, `SseEndToEndSpec`); no `EventSource`, client
 *     reader, or test-harness event parser exists (harnesses split bytes on
 *     `\n\n` only, never parsing fields).
 *   - `Sse`/`Body.sse`/`Response.sse` are send-side constructors.
 *
 * So these tables pin the ENCODER over the same edge inputs the reviewer
 * listed: byte-exact canonical framing per row, cross-checked against the
 * strict inline reference framer below (which never calls [[SseCodec]]).
 * Decode-side grammar (colon-space optionality, `\r\n` dispatch, blank-line
 * dispatch, `retry:` validation, unknown-field tolerance, BOM/whitespace before
 * field names, unterminated trailing events) is UNPINNED by construction — it
 * belongs to a future decoder lane, not this encoder.
 *
 * RED history (scratch probe, since removed): naive hypotheses failed for the
 * right reasons — `"a\n"` framed as `"data: a\ndata: \n\n"` (split `-1`
 * contract), `Some("")` event/id still emit their line, CRLF canonicalizes to
 * LF. GREEN below pins those actuals.
 *
 * FIXED (construction-time validation in `ServerSentEvent`): `event:`/`id:`
 * values containing CR/LF are rejected with `IllegalArgumentException` naming
 * the field — the "construction rejects ..." and "fixed limits ..." suites pin
 * the IAE, and multiline `data` remains legitimate framing.
 */
@experimental
object SseGrammarSpec extends ZIOSpecDefault {

  private val Utf8 = StandardCharsets.UTF_8

  private def text(chunk: Chunk[Byte]): String =
    new String(chunk.toArray[Byte], Utf8)

  /**
   * Strict reference framer: independent reimplementation of the encode
   * contract (explicit char scan splitting on CRLF, lone CR, and LF alike;
   * `split(-1)` trailing-segment semantics; canonical field order
   * event/data/id/retry plus the blank-line terminator; LF-only output). Never
   * calls [[SseCodec]].
   */
  private def referenceFrame(
    data: String,
    event: Option[String],
    id: Option[String],
    retryMs: Option[Long],
  ): String = {
    val lines = List.newBuilder[String]
    val cur   = new StringBuilder()
    var i     = 0
    while (i < data.length) {
      val c = data.charAt(i)
      if (c == '\r') {
        lines += cur.toString()
        cur.clear()
        if (i + 1 < data.length && data.charAt(i + 1) == '\n') i += 1
      } else if (c == '\n') {
        lines += cur.toString()
        cur.clear()
      } else {
        cur.append(c)
      }
      i += 1
    }
    lines += cur.toString()
    val out   = new StringBuilder()
    event.foreach(name => out.append("event: ").append(name).append('\n'))
    lines.result().foreach(line => out.append("data: ").append(line).append('\n'))
    id.foreach(value => out.append("id: ").append(value).append('\n'))
    retryMs.foreach(ms => out.append("retry: ").append(ms).append('\n'))
    out.append('\n')
    out.toString()
  }

  private def retry(ms: Long): Some[Duration] =
    Some(Duration(ms, MILLISECONDS))

  private def encodesTo(event: ServerSentEvent, expected: String): TestResult =
    assertTrue(text(SseCodec.encode(event)) == expected)

  private def constructionMessage(build: => ServerSentEvent): String =
    try {
      build
      "<no failure>"
    } catch {
      case error: IllegalArgumentException => error.getMessage
    }

  private def matchesReference(event: ServerSentEvent, retryMs: Option[Long]): TestResult = {
    val actual   = text(SseCodec.encode(event))
    val expected = referenceFrame(event.data, event.event, event.id, retryMs)
    assertTrue(actual == expected)
  }

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("SseGrammarSpec")(
      suite("line endings canonicalize to LF framing")(
        test("LF splits into one data line per line") {
          encodesTo(ServerSentEvent("a\nb\nc"), "data: a\ndata: b\ndata: c\n\n")
        },
        test("CRLF canonicalizes to LF framing") {
          val actual = text(SseCodec.encode(ServerSentEvent("a\r\nb")))
          assertTrue(actual == "data: a\ndata: b\n\n", !actual.contains("\r"))
        },
        test("lone CR is a line break") {
          val actual = text(SseCodec.encode(ServerSentEvent("a\rb")))
          assertTrue(actual == "data: a\ndata: b\n\n", !actual.contains("\r"))
        },
        test("mixed CRLF CR LF split into four data lines") {
          val actual = text(SseCodec.encode(ServerSentEvent("a\r\nb\rc\nd")))
          assertTrue(actual == "data: a\ndata: b\ndata: c\ndata: d\n\n", !actual.contains("\r"))
        },
        test("trailing LF emits a trailing empty data line") {
          encodesTo(ServerSentEvent("a\n"), "data: a\ndata: \n\n")
        },
        test("lone LF yields two empty data lines") {
          encodesTo(ServerSentEvent("\n"), "data: \ndata: \n\n")
        },
        test("lone CRLF yields two empty data lines") {
          encodesTo(ServerSentEvent("\r\n"), "data: \ndata: \n\n")
        },
      ),
      suite("edge fields frame byte-exact")(
        test("empty event name still emits its line") {
          encodesTo(ServerSentEvent("x", event = Some("")), "event: \ndata: x\n\n")
        },
        test("empty id still emits its line") {
          encodesTo(ServerSentEvent("x", id = Some("")), "data: x\nid: \n\n")
        },
        test("full event frames in canonical field order") {
          val event = ServerSentEvent("a\nb", Some("t"), Some("7"), retry(250L))
          encodesTo(event, "event: t\ndata: a\ndata: b\nid: 7\nretry: 250\n\n")
        },
        test("zero retry renders retry 0") {
          encodesTo(ServerSentEvent("x", retry = retry(0L)), "data: x\nretry: 0\n\n")
        },
        test("huge retry renders full millis without overflow") {
          encodesTo(
            ServerSentEvent("x", retry = retry(999999999999L)),
            "data: x\nretry: 999999999999\n\n",
          )
        },
        test("negative retry renders verbatim") {
          encodesTo(ServerSentEvent("x", retry = retry(-500L)), "data: x\nretry: -500\n\n")
        },
        test("colon-leading payload lines frame verbatim") {
          encodesTo(
            ServerSentEvent(": comment-like\ndata-ish"),
            "data: : comment-like\ndata: data-ish\n\n",
          )
        },
      ),
      suite("unicode and BOM payloads")(
        test("BOM-bearing data is preserved with LF framing") {
          encodesTo(ServerSentEvent("\uFEFFhi"), "data: \uFEFFhi\n\n")
        },
        test("BOM after CRLF splits and preserves the BOM") {
          val actual = text(SseCodec.encode(ServerSentEvent("a\r\n\uFEFFb")))
          assertTrue(actual == "data: a\ndata: \uFEFFb\n\n", !actual.contains("\r"))
        },
        test("unicode payload frames byte-exact") {
          val data = "héllo-世界-🎉"
          encodesTo(ServerSentEvent(data), "data: " + data + "\n\n")
        },
      ),
      suite("bulk framing")(
        test("multi-thousand-line data frames every line plus terminator") {
          val lineCount = 3000
          val data      = (1 to lineCount).map(i => "l" + i).mkString("\n")
          val actual    = text(SseCodec.encode(ServerSentEvent(data)))
          val lines     = actual.split("\n", -1).toList
          assertTrue(
            lines.length == lineCount + 2,
            lines.take(lineCount).zipWithIndex.forall { case (line, idx) => line == "data: l" + (idx + 1) },
            lines(lineCount) == "",
            lines(lineCount + 1) == "",
            actual == referenceFrame(data, None, None, None),
          )
        },
        test("heartbeat interleaves byte-exact between events") {
          val first    = text(SseCodec.encode(ServerSentEvent("e0")))
          val second   = text(SseCodec.encode(ServerSentEvent("e1")))
          val expected = first + ": ping\n\n" + second
          assertTrue(text(SseCodec.heartbeat) == ": ping\n\n", expected == "data: e0\n\n: ping\n\ndata: e1\n\n")
        },
      ),
      suite("reference cross-check pins the whole table")(
        test("encoder matches the strict reference framer on every grammar row") {
          val rows      = List(
            ServerSentEvent(""),
            ServerSentEvent("hello"),
            ServerSentEvent("a\nb\nc"),
            ServerSentEvent("a\r\nb"),
            ServerSentEvent("a\rb"),
            ServerSentEvent("a\r\nb\rc\nd"),
            ServerSentEvent("a\n"),
            ServerSentEvent("\n"),
            ServerSentEvent("\r\n"),
            ServerSentEvent("x", event = Some("")),
            ServerSentEvent("x", id = Some("")),
            ServerSentEvent("a\nb", Some("t"), Some("7"), retry(250L)),
            ServerSentEvent("x", retry = retry(0L)),
            ServerSentEvent("x", retry = retry(999999999999L)),
            ServerSentEvent("\uFEFFhi"),
            ServerSentEvent(": comment-like\ndata-ish"),
            ServerSentEvent("héllo-世界-🎉"),
          )
          val retryMsOf = (e: ServerSentEvent) => e.retry.map(_.toMillis)
          assertTrue(rows.forall(e => text(SseCodec.encode(e)) == referenceFrame(e.data, e.event, e.id, retryMsOf(e))))
        },
        test("wire bytes are the exact UTF-8 encoding of the framed string") {
          val event    = ServerSentEvent("a\r\nb", Some("t"), Some("7"), retry(1500L))
          val expected = "event: t\ndata: a\ndata: b\nid: 7\nretry: 1500\n\n"
          val encoded  = SseCodec.encode(event)
          assertTrue(
            text(encoded) == expected,
            encoded.toArray[Byte].toList == expected.getBytes(Utf8).toList,
          )
        },
      ),
      suite("construction rejects event/id framing injection (RED-first)")(
        test("event value with LF fails construction with IAE naming event") {
          assertTrue(constructionMessage(ServerSentEvent("x", event = Some("a\nb"))).contains("event"))
        },
        test("event value with CR fails construction with IAE naming event") {
          assertTrue(constructionMessage(ServerSentEvent("x", event = Some("a\rb"))).contains("event"))
        },
        test("id value with LF fails construction with IAE naming id") {
          assertTrue(constructionMessage(ServerSentEvent("x", id = Some("1\n2"))).contains("id"))
        },
        test("id value with CR fails construction with IAE naming id") {
          assertTrue(constructionMessage(ServerSentEvent("x", id = Some("1\r2"))).contains("id"))
        },
        test("multiline data still frames as legitimate multi-line data") {
          encodesTo(ServerSentEvent("a\nb"), "data: a\ndata: b\n\n")
        },
      ),
      suite("fixed limits: event/id injection rejected at construction")(
        test("FIXED SseCodec.scala:33-35 event value with LF is rejected, no split framing") {
          assertTrue(constructionMessage(ServerSentEvent("x", event = Some("a\nb"))).contains("event"))
        },
        test("FIXED SseCodec.scala:43-45 id value with LF is rejected, no split framing") {
          assertTrue(constructionMessage(ServerSentEvent("x", id = Some("1\n2"))).contains("id"))
        },
      ),
    )
}
