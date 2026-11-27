package zio.http.h1

import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.Socket

import zio._
import zio.blocks.context.Context
import zio.blocks.endpoint.RoutePattern
import zio.test.TestAspect.sequential
import zio.test._

import zio.http.ResultType._
import zio.http.h1.H1RawClientFixture._
import zio.http.{
  BindAddress,
  Body,
  BoundAddress,
  Connector,
  LoomServer,
  Protocol,
  Request,
  Response,
  Route,
  Routes,
  Status,
  handler,
}

/**
 * Loom HTTP/1.1 engine rejection, limits, stall and lifecycle behavior.
 *
 * Every malformed-framing case must fail as `400` (or the RFC-correct
 * `501`/`413`) with the connection closed — never a hang, never a 500, never a
 * poisoned survivor. Stall, idle, disconnect and shutdown behavior is proved
 * with real sockets and bounded waits; no sleeps poll for state.
 */
object H1TransportLimitsSpec extends ZIOSpecDefault {

  private val routes: Routes[Any] = Routes(
    Route(
      RoutePattern.GET,
      handler { (_: Request) =>
        responseAsResult(Response(status = Status.Ok, body = Body.fromString("ok")))
      },
    ),
  )

  private def smallBodyConnector: Connector =
    Connector(bind = BindAddress.localhost(0), protocol = Protocol.H1C(), maxRequestBodySize = 8L)

  private def fastDeadlineConnector: Connector =
    Connector(bind = BindAddress.localhost(0), protocol = Protocol.H1C(), requestTimeoutMs = 400L)

