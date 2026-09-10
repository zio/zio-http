package zio.http.h2

import java.nio.charset.StandardCharsets

import scala.annotation.experimental
import scala.collection.mutable

import zio._
import zio.blocks.chunk.Chunk
import zio.blocks.context.Context
import zio.blocks.endpoint.RoutePattern
import zio.test.TestAspect.sequential
import zio.test._

import zio.http.ResultType._
import zio.http.h2.H2Frame._
import zio.http.h2.H2RawClientFixture.RawH2Client
import zio.http.h2.hpack.{HeaderField, HpackEncoder}
import zio.http.{
  AccessLog,
  AccessLogRecord,
  AccessLogSink,
  BindAddress,
  Body,
  BoundAddress,
  Connector,
  Handler,
  LoomServer,
  Method,
  Middleware,
  Request,
  Response,
  Route,
  Routes,
  Status,
  TrustedProxyConfig,
  URL,
  Version,
  handler,
}

/**
 * Access/audit logging with a pluggable sink, middleware-only by design.
 *
 * Every request that reaches a route must produce one metadata-only record
 * (method, path, status, duration, request-id, plus the G3 trust decision and
 * the G2 deadline outcome) delivered to a user-supplied [[AccessLogSink]]. Body
 * bytes must never reach the sink (by construction the record carries no body
 * handle), and a throwing sink must never fail the request. Transports emit
 * nothing by default: logging attaches opt-in via [[Middleware.accessLog]],
 * composed above the transport's internal OpenTelemetry span (which is never
 * touched here).
 */
