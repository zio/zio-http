package zio.http.h1

import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

import zio._
import zio.blocks.chunk.Chunk
import zio.blocks.endpoint.RoutePattern
import zio.blocks.streams.Stream
import zio.blocks.streams.io.Reader
import zio.test.TestAspect.sequential
import zio.test._

import zio.http.ResultType._
import zio.http.h1.H1RawClientFixture._
import zio.http.{
  BindAddress,
  Body,
  Connector,
  Handler,
  Headers,
  Method,
  Protocol,
  Request,
  Response,
  Route,
  Routes,
  Status,
  TrustedProxyConfig,
  handler,
}

/**
 * Loom HTTP/1.1 engine behavior over raw sockets.
 *
 * Exercises the real engine end to end: sequential keep-alive on one
 * connection, pipelined wire order, streamed (chunked) request and response
 * bodies with per-chunk delivery, HEAD and the other bodyless statuses
 * (1xx/204/304), route dispatch (200/404/500), malformed handler responses,
 * `Connection: close`, proxy-trust and telemetry parity, and an independent JDK
 * HTTP client. Malformed framing, limits, stalls and lifecycle live in
 * [[H1TransportLimitsSpec]].
 */
object H1TransportSpec extends ZIOSpecDefault {

  private val ProxyRoutes: Routes[Any] = Routes(
    Route(
      RoutePattern(Method.GET, "/whoami"),
      handler { (req: Request) =>
        val clientIp     = req.headers.rawGet("x-client-ip").getOrElse("none")
        val peer         = req.headers.rawGet("x-peer-address").getOrElse("none")
        val forwardedFor = req.headers.rawGet("x-forwarded-for").getOrElse("none")
        val host         = req.url.host.getOrElse("none")
        val scheme       = req.url.scheme.map(_.text).getOrElse("none")
        responseAsResult(
          Response(status = Status.Ok, body = Body.fromString(s"$clientIp|$peer|$forwardedFor|$host|$scheme")),
        )
      },
    ),
  )

  private val routes: Routes[Any] = Routes(
    Route(
      RoutePattern.GET,
      handler { (_: Request) =>
        responseAsResult(Response(status = Status.Ok, body = Body.fromString("ok")))
      },
    ),
    Route(
      RoutePattern(Method.POST, "/echo"),
      handler { (req: Request) =>
        responseAsResult(Response(status = Status.Ok, body = Body.fromString(req.body.asString())))
      },
    ),
    Route(
      RoutePattern(Method.GET, "/stream"),
      handler { (_: Request) =>
        responseAsResult(
          Response(
            Status.Ok,
            Headers("content-type" -> "text/plain"),
            Body.fromStream(Stream.fromIterator("abc".getBytes(StandardCharsets.UTF_8).iterator)),
          ),
        )
      },
    ),
    Route(
      RoutePattern(Method.GET, "/boom"),
      Handler.fromRequest((_: Request) => throw new RuntimeException("boom")),
    ),
    Route(
      RoutePattern(Method.GET, "/hello"),
      handler { (req: Request) =>
        responseAsResult(
          Response(status = Status.Ok, body = Body.fromString(req.url.path.encode + "|" + req.url.queryParams.encode)),
        )
      },
    ),
    Route(
      RoutePattern(Method.GET, "/keepalive"),
      handler { (_: Request) =>
        responseAsResult(
          Response(status = Status.Ok, body = Body.fromString("ka")).setHeader("connection", "keep-alive"),
        )
      },
    ),
    Route(
      RoutePattern(Method.GET, "/no-content"),
      handler { (_: Request) =>
        responseAsResult(Response(status = Status.NoContent, body = Body.fromString("ignored")))
      },
    ),
    Route(
      RoutePattern(Method.GET, "/not-modified"),
      handler { (_: Request) =>
        responseAsResult(Response(status = Status.NotModified, body = Body.fromString("ignored")))
      },
    ),
    Route(
      RoutePattern(Method.GET, "/continue"),
      handler { (_: Request) =>
        responseAsResult(Response(status = Status.Continue, body = Body.fromString("ignored")))
      },
    ),
    Route(
      RoutePattern(Method.GET, "/bad-status"),
      handler { (_: Request) =>
        // Status 99 is out of the encoder's 100..599 range: the handler produced
        // an unserializable response, so the engine must answer 500 and close.
        responseAsResult(Response(status = Status(99), body = Body.fromString("bad")))
      },
    ),
  )

