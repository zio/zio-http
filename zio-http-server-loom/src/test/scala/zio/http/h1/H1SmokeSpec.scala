package zio.http.h1

import java.nio.charset.StandardCharsets

import zio._
import zio.blocks.endpoint.RoutePattern
import zio.blocks.streams.Stream
import zio.test.TestAspect.sequential
import zio.test._

import zio.http.ResultType._
import zio.http.h1.H1RawClientFixture._
import zio.http.{Body, Handler, Method, Request, Response, Route, Routes, Status, handler}

/**
 * Live-wire smoke coverage for the Loom H1 engine.
 *
 * Every test goes through a real loopback socket and `LoomServer` configured
 * with an explicit cleartext `Protocol.H1C` connector: request bytes in,
 * strict-codec response bytes out via [[H1RawClientFixture.RawH1Client]]. The
 * suite pins the baseline transport contract the later smuggling/limits specs
 * build on: Content-Length request/response framing, sequential keep-alive on
 * one socket, chunked request bodies, chunked unknown-length responses,
 * `Connection: close`, and bodyless HEAD.
 */
object H1SmokeSpec extends ZIOSpecDefault {

  private val RootBody       = "h1-smoke-root"
  private val StreamBodyText = "h1-smoke-stream-body"
  private val EchoBody       = "h1-smoke-echo-body"

  private def bytes(text: String): Array[Byte] =
    text.getBytes(StandardCharsets.UTF_8)

  /** Fresh unknown-length byte stream per invocation (never a known chunk). */
  private def streamingBody: Body = {
    val bodyBytes = bytes(StreamBodyText)
    Body.fromStream(
      Stream.unfold(0) { index =>
        if (index >= bodyBytes.length) None
        else Some((bodyBytes(index), index + 1))
      },
    )
  }

  private val routes: Routes[Any] = Routes(
    Route(
      RoutePattern.GET,
      handler { (_: Request) =>
        responseAsResult(Response(status = Status.Ok, body = Body.fromString(RootBody)))
      },
    ),
    Route(
      RoutePattern(Method.POST, "/echo"),
      handler { (req: Request) =>
        responseAsResult(Response(status = Status.Ok, body = req.body))
      },
    ),
    Route(
      RoutePattern(Method.GET, "/stream"),
      handler { (_: Request) =>
        responseAsResult(Response(status = Status.Ok, body = streamingBody))
      },
    ),
  )

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("H1SmokeSpec")(
      test("GET / returns 200 with the route body and a Content-Length header") {
        withH1Server(routes) { port =>
          ZIO.attemptBlocking {
            val client = new RawH1Client(port)
            try {
              client.sendRaw("GET / HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n")
              val response = client.readResponse()
              assertTrue(
                response.status == 200,
                response.bodyText == RootBody,
                response.header("content-length").contains(bytes(RootBody).length.toString),
              )
            } finally client.close()
          }
        }
      },
      test("POST with a Content-Length body is echoed by the handler") {
        withH1Server(routes) { port =>
          ZIO.attemptBlocking {
            val client = new RawH1Client(port)
            try {
              client.sendRaw(
                "POST /echo HTTP/1.1\r\nHost: 127.0.0.1\r\nContent-Length: " + bytes(EchoBody).length + "\r\n\r\n" +
                  EchoBody,
              )
              val response = client.readResponse()
              assertTrue(
                response.status == 200,
                response.bodyText == EchoBody,
              )
            } finally client.close()
          }
        }
      },
      test("two sequential requests on one RawH1Client reuse the keep-alive connection") {
        withH1Server(routes) { port =>
          ZIO.attemptBlocking {
            val client = new RawH1Client(port)
            try {
              client.sendRaw("GET / HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n")
              val first  = client.readResponse()
              client.sendRaw(
                "POST /echo HTTP/1.1\r\nHost: 127.0.0.1\r\nContent-Length: " + bytes(EchoBody).length + "\r\n\r\n" +
                  EchoBody,
              )
              val second = client.readResponse()
              assertTrue(
                first.status == 200 && first.bodyText == RootBody,
                second.status == 200 && second.bodyText == EchoBody,
                first.header("connection").isEmpty,
                second.header("connection").isEmpty,
              )
            } finally client.close()
          }
        }
      },
      test("chunked request body is decoded and echoed") {
        withH1Server(routes) { port =>
          ZIO.attemptBlocking {
            val client = new RawH1Client(port)
            try {
              client.sendRaw(
                "POST /echo HTTP/1.1\r\nHost: 127.0.0.1\r\nTransfer-Encoding: chunked\r\n\r\n" +
                  "5\r\nhello\r\n6\r\n world\r\n0\r\n\r\n",
              )
              val response = client.readResponse()
              assertTrue(
                response.status == 200,
                response.bodyText == "hello world",
              )
            } finally client.close()
          }
        }
      },
      test("Connection: close is reflected on the response and the socket reaches EOF") {
        withH1Server(routes) { port =>
          ZIO.attemptBlocking {
            val client = new RawH1Client(port)
            try {
              client.sendRaw("GET / HTTP/1.1\r\nHost: 127.0.0.1\r\nConnection: close\r\n\r\n")
              val response = client.readResponse()
              val eof      = client.readByte()
              assertTrue(
                response.status == 200 && response.bodyText == RootBody,
                response.header("connection").exists(_.equalsIgnoreCase("close")),
                eof == -1,
              )
            } finally client.close()
          }
        }
      },
      test("unknown-length streaming response is chunked and decodes to the route body") {
        withH1Server(routes) { port =>
          ZIO.attemptBlocking {
            val client = new RawH1Client(port)
            try {
              client.sendRaw("GET /stream HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n")
              val response = client.readResponse()
              assertTrue(
                response.status == 200,
                response.header("transfer-encoding").exists(_.toLowerCase(java.util.Locale.ROOT).contains("chunked")),
                response.bodyText == StreamBodyText,
              )
            } finally client.close()
          }
        }
      },
      test("HEAD returns headers only with no body bytes and the connection stays usable") {
        withH1Server(routes) { port =>
          ZIO.attemptBlocking {
            val client = new RawH1Client(port)
            try {
              client.sendRaw("HEAD / HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n")
              val head     = client.readResponse(readBody = false)
              // A body sent after HEAD would desync this next parse on the wire.
              client.sendRaw("GET / HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n")
              val followUp = client.readResponse()
              assertTrue(
                head.status == 200,
                head.body.isEmpty,
                head.header("content-length").contains(bytes(RootBody).length.toString),
                followUp.status == 200 && followUp.bodyText == RootBody,
              )
            } finally client.close()
          }
        }
      },
    ) @@ sequential
}
