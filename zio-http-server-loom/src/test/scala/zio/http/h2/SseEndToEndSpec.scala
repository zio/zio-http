package zio.http.h2

import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicInteger

import scala.annotation.experimental
import scala.collection.mutable.ListBuffer

import zio._
import zio.blocks.context.Context
import zio.blocks.endpoint.RoutePattern
import zio.blocks.streams.Stream
import zio.test.TestAspect.sequential
import zio.test._

import zio.http.ResultType._
import zio.http.sse.Sse._
import zio.http.sse.{ServerSentEvent, SseCodec}
import zio.http.{
  BindAddress,
  Body,
  BoundAddress,
  Connector,
  Http2Config,
  LoomServer,
  Method,
  Path,
  PooledLoomH2Client,
  Protocol,
  QueryParams,
  Request,
  Response,
  Route,
  Routes,
  Status,
  URL,
  handler,
}

/**
 * Todo 17: end-to-end composition over the real [[LoomServer]] (H2C) and the
 * pooled client — three legs, each asserting client-observed observables:
 *
 *   1. SSE delay leg: `Response.sse` paced events (5 @ 200ms, T9 harness shape)
 *      served by [[LoomServer]] and consumed through [[PooledLoomH2Client]]'s
 *      lazy body; inter-arrival gaps are stamped at SSE event boundaries
 *      (`\n\n`) on the incremental byte fold, and the concatenated bytes must
 *      equal the [[SseCodec]] wire encoding exactly.
 *   2. Endpoint-shape round-trip leg: the exact request T12 proves
 *      `EndpointBridge.buildRequest` renders (`GET /users/42?active=true` +
 *      `X-Trace` + JSON body, never `URL.root` — see `EndpointRoundTripSpec`)
 *      is built here, sent through the pool to a [[LoomServer]] echo route,
 *      and decoded back to equality. T12 owns request-rendering proof; this
 *      leg owns transport proof — together they cover
 *      buildRequest → pool → server → decode without re-proving the walker.
 *   3. Pool/streaming leg: N sequential requests through one pool reuse a
 *      single connection (`stats.idle == 1`), and a 200KB unknown-length body
 *      streams lazily (`knownChunk.isEmpty`) byte-exact under flow control
 *      (T15 stub idioms, here against the real server).
 *
 * Every transfer larger than 64KB relies on the pooled client's own
 * RFC 9113 §6.9 window management (T15); every server/client pair uses
 * ephemeral ports with `acquireRelease` (server) and try/finally `close()`
 * (pool) — no stray threads, servers, ports, or files.
 */
@experimental
object SseEndToEndSpec extends ZIOSpecDefault {

  private val Utf8 = StandardCharsets.UTF_8

  private val SseEventCount = 5
  private val SseSpacingMs  = 200L

  /** Lower/upper bound (ms) per inter-arrival gap on 200ms spacing. */
  private val GapLowerMs = 150L
  private val GapUpperMs = 1000L

  private val BigBodyBytes = 200 * 1024

  /** Lazily-paced events (T9 `pacedEvents` shape): sleep before every event. */
  private def pacedEvents(total: Int, spacingMs: Long): Stream[Nothing, ServerSentEvent] =
    Stream.unfold(0) { i =>
      if (spacingMs > 0L) Thread.sleep(spacingMs)
      if (i >= total) None
      else Some((ServerSentEvent("e" + i), i + 1))
    }

  /** Lazily-generated deterministic bytes (T16 `unfoldingBytes` shape). */
  private def unfoldingBytes(total: Int): Stream[Nothing, Byte] =
    Stream.unfold(0) { i =>
      if (i >= total) None
      else Some((((i % 251) & 0xff).toByte, i + 1))
    }

  private def absUrl(raw: String): URL =
    URL.parse(raw).fold(err => throw new IllegalArgumentException("Invalid test URL: " + err), identity)