  private def trustedProxyConnector: Connector =
    Connector(
      bind = BindAddress.localhost(0),
      protocol = Protocol.H1C(),
      trustedProxy = TrustedProxyConfig(trustedCidrs = Set("127.0.0.1/32")),
    )

  private def telemetryConnector: Connector =
    Connector(bind = BindAddress.localhost(0), protocol = Protocol.H1C(), requestTelemetry = true)

  private def gatedStreamRoutes(first: Array[Byte], second: Array[Byte], releaseSecond: CountDownLatch): Routes[Any] =
    Routes(
      Route(
        RoutePattern(Method.GET, "/gated"),
        handler { (_: Request) =>
          responseAsResult(
            Response(
              Status.Ok,
              Headers("content-type" -> "text/plain"),
              Body.fromStream(
                Stream.fromReader[Nothing, Byte](new GatedChunkReader(first, second, releaseSecond)),
              ),
            ),
          )
        },
      ),
    )

  /**
   * A two-chunk pull source. `readN` returns the first chunk immediately and
   * blocks before the second until the test has observed the first chunk on the
   * wire; `chunked(16384)` therefore emits (and the transport flushes) exactly
   * one chunk per pull.
   */
  private final class GatedChunkReader(first: Array[Byte], second: Array[Byte], releaseSecond: CountDownLatch)
      extends Reader[Byte] {
    private var stage  = 0
    private var offset = 0
    private var closed = false

    override def close(): Unit     = closed = true
    override def isClosed: Boolean = closed || (stage == 2 && offset >= second.length)

    override def read[A >: Byte](sentinel: A): A = {
      if (closed) sentinel
      else if (stage == 0) {
        if (offset < first.length) {
          val byte = first(offset)
          offset += 1
          byte.asInstanceOf[A]
        } else {
          stage = 1
          offset = 0
          read(sentinel)
        }
      } else if (stage == 1) {
        awaitRelease()
        stage = 2
        offset = 0
        read(sentinel)
      } else if (offset < second.length) {
        val byte = second(offset)
        offset += 1
        byte.asInstanceOf[A]
      } else {
        closed = true
        sentinel
      }
    }

    override def readN[A >: Byte](n: Int): Chunk[A] = {
      if (closed || n <= 0) Chunk.empty
      else
        stage match {
          case 0 if offset < first.length =>
            val out = Chunk.fromArray(java.util.Arrays.copyOfRange(first, offset, first.length))
            offset = first.length
            out.asInstanceOf[Chunk[A]]
          case 0 | 1                      =>
            stage = 1
            awaitRelease()
            stage = 2
            val out = Chunk.fromArray(second)
            offset = second.length
            out.asInstanceOf[Chunk[A]]
          case _                          =>
            closed = true
            Chunk.empty
        }
    }

    private def awaitRelease(): Unit =
      if (!releaseSecond.await(10L, TimeUnit.SECONDS))
        throw new AssertionError("gated second chunk was not released in time")
  }

  private def readLine(in: InputStream): String = {
    val buf   = new ByteArrayOutputStream()
    var byte  = in.read()
    if (byte < 0) throw new EOFException("EOF inside H1 response line")
    while (byte >= 0 && byte != '\n') {
      buf.write(byte)
      byte = in.read()
    }
    val bytes = buf.toByteArray
    val len   = if (bytes.nonEmpty && bytes(bytes.length - 1) == '\r') bytes.length - 1 else bytes.length
    new String(bytes, 0, len, StandardCharsets.US_ASCII)
  }