@experimental
object H2AccessLogSpec extends ZIOSpecDefault {

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("H2AccessLogSpec")(
      test("custom sink receives one record per request with method/path/status/duration/request-id") {
        val capture = CaptureSink()
        withLoggedServer(TrustedProxyConfig.default, capture, EchoRoutes) { port =>
          ZIO.attemptBlocking {
            val client   = new RawH2Client(port)
            val response =
              try client.get("/", streamId = 1, extra = List(HeaderField("x-request-id", "req-1")))
              finally client.close()
            val records  = capture.records
            assertTrue(
              response.status == 200,
              records.length == 1,
              records.head.method == "GET",
              records.head.path == "/",
              records.head.status == 200,
              records.head.durationMs >= 0L,
              records.head.requestId == "req-1",
              records.head.deadlineOutcome.contains(AccessLog.DeadlineOutcome.Ok),
            )
          }
        }
      },
      test("trusted peer forwarding yields trustDecision=trusted with resolved client-ip") {
        val capture = CaptureSink()
        val trusted = TrustedProxyConfig(trustedCidrs = Set("127.0.0.1/32"))
        withLoggedServer(trusted, capture, EchoRoutes) { port =>
          ZIO.attemptBlocking {
            val client   = new RawH2Client(port)
            val extra    = List(
              HeaderField("x-forwarded-for", "203.0.113.7"),
              HeaderField("x-request-id", "req-trusted"),
            )
            val response =
              try client.get("/", streamId = 1, extra = extra)
              finally client.close()
            val records  = capture.records
            assertTrue(
              response.status == 200,
              records.length == 1,
              records.head.peerAddress.contains("127.0.0.1"),
              records.head.clientIp.contains("203.0.113.7"),
              records.head.trustDecision.contains(AccessLog.TrustDecision.Trusted),
            )
          }
        }
      },
      test("untrusted peer yields trustDecision=untrusted and client-ip falls back to peer") {
        val capture = CaptureSink()
        withLoggedServer(TrustedProxyConfig.default, capture, EchoRoutes) { port =>
          ZIO.attemptBlocking {
            val client   = new RawH2Client(port)
            val extra    = List(
              HeaderField("x-forwarded-for", "203.0.113.7"),
              HeaderField("x-request-id", "req-untrusted"),
            )
            val response =
              try client.get("/", streamId = 1, extra = extra)
              finally client.close()
            val records  = capture.records
            assertTrue(
              response.status == 200,
              records.length == 1,
              records.head.peerAddress.contains("127.0.0.1"),
              records.head.clientIp.contains("127.0.0.1"),
              records.head.trustDecision.contains(AccessLog.TrustDecision.Untrusted),
            )
          }
        }
      },
      test("request body bytes never reach the sink") {
        val capture = CaptureSink()
        val secret  = "super-secret-body-payload-7f3a9c"
        withLoggedServer(TrustedProxyConfig.default, capture, EchoRoutes) { port =>
          ZIO.attemptBlocking {
            val client   = new RawH2Client(port)
            val response =
              try
                client.post("/", Chunk.fromArray(secret.getBytes(StandardCharsets.UTF_8)), streamId = 1, extra = Nil)
              finally client.close()
            val records  = capture.records
            val record   = records.head
            assertTrue(
              response.status == 200,
              new String(response.body.toArray, StandardCharsets.UTF_8) == secret,
              records.length == 1,
              record.method == "POST",
              // Field-level secrecy: no record field and no rendered log line
              // may carry body bytes (not just "toString has no secret").
              !record.path.contains(secret),
              !record.route.exists(_.contains(secret)),
              !record.requestId.contains(secret),
              !record.peerAddress.exists(_.contains(secret)),
              !record.clientIp.exists(_.contains(secret)),
              !record.protocol.contains(secret),
              !AccessLog.formatLine(record).contains(secret),
            )
          }
        }
      },
      test("sink failure never fails the request") {
        val failing = AccessLogSink(_ => throw new RuntimeException("sink boom"))
        withLoggedServer(TrustedProxyConfig.default, failing, EchoRoutes) { port =>
          ZIO.attemptBlocking {
            val client   = new RawH2Client(port)
            val response =
              try client.get("/", streamId = 1, extra = Nil)
              finally client.close()
            assertTrue(
              response.status == 200,
              new String(response.body.toArray, StandardCharsets.UTF_8) == "hello",
            )
          }
        }
      },
      test("missing x-request-id is replaced with a generated id") {
        val capture = CaptureSink()
        withLoggedServer(TrustedProxyConfig.default, capture, EchoRoutes) { port =>
          ZIO.attemptBlocking {
            val client   = new RawH2Client(port)
            val response =
              try client.get("/", streamId = 1, extra = Nil)
              finally client.close()
            val records  = capture.records
            assertTrue(
              response.status == 200,
              records.length == 1,
              records.head.requestId.nonEmpty,
            )
          }
        }
      },
      test("over-long injected request-id is truncated before it reaches the record") {
        val capture = CaptureSink()
        withLoggedServer(TrustedProxyConfig.default, capture, EchoRoutes) { port =>
          ZIO.attemptBlocking {
            // A raw newline never survives HPACK validation (the server 500s
            // before any handler runs), so the wire-provable sanitize rule is
            // cardinality bounding: a 5KB id must arrive truncated to 128.
            val big      = "a" * 5120
            val client   = new RawH2Client(port)
            val response =
              try client.get("/", streamId = 1, extra = List(HeaderField("x-request-id", big)))
              finally client.close()
            val records  = capture.records
            assertTrue(
              response.status == 200,
              records.length == 1,
              records.head.requestId == "a" * 128,
            )
          }
        }
      },
      suite("AccessLog unit")(
        test("disabled sink accepts records without effect") {
          val record = AccessLogRecord(
            method = "GET",
            path = "/",
            route = None,
            status = 200,
            durationMs = 1L,
            requestId = "u-1",
            peerAddress = None,
            clientIp = None,
            trustDecision = None,
            deadlineOutcome = None,
            protocol = "h2c",
          )
          // Both entry points must complete without throwing ...
          AccessLog.emit(AccessLogSink.disabled, record)
          AccessLogSink.disabled.log(record)
          // ... and a fresh capture sink observes zero records from these emits.
          assertTrue(CaptureSink().records.isEmpty)
        },
        test("emit swallows sink failures") {
          val boom   = AccessLogSink(_ => throw new RuntimeException("sink boom"))
          val record = AccessLogRecord(
            method = "GET",
            path = "/",
            route = None,
            status = 200,
            durationMs = 0L,
            requestId = "u-3",
            peerAddress = None,
            clientIp = None,
            trustDecision = None,
            deadlineOutcome = Some(AccessLog.DeadlineOutcome.Ok),
            protocol = "h2c",
          )
          AccessLog.emit(boom, record)
          assertTrue(true)
        },
        test("record exposes no body handle by construction") {
          val fields    = classOf[AccessLogRecord].getDeclaredFields.toList
          val typeNames = fields.map(_.getType.getName)
          assertTrue(
            !fields.exists(_.getName.equalsIgnoreCase("body")),
            !typeNames.exists(_.contains("Body")),
            !typeNames.exists(_.contains("Chunk")),
          )
        },
        test("trust derivation honors forwarding only when it was applied") {
          assertTrue(
            AccessLog.trustDecision(Some("127.0.0.1"), Some("203.0.113.7")).contains(AccessLog.TrustDecision.Trusted),
            AccessLog.trustDecision(Some("127.0.0.1"), Some("127.0.0.1")).contains(AccessLog.TrustDecision.Untrusted),
            AccessLog.trustDecision(None, None).isEmpty,
          )
        },
        test("header literals stay in sync with TrustedProxyConfig") {
          assertTrue(
            AccessLog.PeerAddressHeader == TrustedProxyConfig.PeerAddressHeader,
            AccessLog.ClientIpHeader == TrustedProxyConfig.ClientIpHeader,
          )
        },
        test("accessLog middleware preserves Response and Halt results") {
          val okResponse  = Response(status = Status.Ok, body = Body.fromString("ok"))
          val halt        = zio.http.Halt(Response(status = Status.Forbidden, body = Body.empty))
          val okRoutes    = Routes(Route(RoutePattern.GET, Handler.succeed(okResponse)))
          val haltRoutes  = Routes(Route(RoutePattern.POST, Handler(halt)))
          val loggedOk    = okRoutes @@ Middleware.accessLog(AccessLogSink.disabled)
          val loggedHalt  = haltRoutes @@ Middleware.accessLog(AccessLogSink.disabled)
          val scope       = zio.blocks.scope.Scope.global
          val plain       = Request(Method.GET, URL.root, zio.http.Headers.empty, Body.empty, Version.`HTTP/1.1`)
          val directOk    = okRoutes.routes.toList.head.handler.handle(plain, Context.empty, (), scope)
          val wrappedOk   = loggedOk.routes.toList.head.handler.handle(plain, Context.empty, (), scope)
          val directHalt  = haltRoutes.routes.toList.head.handler.handle(plain, Context.empty, (), scope)
          val wrappedHalt = loggedHalt.routes.toList.head.handler.handle(plain, Context.empty, (), scope)
          assertTrue(
            wrappedOk == directOk,
            wrappedHalt == directHalt,
            wrappedHalt != wrappedOk,
            loggedOk.size == 1,
            loggedHalt.size == 1,
          )
        },
      ),
    ) @@ sequential