  private def proof(line: String): Unit =
    println("[SseEndToEndSpec] " + line)

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("SseEndToEndSpec")(
      test("SSE delay leg: paced events arrive spaced through the pooled client, bytes exact") {
        val routes = Routes(
          Route(
            RoutePattern.GET,
            handler { (_: Request) =>
              responseAsResult(Response.sse(pacedEvents(SseEventCount, SseSpacingMs)))
            },
          ),
        )
        withLoom(routes) { port =>
          ZIO.attemptBlocking {
            val pool = PooledLoomH2Client(zio.http.ClientConfig())
            try {
              val response = pool.send(Request.get(absUrl("http://127.0.0.1:" + port + "/")))
              val headOk   = response.status == Status.Ok &&
                response.headers.rawGet("Content-Type").exists(_.contains("text/event-stream"))
              val bodyStream = response.body.toStream
              val lazyProof  = bodyStream.knownChunk.isEmpty
              val buf        = new StringBuilder()
              val gaps       = ListBuffer.empty[Long]
              var lastNs     = 0L
              var events     = 0
              val count      = bodyStream.runFold(0L) { (n: Long, b: Byte) =>
                buf.append((b & 0xff).toChar)
                val len = buf.length
                if (len >= 2 && buf.charAt(len - 1) == '\n' && buf.charAt(len - 2) == '\n') {
                  events += 1
                  val now = java.lang.System.nanoTime()
                  if (lastNs != 0L) gaps += ((now - lastNs) / 1000000L)
                  lastNs = now
                }
                n + 1L
              } match {
                case Right(n) => n
                case Left(_)  => -1L
              }
              val expected = (0 until SseEventCount)
                .map(i => new String(SseCodec.encode(ServerSentEvent("e" + i)).toArray, Utf8))
                .mkString
              val stats = pool.stats
              proof(
                "sse-delay gapsMs=" + gaps.toList + " events=" + events +
                  " bytes=" + count + " lazy=" + lazyProof + " stats=" + stats,
              )
              (headOk, lazyProof, events, gaps.toList, buf.toString, expected, count, stats)
            } finally pool.close()
          }.map { case (headOk, lazyProof, events, gaps, actual, expected, count, stats) =>
            assertTrue(
              headOk,
              lazyProof,
              events == SseEventCount,
              count == expected.length.toLong,
              actual == expected,
              gaps.length == SseEventCount - 1,
              gaps.forall(g => g >= GapLowerMs && g <= GapUpperMs),
              stats.checkedOut == 0,
            )
          }
        }
      },
      test("endpoint-shape round-trip leg: T12 request bytes survive pool plus server to equality") {
        val routes = Routes(
          Route(
            RoutePattern(Method.GET, "/users/42"),
            handler { (request: Request) =>
              val bodyStr = new String(request.body.toArray, Utf8)
              val active  = request.url.queryParams.getFirst("active").getOrElse("<missing>")
              val trace   = request.headers.rawGet("X-Trace").getOrElse("<missing>")
              val echo    = request.url.path.encode + "|" + active + "|" + trace + "|" + bodyStr
              responseAsResult(Response.text(echo))
            },
          ),
        )
        withLoom(routes) { port =>
          ZIO.attemptBlocking {
            val pool = PooledLoomH2Client(zio.http.ClientConfig())
            try {
              // Byte-identical to what T12 proves buildRequest renders
              // (EndpointRoundTripSpec: path /users/42, ?active=true,
              // X-Trace, JSON body, never URL.root).
              val base    = absUrl("http://127.0.0.1:" + port + "/users/42")
              val url     = base.addQueryParams(QueryParams("active" -> "true"))
              val request = Request
                .post(url, Body.fromString("\"payload\""))
                .copy(method = Method.GET)
                .addHeader("X-Trace", "abc")
                .addHeader("Content-Type", "application/json")
              val renderOk =
                request.url.path != Path.root &&
                  request.url.path.encode == "/users/42" &&
                  request.url.queryParams.getFirst("active") == Some("true") &&
                  request.headers.rawGet("X-Trace") == Some("abc")
              val response = pool.send(request)
              val actual   = new String(response.body.toArray, Utf8)
              val expected = "/users/42|true|abc|\"payload\""
              val stats    = pool.stats
              proof("endpoint-roundtrip renderOk=" + renderOk + " actual=" + actual + " stats=" + stats)
              (renderOk, response.status, actual, expected, stats)
            } finally pool.close()
          }.map { case (renderOk, status, actual, expected, stats) =>
            assertTrue(
              renderOk,
              status == Status.Ok,
              actual == expected,
              stats.checkedOut == 0,
            )
          }
        }
      },
      test("pool/streaming leg: sequential reuse plus large-body lazy streaming through the pool") {
        val routes = Routes(
          Route(
            RoutePattern.GET,
            handler { (_: Request) =>
              responseAsResult(Response(status = Status.Ok, body = Body.fromString("pool-ok")))
            },
          ),
          Route(
            RoutePattern(Method.GET, "/big"),
            handler { (_: Request) =>
              responseAsResult(Response(status = Status.Ok, body = Body.fromStream(unfoldingBytes(BigBodyBytes))))
            },
          ),
        )
        withLoom(routes) { port =>
          ZIO.attemptBlocking {
            val pool = PooledLoomH2Client(zio.http.ClientConfig())
            try {
              val bodies = (1 to 5).map { _ =>
                val response = pool.send(Request.get(absUrl("http://127.0.0.1:" + port + "/")))
                if (response.status != Status.Ok) throw new AssertionError("status: " + response.status)
                new String(response.body.toArray, Utf8)
              }.toList
              val afterReuse = pool.stats
              val big        = pool.send(Request.get(absUrl("http://127.0.0.1:" + port + "/big")))
              val bigStream  = big.body.toStream
              val lazyProof  = bigStream.knownChunk.isEmpty
              val mismatches = new AtomicInteger(0)
              val total      = bigStream.runFold(0L) { (idx: Long, b: Byte) =>
                if (b != ((idx % 251) & 0xff).toByte) mismatches.incrementAndGet()
                idx + 1L
              } match {
                case Right(n) => n
                case Left(_)  => -1L
              }
              val afterBig = pool.stats
              proof(
                "pool-stream reuse=" + bodies.distinct + " idleAfterReuse=" + afterReuse +
                  " bigTotal=" + total + " mismatches=" + mismatches.get() +
                  " lazy=" + lazyProof + " idleAfterBig=" + afterBig,
              )
              (bodies, afterReuse, total, mismatches.get(), lazyProof, afterBig)
            } finally pool.close()
          }.map { case (bodies, afterReuse, total, mismatches, lazyProof, afterBig) =>
            assertTrue(
              bodies.forall(_ == "pool-ok"),
              afterReuse.checkedOut == 0,
              afterReuse.idle == 1,
              total == BigBodyBytes.toLong,
              mismatches == 0,
              lazyProof,
              afterBig.checkedOut == 0,
            )
          }
        }
      },
    ) @@ sequential

  private def withLoom[R](
    routes: Routes[Any],
    http2Config: Http2Config = Http2Config(),
  )(use: Int => ZIO[R, Throwable, TestResult]): ZIO[R & Scope, Throwable, TestResult] = {
    val connector =
      Connector(bind = BindAddress.localhost(0), protocol = Protocol.H2C(http2Config))
    ZIO
      .acquireRelease(
        ZIO.attempt(LoomServer(connector).serve(routes, Context.empty)),
      )(handle => ZIO.succeed(handle.shutdownAndWait()))
      .flatMap { handle =>
        val port = handle.bindings.head.address match {
          case BoundAddress.Tcp(_, value) => value
          case other                      => throw new AssertionError("Expected TCP binding: " + other)
        }
        use(port)
      }
  }
}