  private def idleTimeoutConnector: Connector =
    Connector(
      bind = BindAddress.localhost(0),
      protocol = Protocol.H1C(),
      idleTimeout = java.time.Duration.ofMillis(300L),
    )

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("H1TransportLimitsSpec")(
      test("rejects a malformed request line with 400 and closes") {
        withH1Server(routes) { port =>
          ZIO.attemptBlocking {
            val client = new RawH1Client(port)
            try {
              client.sendRaw("BOGUS\r\n\r\n")
              val resp = client.readResponse()
              val eof  = client.readByte()
              assertTrue(resp.status == 400, eof == -1)
            } finally client.close()
          }
        }
      },
      test("rejects LF-only line endings with 400 and closes") {
        withH1Server(routes) { port =>
          ZIO.attemptBlocking {
            val client = new RawH1Client(port)
            try {
              client.sendRaw("GET / HTTP/1.1\nHost: 127.0.0.1\n\n")
              val resp = client.readResponse()
              val eof  = client.readByte()
              assertTrue(resp.status == 400, eof == -1)
            } finally client.close()
          }
        }
      },
      test("rejects HTTP/1.0 with 400 and closes") {
        withH1Server(routes) { port =>
          ZIO.attemptBlocking {
            val client = new RawH1Client(port)
            try {
              client.sendRaw("GET / HTTP/1.0\r\nHost: 127.0.0.1\r\n\r\n")
              val resp = client.readResponse()
              val eof  = client.readByte()
              assertTrue(resp.status == 400, eof == -1)
            } finally client.close()
          }
        }
      },
      test("rejects Content-Length with Transfer-Encoding with 400 and closes") {
        withH1Server(routes) { port =>
          ZIO.attemptBlocking {
            val client = new RawH1Client(port)
            try {
              client.sendRaw(
                "POST / HTTP/1.1\r\nHost: 127.0.0.1\r\nContent-Length: 2\r\nTransfer-Encoding: chunked\r\n\r\n",
              )
              val resp = client.readResponse()
              val eof  = client.readByte()
              assertTrue(resp.status == 400, eof == -1)
            } finally client.close()
          }
        }
      },
      test("rejects unknown transfer codings with 400 and closes") {
        withH1Server(routes) { port =>
          ZIO.attemptBlocking {
            val client = new RawH1Client(port)
            try {
              client.sendRaw("POST / HTTP/1.1\r\nHost: 127.0.0.1\r\nTransfer-Encoding: gzip\r\n\r\n")
              val resp = client.readResponse()
              val eof  = client.readByte()
              assertTrue(resp.status == 400, eof == -1)
            } finally client.close()
          }
        }
      },
      test("rejects CONNECT with 501 and closes") {
        withH1Server(routes) { port =>
          ZIO.attemptBlocking {
            val client = new RawH1Client(port)
            try {
              client.sendRaw("CONNECT example.com:443 HTTP/1.1\r\nHost: example.com:443\r\n\r\n")
              val resp = client.readResponse()
              val eof  = client.readByte()
              assertTrue(resp.status == 501, eof == -1)
            } finally client.close()
          }
        }
      },
      test("rejects unknown methods with 501 and closes") {
        withH1Server(routes) { port =>
          ZIO.attemptBlocking {
            val client = new RawH1Client(port)
            try {
              client.sendRaw("FROB / HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n")
              val resp = client.readResponse()
              val eof  = client.readByte()
              assertTrue(resp.status == 501, eof == -1)
            } finally client.close()
          }
        }
      },
      test("rejects bodies over maxRequestBodySize with 413 and closes") {
        withH1Server(routes, smallBodyConnector) { port =>
          ZIO.attemptBlocking {
            val client = new RawH1Client(port)
            try {
              // The client sends everything it declared; the engine rejects
              // on the decoded size, before dispatch.
              client.sendRaw("POST / HTTP/1.1\r\nHost: 127.0.0.1\r\nContent-Length: 100\r\n\r\n" + ("0123456789" * 10))
              val resp = client.readResponse()
              val eof  = client.readByte()
              assertTrue(resp.status == 413, eof == -1)
            } finally client.close()
          }
        }
      },
      test("rejects bodies over the codec retention bound with 413 and closes") {
        withH1Server(routes) { port =>
          ZIO.attemptBlocking {
            val client = new RawH1Client(port)
            try {
              // The strict codec rejects the declared size before any body
              // byte arrives, so this stays cheap on the wire.
              client.sendRaw("POST / HTTP/1.1\r\nHost: 127.0.0.1\r\nContent-Length: 2000000\r\n\r\n")
              val resp = client.readResponse()
              val eof  = client.readByte()
              assertTrue(resp.status == 413, eof == -1)
            } finally client.close()
          }
        }
      },
      test("rejects more than 100 headers with 400 and closes") {
        withH1Server(routes) { port =>
          ZIO.attemptBlocking {
            val client = new RawH1Client(port)
            try {
              val head = new StringBuilder("GET / HTTP/1.1\r\nHost: 127.0.0.1\r\n")
              var i    = 0
              while (i < 101) {
                head.append("x-flood-").append(i.toString).append(": v\r\n")
                i += 1
              }
              head.append("\r\n")
              client.sendRaw(head.toString)
              val resp = client.readResponse()
              val eof  = client.readByte()
              assertTrue(resp.status == 400, eof == -1)
            } finally client.close()
          }
        }
      },
      test("rejects trailing garbage on a keep-alive connection with 400 and closes") {
        withH1Server(routes) { port =>
          ZIO.attemptBlocking {
            val client = new RawH1Client(port)
            try {
              client.sendRaw("GET / HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n")
              val first  = client.readResponse()
              client.sendRaw("GARBAGE!!!\r\n\r\n")
              val second = client.readResponse()
              val eof    = client.readByte()
              assertTrue(
                first.status == 200 && first.bodyText == "ok",
                second.status == 400,
                eof == -1,
              )
            } finally client.close()
          }
        }
      },
      test("closes stalled connections after requestTimeoutMs") {
        withH1Server(routes, fastDeadlineConnector) { port =>
          ZIO.attemptBlocking {
            val client = new RawH1Client(port)
            try {
              client.sendRaw("GET / HTTP/1.1\r\n")
              val outcome =
                try {
                  val resp = client.readResponse()
                  "status:" + resp.status.toString
                } catch {
                  case _: java.io.EOFException => "closed"
                }
              // The deadline aborts the turn by closing the streams: the
              // stalled peer observes a clean close, never a hang.
              assertTrue(outcome == "closed")
            } finally client.close()
          }
        }
      },
      test("closes idle keep-alive connections after idleTimeout") {
        withH1Server(routes, idleTimeoutConnector) { port =>
          ZIO.attemptBlocking {
            val client = new RawH1Client(port)
            try {
              client.sendRaw("GET / HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n")
              val resp = client.readResponse()
              // No further bytes are sent: the idle guard closes the socket.
              val eof  = client.readByte()
              assertTrue(resp.status == 200 && resp.bodyText == "ok", eof == -1)
            } finally client.close()
          }
        }
      },
      test("survives client disconnects and keeps serving") {
        withH1Server(routes) { port =>
          ZIO.attemptBlocking {
            val dirty  = new Socket("127.0.0.1", port)
            dirty.setSoTimeout(10000)
            try {
              val out = dirty.getOutputStream
              out.write("POST / HTTP/1.1\r\nHost: 127.0.0.1\r\nContent-Length: 100\r\n\r\npartial".getBytes("UTF-8"))
              out.flush()
            } finally dirty.close()
            // No settling sleep: the clean request must succeed regardless of
            // where the dirty connection is in teardown.
            val client = new RawH1Client(port)
            try {
              client.sendRaw("GET / HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n")
              val resp = client.readResponse()
              assertTrue(resp.status == 200, resp.bodyText == "ok")
            } finally client.close()
          }
        }
      },
      test("shutdownAndWait closes keep-alive sockets and refuses new connections") {
        ZIO.attemptBlocking {
          val connector = Connector(bind = BindAddress.localhost(0), protocol = Protocol.H1C())
          val handle    = LoomServer(connector).serve(routes, Context.empty)
          try {
            val port = handle.bindings.head.address match {
              case BoundAddress.Tcp(_, value) => value
              case other                      => throw new AssertionError("Expected TCP binding but found: " + other)
            }
            val kept = new RawH1Client(port)
            try {
              kept.sendRaw("GET / HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n")
              val first   = kept.readResponse()
              handle.shutdownAndWait()
              kept.socket.setSoTimeout(5000)
              val eof     = kept.readByte()
              val refused =
                try {
                  val fresh = new Socket()
                  try {
                    fresh.connect(new InetSocketAddress("127.0.0.1", port), 1000)
                    false
                  } finally fresh.close()
                } catch {
                  case _: ConnectException => true
                }
              assertTrue(first.status == 200, first.bodyText == "ok", eof == -1, refused)
            } finally kept.close()
          } finally handle.shutdownAndWait()
        }
      },
    ) @@ sequential
}