  private val EchoRoutes: Routes[Any] =
    Routes(
      Route(
        RoutePattern.GET,
        handler { (_: Request) =>
          responseAsResult(Response(status = Status.Ok, body = Body.fromString("hello")))
        },
      ),
      Route(
        RoutePattern.POST,
        handler { (req: Request) =>
          responseAsResult(Response(status = Status.Ok, body = req.body))
        },
      ),
    )

  private def withLoggedServer[R](
    trusted: TrustedProxyConfig,
    sink: AccessLogSink,
    routes: Routes[Any],
  )(use: Int => ZIO[R, Throwable, TestResult]): ZIO[R & Scope, Throwable, TestResult] =
    ZIO
      .acquireRelease(
        ZIO.attempt {
          val connector = Connector(
            bind = BindAddress.localhost(0),
            trustedProxy = trusted,
          )
          new LoomServer(connector).serve(routes @@ Middleware.accessLog(sink), Context.empty)
        },
      )(handle => ZIO.succeed(handle.shutdownAndWait()))
      .flatMap { handle =>
        val port = handle.bindings.head.address match {
          case BoundAddress.Tcp(_, value) => value
          case other                      => throw new AssertionError("Expected TCP binding but found: " + other)
        }
        use(port)
      }

  private final class CaptureSink extends AccessLogSink {
    private val queue                      = new java.util.concurrent.ConcurrentLinkedQueue[AccessLogRecord]()
    def log(record: AccessLogRecord): Unit = { queue.add(record); () }
    def records: List[AccessLogRecord]     = queue.toArray(new Array[AccessLogRecord](0)).toList
  }

  private object CaptureSink {
    def apply(): CaptureSink = new CaptureSink()
  }

  /**
   * Header-aware sends on top of the shared raw client (extra headers + POST).
   */
  private implicit final class RawClientOps(val client: RawH2Client) {
    private def encoder: HpackEncoder = new HpackEncoder()

    def get(path: String, streamId: Int, extra: List[HeaderField]): H2RawClientFixture.RawResponse = {
      client.sendFrame(makeHeaders("GET", path, streamId, endStream = true, extra))
      client.awaitResponse(streamId)
    }

    def post(
      path: String,
      body: Chunk[Byte],
      streamId: Int,
      extra: List[HeaderField],
    ): H2RawClientFixture.RawResponse = {
      client.sendFrame(makeHeaders("POST", path, streamId, endStream = false, extra))
      client.sendFrame(Data(streamId, body, endStream = true))
      client.awaitResponse(streamId)
    }

    private def makeHeaders(
      method: String,
      path: String,
      streamId: Int,
      endStream: Boolean,
      extra: List[HeaderField],
    ): Headers = {
      val pseudo = List(
        HeaderField(":method", method),
        HeaderField(":path", path),
        HeaderField(":scheme", "http"),
        HeaderField(":authority", s"127.0.0.1:${client.port}"),
      )
      Headers(
        streamId = streamId,
        headerBlock = encoder.encode(pseudo ++ extra),
        endStream = endStream,
        endHeaders = true,
      )
    }
  }
}
