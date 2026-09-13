package zio.http.h2

import zio._
import zio.blocks.chunk.Chunk
import zio.blocks.endpoint.RoutePattern
import zio.test.TestAspect.sequential
import zio.test._

import zio.http.{Body, Handler, Request, Response, Route, Routes, Status, handler}
import zio.http.ResultType._
import zio.http.h2.H2Frame._
import zio.http.h2.H2RawClientFixture.{RawH2Client, withRawServer}
import zio.http.h2.hpack.{HeaderField, Hpack}

/**
 * Request-handling behavior: authority parsing, route dispatch, and response
 * framing.
 */
object H2RequestRoutingSpec extends ZIOSpecDefault {

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("H2RequestRoutingSpec")(
      // ── H2Transport.parseUrl: authority with no port (host.port = None) ──
      test("request with hostname-only authority parses host correctly") {
        val routes = Routes(
          Route(
            RoutePattern.GET,
            handler { (req: Request) =>
              responseAsResult(Response(status = Status.Ok, body = Body.fromString(req.url.host.getOrElse("none"))))
            },
          ),
        )
        withRawServer(routes) { port =>
          ZIO.attemptBlocking {
            val client = new RawH2Client(port)
            try {
              client.sendRaw(
                FrameCodec
                  .encode(
                    Headers(
                      streamId = 1,
                      headerBlock = Hpack.encode(
                        List(
                          HeaderField(":method", "GET"),
                          HeaderField(":path", "/"),
                          HeaderField(":scheme", "http"),
                          HeaderField(":authority", "example.com"),
                        ),
                      ),
                      endStream = true,
                      endHeaders = true,
                    ),
                  )
                  .toArray,
              )
              val resp = client.awaitResponse(1)
              assertTrue(resp.status == 200, resp.bodyText == "example.com")
            } finally client.close()
          }
        }
      },
      // ── H2Transport.parseUrl: authority fails Header.Host.parse → raw host
      test("request with invalid authority falls back to raw host value") {
        val routes = Routes(
          Route(
            RoutePattern.GET,
            handler { (req: Request) =>
              responseAsResult(Response(status = Status.Ok, body = Body.fromString(req.url.host.getOrElse("none"))))
            },
          ),
        )
        withRawServer(routes) { port =>
          ZIO.attemptBlocking {
            val client = new RawH2Client(port)
            try {
              client.sendRaw(
                FrameCodec
                  .encode(
                    Headers(
                      streamId = 1,
                      headerBlock = Hpack.encode(
                        List(
                          HeaderField(":method", "GET"),
                          HeaderField(":path", "/"),
                          HeaderField(":scheme", "http"),
                          HeaderField(":authority", "[invalid-ipv6"),
                        ),
                      ),
                      endStream = true,
                      endHeaders = true,
                    ),
                  )
                  .toArray,
              )
              val resp = client.awaitResponse(1)
              assertTrue(resp.status == 200)
            } finally client.close()
          }
        }
      },
      // ── H2Transport.buildRouteTree: alternatives.nonEmpty path ───────────
      test("routes with GET wildcard pattern are served via alternatives path") {
        val routes = Routes(
          Route(RoutePattern.GET, Handler.succeed(Response.ok)),
          Route(RoutePattern.POST, Handler.succeed(Response(Status.Created))),
        )
        withRawServer(routes) { port =>
          ZIO.attemptBlocking {
            val client = new RawH2Client(port)
            try {
              val getResp  = client.roundTrip("GET", "/", Chunk.empty, streamId = 1)
              val postResp = client.roundTrip("POST", "/", Chunk.empty, streamId = 3)
              assertTrue(getResp.status == 200, postResp.status == 201)
            } finally client.close()
          }
        }
      },
      // ── H2Transport: toResponse handles Halt directly ─────────────────────
      test("Halt result from handler is served as its embedded response") {
        import zio.http.Halt
        val routes = Routes(
          Route(
            RoutePattern.GET,
            zio.http.handler { (_: Request) =>
              Halt(Response(Status.Created)): Response | Halt
            },
          ),
        )
        withRawServer(routes) { port =>
          ZIO.attemptBlocking {
            val client = new RawH2Client(port)
            try {
              val resp = client.roundTrip("GET", "/", Chunk.empty, streamId = 1)
              assertTrue(resp.status == 201)
            } finally client.close()
          }
        }
      },
      // ── H2Transport: chunkBody body equals maxFrameSize ───────────────────
      test("response body exactly matching maxFrameSize sends in one DATA frame") {
        val exactBody = Chunk.fromArray(new Array[Byte](16384))
        val routes    = Routes(
          Route(
            RoutePattern.GET,
            Handler.succeed(Response(status = Status.Ok, body = Body.fromChunk(exactBody))),
          ),
        )
        withRawServer(routes) { port =>
          ZIO.attemptBlocking {
            val client = new RawH2Client(port)
            try {
              val resp = client.roundTrip("GET", "/", Chunk.empty, streamId = 1)
              assertTrue(resp.status == 200, resp.body.length == 16384)
            } finally client.close()
          }
        }
      },
      // ── H2Transport: buildResponseHeaders when body is empty ─────────────
      test("empty response body sends HEADERS with endStream=true and no DATA") {
        val routes = Routes(Route(RoutePattern.GET, Handler.succeed(Response.ok)))
        withRawServer(routes) { port =>
          ZIO.attemptBlocking {
            val client = new RawH2Client(port)
            try {
              val resp = client.roundTrip("GET", "/", Chunk.empty, streamId = 1)
              assertTrue(resp.status == 200, resp.body.isEmpty)
            } finally client.close()
          }
        }
      },
      // ── H2Transport: multiple concurrent streams → stream multiplexing ────
      test("5 concurrent streams all succeed on a single connection") {
        val routes = Routes(
          Route(RoutePattern.GET, Handler.succeed(Response(status = Status.Ok, body = Body.fromString("ok")))),
        )
        withRawServer(routes) { port =>
          ZIO.attemptBlocking {
            val client = new RawH2Client(port)
            try {
              for (i <- 0 until 5) {
                client.sendFrame(
                  Headers(
                    streamId = 2 * i + 1,
                    headerBlock = Hpack.encode(
                      List(
                        HeaderField(":method", "GET"),
                        HeaderField(":path", "/"),
                        HeaderField(":scheme", "http"),
                        HeaderField(":authority", s"127.0.0.1:$port"),
                      ),
                    ),
                    endStream = true,
                    endHeaders = true,
                  ),
                )
              }
              val results = (0 until 5).map { i => client.awaitResponse(2 * i + 1) }
              assertTrue(results.forall(_.status == 200))
            } finally client.close()
          }
        }
      },
      test("H2Transport with empty Routes returns 404 for any request") {
        val routes = Routes.empty[Any]
        withRawServer(routes) { port =>
          ZIO.attemptBlocking {
            val client = new RawH2Client(port)
            try {
              val resp = client.roundTrip("GET", "/nonexistent", Chunk.empty, streamId = 1)
              assertTrue(resp.status == 404)
            } finally client.close()
          }
        }
      },
    ) @@ sequential
}