  private def readExact(in: InputStream, length: Int): Array[Byte] = {
    val out    = new Array[Byte](length)
    var offset = 0
    while (offset < length) {
      val n = in.read(out, offset, length - offset)
      if (n < 0) throw new EOFException("EOF after " + offset + " of " + length + " bytes")
      offset += n
    }
    out
  }

  private def readHeaders(in: InputStream): Map[String, String] = {
    var headers = Map.empty[String, String]
    var line    = readLine(in)
    while (line.nonEmpty) {
      val colon = line.indexOf(':')
      if (colon < 0) throw new AssertionError("Bad H1 header line: " + line)
      headers = headers.updated(
        line.substring(0, colon).trim.toLowerCase(java.util.Locale.ROOT),
        line.substring(colon + 1).trim,
      )
      line = readLine(in)
    }
    headers
  }

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("H1TransportSpec")(
      test("serves GET with Content-Length over a raw socket") {
        withH1Server(routes) { port =>
          ZIO.attemptBlocking {
            val client = new RawH1Client(port)
            try {
              client.sendRaw("GET / HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n")
              val resp = client.readResponse()
              assertTrue(resp.status == 200, resp.bodyText == "ok", resp.header("content-length").contains("2"))
            } finally client.close()
          }
        }
      },
      test("serves sequential keep-alive requests on one connection") {
        withH1Server(routes) { port =>
          ZIO.attemptBlocking {
            val client = new RawH1Client(port)
            try {
              client.sendRaw("GET / HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n")
              val first  = client.readResponse()
              client.sendRaw("GET /hello?name=avery HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n")
              val second = client.readResponse()
              assertTrue(
                first.status == 200 && first.bodyText == "ok",
                second.status == 200 && second.bodyText == "/hello|name=avery",
                second.header("connection").isEmpty,
              )
            } finally client.close()
          }
        }
      },
      test("serves pipelined requests in wire order") {
        withH1Server(routes) { port =>
          ZIO.attemptBlocking {
            val client = new RawH1Client(port)
            try {
              client.sendRaw(
                "GET / HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n" +
                  "GET /hello?name=pipe HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n",
              )
              val first  = client.readResponse()
              val second = client.readResponse()
              assertTrue(
                first.status == 200 && first.bodyText == "ok",
                second.status == 200 && second.bodyText == "/hello|name=pipe",
              )
            } finally client.close()
          }
        }
      },
      test("answers HEAD with headers only and no body bytes") {
        withH1Server(routes) { port =>
          ZIO.attemptBlocking {
            val client = new RawH1Client(port)
            try {
              client.sendRaw("HEAD / HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n")
              val resp = client.readResponse(readBody = false)
              assertTrue(
                resp.status == 200,
                resp.body.isEmpty,
                resp.header("content-length").contains("2"),
              )
            } finally client.close()
          }
        }
      },
      test("serves 204 and 304 responses without body bytes and keeps the connection usable") {
        withH1Server(routes) { port =>
          ZIO.attemptBlocking {
            val client = new RawH1Client(port)
            try {
              client.sendRaw("GET /no-content HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n")
              val noContent   = client.readResponse(readBody = false)
              client.sendRaw("GET /not-modified HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n")
              val notModified = client.readResponse(readBody = false)
              client.sendRaw("GET / HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n")
              val followUp    = client.readResponse()
              assertTrue(
                noContent.status == 204,
                noContent.body.isEmpty,
                noContent.header("content-length").isEmpty,
                noContent.header("transfer-encoding").isEmpty,
                notModified.status == 304,
                notModified.body.isEmpty,
                notModified.header("content-length").isEmpty,
                notModified.header("transfer-encoding").isEmpty,
                followUp.status == 200 && followUp.bodyText == "ok",
              )
            } finally client.close()
          }
        }
      },
      test("serves a 1xx response without body bytes and keeps the connection usable") {
        withH1Server(routes) { port =>
          ZIO.attemptBlocking {
            val client = new RawH1Client(port)
            try {
              client.sendRaw("GET /continue HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n")
              val interim  = client.readResponse(readBody = false)
              client.sendRaw("GET / HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n")
              val followUp = client.readResponse()
              assertTrue(
                interim.status == 100,
                interim.body.isEmpty,
                interim.header("content-length").isEmpty,
                interim.header("transfer-encoding").isEmpty,
                followUp.status == 200 && followUp.bodyText == "ok",
              )
            } finally client.close()
          }
        }
      },
      test("returns 404 for unknown routes") {
        withH1Server(routes) { port =>
          ZIO.attemptBlocking {
            val client = new RawH1Client(port)
            try {
              client.sendRaw("GET /missing HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n")
              val resp = client.readResponse()
              assertTrue(resp.status == 404)
            } finally client.close()
          }
        }
      },
      test("maps handler defects to 500") {
        withH1Server(routes) { port =>
          ZIO.attemptBlocking {
            val client = new RawH1Client(port)
            try {
              client.sendRaw("GET /boom HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n")
              val resp = client.readResponse()
              assertTrue(resp.status == 500)
            } finally client.close()
          }
        }
      },
      test("maps a malformed handler response to 500 and closes") {
        withH1Server(routes) { port =>
          ZIO.attemptBlocking {
            val client = new RawH1Client(port)
            try {
              client.sendRaw("GET /bad-status HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n")
              val resp = client.readResponse()
              val eof  = client.readByte()
              assertTrue(resp.status == 500, eof == -1)
            } finally client.close()
          }
        }
      },
      test("echoes chunked request bodies") {
        withH1Server(routes) { port =>
          ZIO.attemptBlocking {
            val client = new RawH1Client(port)
            try {
              client.sendRaw(
                "POST /echo HTTP/1.1\r\nHost: 127.0.0.1\r\nTransfer-Encoding: chunked\r\n\r\n" +
                  "5\r\nhello\r\n6\r\n world\r\n0\r\n\r\n",
              )
              val resp = client.readResponse()
              assertTrue(resp.status == 200, resp.bodyText == "hello world")
            } finally client.close()
          }
        }
      },
      test("streams unknown-length responses as chunked") {
        withH1Server(routes) { port =>
          ZIO.attemptBlocking {
            val client = new RawH1Client(port)
            try {
              client.sendRaw("GET /stream HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n")
              val resp = client.readResponse()
              assertTrue(
                resp.status == 200,
                resp.bodyText == "abc",
                resp.header("transfer-encoding").exists(_.toLowerCase(java.util.Locale.ROOT).contains("chunked")),
              )
            } finally client.close()
          }
        }
      },
      test("streams chunked responses with per-chunk delivery") {
        val releaseSecond = new CountDownLatch(1)
        withH1Server(
          gatedStreamRoutes(
            "abc".getBytes(StandardCharsets.UTF_8),
            "def".getBytes(StandardCharsets.UTF_8),
            releaseSecond,
          ),
        ) { port =>
          ZIO.attemptBlocking {
            val client = new RawH1Client(port)
            try {
              client.socket.setSoTimeout(5000)
              val in          = client.socket.getInputStream
              client.sendRaw("GET /gated HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n")
              val statusLine  = readLine(in)
              val headers     = readHeaders(in)
              // Reading the first chunk before releasing the gate proves the
              // transport flushed it instead of buffering the whole body.
              val firstSize   = Integer.parseInt(readLine(in), 16)
              val firstChunk  = readExact(in, firstSize)
              readExact(in, 2)
              releaseSecond.countDown()
              val secondSize  = Integer.parseInt(readLine(in), 16)
              val secondChunk = readExact(in, secondSize)
              readExact(in, 2)
              val terminal    = readLine(in)
              val trailerEnd  = readLine(in)
              assertTrue(
                statusLine == "HTTP/1.1 200 OK",
                headers.get("transfer-encoding").exists(_.toLowerCase(java.util.Locale.ROOT).contains("chunked")),
                new String(firstChunk, StandardCharsets.UTF_8) == "abc",
                new String(secondChunk, StandardCharsets.UTF_8) == "def",
                terminal == "0",
                trailerEnd.isEmpty,
              )
            } finally client.close()
          }
        }
      },
      test("serves an independent JDK HTTP client") {
        withH1Server(routes) { port =>
          ZIO.attemptBlocking {
            val client   = java.net.http.HttpClient.newHttpClient()
            val request  =
              java.net.http.HttpRequest
                .newBuilder(java.net.URI.create("http://127.0.0.1:" + port + "/hello?name=jdk"))
                .GET()
                .build()
            val response =
              client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString())
            assertTrue(response.statusCode() == 200, response.body() == "/hello|name=jdk")
          }
        }
      },
      test("closes the connection after Connection: close") {
        withH1Server(routes) { port =>
          ZIO.attemptBlocking {
            val client = new RawH1Client(port)
            try {
              client.sendRaw("GET / HTTP/1.1\r\nHost: 127.0.0.1\r\nConnection: close\r\n\r\n")
              val resp = client.readResponse()
              val eof  = client.readByte()
              assertTrue(
                resp.status == 200 && resp.bodyText == "ok",
                resp.header("connection").exists(_.equalsIgnoreCase("close")),
                eof == -1,
              )
            } finally client.close()
          }
        }
      },
      test("lets request close win over a handler keep-alive") {
        withH1Server(routes) { port =>
          ZIO.attemptBlocking {
            val client = new RawH1Client(port)
            try {
              client.sendRaw("GET /keepalive HTTP/1.1\r\nHost: 127.0.0.1\r\nConnection: close\r\n\r\n")
              val resp = client.readResponse()
              val eof  = client.readByte()
              assertTrue(
                resp.status == 200 && resp.bodyText == "ka",
                resp.header("connection").exists(_.equalsIgnoreCase("close")),
                eof == -1,
              )
            } finally client.close()
          }
        }
      },
      test("trusted proxy headers resolve the client IP, host and scheme") {
        withH1Server(ProxyRoutes, trustedProxyConnector) { port =>
          ZIO.attemptBlocking {
            val client = new RawH1Client(port)
            try {
              client.sendRaw(
                "GET /whoami HTTP/1.1\r\nHost: 127.0.0.1\r\n" +
                  "X-Forwarded-For: 203.0.113.7\r\n" +
                  "X-Forwarded-Proto: https\r\n" +
                  "X-Forwarded-Host: example.com\r\n\r\n",
              )
              val resp = client.readResponse()
              assertTrue(
                resp.status == 200,
                resp.bodyText == "203.0.113.7|127.0.0.1|none|example.com|https",
              )
            } finally client.close()
          }
        }
      },
      test("untrusted proxy headers are stripped with zero effect") {
        withH1Server(ProxyRoutes) { port =>
          ZIO.attemptBlocking {
            val client = new RawH1Client(port)
            try {
              client.sendRaw(
                "GET /whoami HTTP/1.1\r\nHost: 127.0.0.1\r\n" +
                  "X-Forwarded-For: 203.0.113.7\r\n" +
                  "X-Forwarded-Proto: https\r\n" +
                  "X-Forwarded-Host: example.com\r\n\r\n",
              )
              val resp = client.readResponse()
              assertTrue(
                resp.status == 200,
                resp.bodyText == "127.0.0.1|127.0.0.1|none|none|none",
              )
            } finally client.close()
          }
        }
      },
      test("request telemetry leaves wire behavior unchanged") {
        withH1Server(routes, telemetryConnector) { port =>
          ZIO.attemptBlocking {
            val client = new RawH1Client(port)
            try {
              client.sendRaw("GET / HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n")
              val first  = client.readResponse()
              client.sendRaw("GET /hello?name=telemetry HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n")
              val second = client.readResponse()
              assertTrue(
                first.status == 200 && first.bodyText == "ok",
                second.status == 200 && second.bodyText == "/hello|name=telemetry",
                first.header("connection").isEmpty,
                second.header("connection").isEmpty,
              )
            } finally client.close()
          }
        }
      },
    ) @@ sequential
}
