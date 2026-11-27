package zio.http.h1

import java.io.EOFException
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger

import zio._
import zio.blocks.chunk.Chunk
import zio.blocks.endpoint.RoutePattern
import zio.test.TestAspect.sequential
import zio.test._

import zio.http.ResultType._
import zio.http.h1.H1RawClientFixture._
import zio.http.{Body, Method, Request, Response, Route, Routes, Status, handler}

/**
 * H1 request-desync/smuggling corpus for the hardened Loom H1 engine: the
 * security proof that cleartext H1 dispatch never trusts ambiguous framing.
 *
 * Two layers pin the same boundary. The direct `H1Decoder` suite classifies
 * every vector by error type and `mustClose`, and additionally asserts decoder
 * poisoning (`poisoned`, cleared buffer, and a `DecoderPoisoned` follow-up);
 * one smuggling vector is also fed byte-at-a-time to prove fragmented delivery
 * still rejects. The live suite drives real raw sockets against
 * `LoomServer(Protocol.H1C)`: every rejection answers the mapped status (`400`,
 * `413` for oversize bodies, `417` for unsupported `Expect`) with
 * `Connection: close`, closes the socket, never dispatches a handler, never
 * serves a smuggled victim request on the same bytes, and leaves a fresh
 * connection healthy afterwards. Live accept vectors prove the hardened rules
 * do not over-reject: `Expect: 100-continue` gets the interim `100 Continue`
 * then the final response, announced chunked trailers decode, and an
 * absolute-form target with a matching `Host` is served.
 *
 * Hardened boundary pinned here (deliberately stricter than RFC obs-text
 * tolerance): a message starts exactly with its request line (a leading CRLF is
 * fatal, never skipped); exactly one length mechanism; duplicate or conflicting
 * Content-Length, any Transfer-Encoding other than a single `chunked`, chunk
 * extensions, and framing headers inside trailers are fatal; bare LF, obs-fold,
 * whitespace before the colon, control bytes, and non-ASCII (>= 0x80) bytes are
 * rejected in header names, header values, and targets alike; every request
 * carries exactly one non-empty `Host` (missing or empty is fatal regardless of
 * target form), agreeing with the absolute-form authority (host and port,
 * scheme defaults 80/443); chunked trailers must be announced via `Trailer`
 * (announced-but-absent is fine); `Expect` must be a single `100-continue` or
 * the request fails `417`.
 *
 * Wire encoding is ISO-8859-1 on both layers (codec feed and socket bytes):
 * high bytes reach the parser unmangled (US_ASCII would silently replace them)
 * and are rejected by the hardened value/name/target grammars.
 *
 * No pipelining, proxy, Upgrade, H3, or implementation changes: tests only.
 */
object H1RequestSmugglingSpec extends ZIOSpecDefault {

  private def wire(s: String): Chunk[Byte] =
    Chunk.fromArray(s.getBytes("ISO-8859-1"))

  private def codecRejectsAs[E <: H1Error](raw: String)(implicit
    tag: scala.reflect.ClassTag[E],
  ): Boolean = {
    val decoder = new H1Decoder()
    decoder.feed(wire(raw)) match {
      case Left(error) =>
        val poisonedReuse = decoder.feed(wire("GET / HTTP/1.1\r\nHost: x\r\n\r\n")) match {
          case Left(_: H1Error.DecoderPoisoned) => true
          case _                                => false
        }
        tag.runtimeClass.isInstance(error) &&
        error.mustClose &&
        decoder.poisoned &&
        decoder.bufferedBytes == 0 &&
        poisonedReuse
      case Right(_)    => false
    }
  }

  /**
   * Feeds `raw` once and checks the single decoded request; false on any
   * rejection.
   */
  private def completesWith(raw: String)(check: H1Request => Boolean): Boolean = {
    val decoder = new H1Decoder()
    decoder.feed(wire(raw)) match {
      case Right(List(request)) => !decoder.poisoned && check(request)
      case _                    => false
    }
  }

  private def countingRoutes(counter: AtomicInteger): Routes[Any] =
    Routes(
      Route(
        RoutePattern.GET,
        handler { (_: Request) =>
          counter.incrementAndGet()
          responseAsResult(Response(status = Status.Ok, body = Body.fromString("victim")))
        },
      ),
      Route(
        RoutePattern(Method.POST, "/echo"),
        handler { (req: Request) =>
          counter.incrementAndGet()
          responseAsResult(Response(status = Status.Ok, body = req.body))
        },
      ),
    )

  /**
   * Sends one smuggling vector at a live engine and proves the full
   * reject+close contract: expected status with `Connection: close`, the socket
   * closed (clean FIN or RST after unread smuggle bytes), no handler dispatch,
   * no second response on the poisoned socket, and a healthy victim on a fresh
   * connection.
   */
  private def rejectsLive(raw: String, expectedStatus: Int): ZIO[Scope, Throwable, TestResult] = {
    val counter = new AtomicInteger(0)
    withH1Server(countingRoutes(counter)) { port =>
      ZIO.attemptBlocking {
        val client = new RawH1Client(port)
        try {
          // ISO-8859-1, aligned with the codec feed: high bytes reach the
          // parser unmangled (US_ASCII would silently replace them).
          client.sendRaw(raw.getBytes("ISO-8859-1"))
          val resp                  = client.readResponse()
          // Unread smuggled bytes may turn the close into a reset instead of
          // a clean FIN; both prove the connection died with the request.
          val closed                =
            try client.readByte() == -1
            catch {
              case _: java.net.SocketException => true
            }
          val retryClosed           =
            try {
              client.sendRaw("GET / HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n")
              client.readResponse()
              "served"
            } catch {
              case _: EOFException                    => "closed"
              case _: java.net.SocketException        => "closed"
              case _: java.net.SocketTimeoutException => "closed"
            }
          val dispatchedBeforeFresh = counter.get()
          val fresh                 = new RawH1Client(port)
          try {
            fresh.sendRaw("GET / HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n")
            val victim = fresh.readResponse()
            assertTrue(
              resp.status == expectedStatus,
              resp.header("connection").exists(_.equalsIgnoreCase("close")),
              closed,
              retryClosed == "closed",
              dispatchedBeforeFresh == 0,
              victim.status == 200 && victim.bodyText == "victim",
              counter.get() == 1,
            )
          } finally fresh.close()
        } finally client.close()
      }
    }
  }

  /**
   * Sends one accepted request at a live engine and proves no over-rejection:
   * the expected 200 body, exactly one dispatch, and the same connection still
   * serving a follow-up request (the hardened rules never close early).
   */
  private def acceptsLive(raw: String, expectedBody: String): ZIO[Scope, Throwable, TestResult] = {
    val counter = new AtomicInteger(0)
    withH1Server(countingRoutes(counter)) { port =>
      ZIO.attemptBlocking {
        val client = new RawH1Client(port)
        try {
          client.sendRaw(raw.getBytes("ISO-8859-1"))
          val response                = client.readResponse()
          // Snapshot before the follow-up: assertTrue evaluates its arguments
          // eagerly, so the one-dispatch proof must be captured up front.
          val dispatchedAfterAccepted = counter.get()
          client.sendRaw("GET / HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n")
          val followUp                = client.readResponse()
          assertTrue(
            response.status == 200,
            response.bodyText == expectedBody,
            dispatchedAfterAccepted == 1,
            followUp.status == 200 && followUp.bodyText == "victim",
            counter.get() == 2,
          )
        } finally client.close()
      }
    }
  }

  /**
   * Reads one interim response head (status line + headers + blank line)
   * byte-at-a-time, stopping after the terminating CRLFCRLF; the fixture's
   * response reader cannot consume a bodyless 1xx head (it would wait for a
   * body that never comes), so the interim 100 is read explicitly.
   */
  private def readInterimHead(client: RawH1Client): String = {
    val buf  = new StringBuilder
    var done = false
    while (!done) {
      val b = client.readByte()
      if (b < 0) done = true
      else {
        buf.append(b.toChar)
        done = buf.length >= 4 &&
          buf.charAt(buf.length - 4) == '\r' && buf.charAt(buf.length - 3) == '\n' &&
          buf.charAt(buf.length - 2) == '\r' && buf.charAt(buf.length - 1) == '\n'
      }
    }
    buf.toString
  }

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("H1RequestSmugglingSpec")(
      suite("strict codec boundary")(
        test("CL.TE classifies AmbiguousFraming must-close") {
          assertTrue(
            codecRejectsAs[H1Error.AmbiguousFraming](
              "POST /echo HTTP/1.1\r\nHost: x\r\nContent-Length: 4\r\nTransfer-Encoding: chunked\r\n\r\n",
            ),
          )
        },
        test("TE.CL ordering classifies AmbiguousFraming must-close") {
          assertTrue(
            codecRejectsAs[H1Error.AmbiguousFraming](
              "POST /echo HTTP/1.1\r\nHost: x\r\nTransfer-Encoding: chunked\r\nContent-Length: 4\r\n\r\n",
            ),
          )
        },
        test("conflicting Content-Length values classify AmbiguousFraming must-close") {
          assertTrue(
            codecRejectsAs[H1Error.AmbiguousFraming](
              "POST /echo HTTP/1.1\r\nHost: x\r\nContent-Length: 5\r\nContent-Length: 6\r\n\r\n",
            ),
          )
        },
        test("duplicated identical Content-Length classifies AmbiguousFraming must-close") {
          assertTrue(
            codecRejectsAs[H1Error.AmbiguousFraming](
              "POST /echo HTTP/1.1\r\nHost: x\r\nContent-Length: 5\r\nContent-Length: 5\r\n\r\n",
            ),
          )
        },
        test("duplicated Transfer-Encoding classifies AmbiguousFraming and poisons") {
          assertTrue(
            codecRejectsAs[H1Error.AmbiguousFraming](
              "POST /echo HTTP/1.1\r\nHost: x\r\nTransfer-Encoding: chunked\r\nTransfer-Encoding: chunked\r\n\r\n",
            ),
          )
        },
        test("unsupported gzip coding classifies AmbiguousFraming and poisons") {
          assertTrue(
            codecRejectsAs[H1Error.AmbiguousFraming](
              "POST /echo HTTP/1.1\r\nHost: x\r\nTransfer-Encoding: gzip\r\n\r\n",
            ),
          )
        },
        test("transfer coding list classifies AmbiguousFraming and poisons") {
          assertTrue(
            codecRejectsAs[H1Error.AmbiguousFraming](
              "POST /echo HTTP/1.1\r\nHost: x\r\nTransfer-Encoding: gzip, chunked\r\n\r\n",
            ),
          )
        },
        test("empty Transfer-Encoding classifies AmbiguousFraming and poisons") {
          assertTrue(
            codecRejectsAs[H1Error.AmbiguousFraming](
              "POST /echo HTTP/1.1\r\nHost: x\r\nTransfer-Encoding:\r\n\r\n",
            ),
          )
        },
        test("whitespace before the header colon classifies InvalidMessage must-close") {
          assertTrue(
            codecRejectsAs[H1Error.InvalidMessage](
              "POST /echo HTTP/1.1\r\nHost : x\r\nContent-Length: 0\r\n\r\n",
            ),
          )
        },
        test("control byte in a header value classifies InvalidMessage must-close") {
          assertTrue(
            codecRejectsAs[H1Error.InvalidMessage](
              "POST /echo HTTP/1.1\r\nHost: x\u0001\r\nContent-Length: 0\r\n\r\n",
            ),
          )
        },
        test("control byte in a header name classifies InvalidMessage must-close") {
          assertTrue(
            codecRejectsAs[H1Error.InvalidMessage](
              "POST /echo HTTP/1.1\r\nHo\u007fst: x\r\nContent-Length: 0\r\n\r\n",
            ),
          )
        },
        test("whitespace in the request target classifies InvalidMessage must-close") {
          assertTrue(
            codecRejectsAs[H1Error.InvalidMessage](
              "GET /vic tim HTTP/1.1\r\nHost: x\r\n\r\n",
            ),
          )
        },
        test("bare LF line endings classify InvalidMessage and poison") {
          assertTrue(
            codecRejectsAs[H1Error.InvalidMessage](
              "GET /echo HTTP/1.1\nHost: x\n\n",
            ),
          )
        },
        test("high byte in a header name classifies InvalidMessage and poisons") {
          assertTrue(
            codecRejectsAs[H1Error.InvalidMessage](
              "POST /echo HTTP/1.1\r\nHoÿst: x\r\nContent-Length: 0\r\n\r\n",
            ),
          )
        },
        test("high byte in a header value classifies InvalidMessage and poisons") {
          assertTrue(
            codecRejectsAs[H1Error.InvalidMessage](
              "POST /echo HTTP/1.1\r\nHost: x\r\nX-High: vÿ\r\nContent-Length: 0\r\n\r\n",
            ),
          )
        },
        test("obs-fold classifies InvalidMessage must-close") {
          assertTrue(
            codecRejectsAs[H1Error.InvalidMessage](
              "POST /echo HTTP/1.1\r\nHost: x\r\n X-Folded: 1\r\nContent-Length: 0\r\n\r\n",
            ),
          )
        },
        test("Trailer without chunked classifies AmbiguousFraming must-close") {
          assertTrue(
            codecRejectsAs[H1Error.AmbiguousFraming](
              "POST /echo HTTP/1.1\r\nHost: x\r\nContent-Length: 0\r\nTrailer: X-Sum\r\n\r\n",
            ),
          )
        },
        test("Content-Length inside trailers classifies AmbiguousFraming must-close") {
          assertTrue(
            codecRejectsAs[H1Error.AmbiguousFraming](
              "POST /echo HTTP/1.1\r\nHost: x\r\nTransfer-Encoding: chunked\r\n\r\n0\r\nContent-Length: 5\r\n\r\n",
            ),
          )
        },
        test("Transfer-Encoding inside trailers classifies AmbiguousFraming must-close") {
          assertTrue(
            codecRejectsAs[H1Error.AmbiguousFraming](
              "POST /echo HTTP/1.1\r\nHost: x\r\nTransfer-Encoding: chunked\r\n\r\n0\r\nTransfer-Encoding: chunked\r\n\r\n",
            ),
          )
        },
        test("corrupt chunk terminator classifies InvalidMessage must-close") {
          assertTrue(
            codecRejectsAs[H1Error.InvalidMessage](
              "POST /echo HTTP/1.1\r\nHost: x\r\nTransfer-Encoding: chunked\r\n\r\n5\r\nhelloXX\r\n0\r\n\r\n",
            ),
          )
        },
        test("header-count flood classifies TooManyHeaders must-close") {
          val head = new StringBuilder("POST /echo HTTP/1.1\r\nHost: x\r\n")
          var i    = 0
          while (i < 101) {
            head.append("x-flood-").append(i.toString).append(": v\r\n")
            i += 1
          }
          head.append("\r\n")
          assertTrue(codecRejectsAs[H1Error.TooManyHeaders](head.toString))
        },
        test("oversize declared body classifies BodyTooLarge must-close") {
          assertTrue(
            codecRejectsAs[H1Error.BodyTooLarge](
              "POST /echo HTTP/1.1\r\nHost: x\r\nContent-Length: 2000000\r\n\r\n",
            ),
          )
        },
        test("Content-Length with inner whitespace classifies InvalidMessage and poisons") {
          assertTrue(
            codecRejectsAs[H1Error.InvalidMessage](
              "POST /echo HTTP/1.1\r\nHost: x\r\nContent-Length: 1 0\r\n\r\n",
            ),
          )
        },
        test("non-numeric Content-Length classifies InvalidMessage and poisons") {
          assertTrue(
            codecRejectsAs[H1Error.InvalidMessage](
              "POST /echo HTTP/1.1\r\nHost: x\r\nContent-Length: 12a\r\n\r\n",
            ),
          )
        },
        test("negative Content-Length classifies InvalidMessage and poisons") {
          assertTrue(
            codecRejectsAs[H1Error.InvalidMessage](
              "POST /echo HTTP/1.1\r\nHost: x\r\nContent-Length: -5\r\n\r\n",
            ),
          )
        },
        test("chunk extensions classify InvalidMessage and poison") {
          assertTrue(
            codecRejectsAs[H1Error.InvalidMessage](
              "POST /echo HTTP/1.1\r\nHost: x\r\nTransfer-Encoding: chunked\r\n\r\n5;ext=x\r\nhello\r\n0\r\n\r\n",
            ),
          )
        },
        test("empty chunk-size line classifies InvalidMessage and poisons") {
          assertTrue(
            codecRejectsAs[H1Error.InvalidMessage](
              "POST /echo HTTP/1.1\r\nHost: x\r\nTransfer-Encoding: chunked\r\n\r\n\r\n",
            ),
          )
        },
        test("byte-at-a-time CL.TE feed still rejects AmbiguousFraming and poisons") {
          val decoder                                   = new H1Decoder()
          val bytes                                     = wire(
            "POST /echo HTTP/1.1\r\nHost: x\r\nContent-Length: 4\r\nTransfer-Encoding: chunked\r\n\r\n",
          ).toArray
          var outcome: Either[H1Error, List[H1Request]] = Right(Nil)
          var i                                         = 0
          while (i < bytes.length && outcome.isRight) {
            outcome = decoder.feed(Chunk.fromArray(bytes.slice(i, i + 1)))
            i += 1
          }
          val poisonedReuse = decoder.feed(wire("GET / HTTP/1.1\r\nHost: x\r\n\r\n")) match {
            case Left(_: H1Error.DecoderPoisoned) => true
            case _                                => false
          }
          assertTrue(
            outcome match {
              case Left(_: H1Error.AmbiguousFraming) => true
              case _                                 => false
            },
            decoder.poisoned,
            decoder.bufferedBytes == 0,
            poisonedReuse,
          )
        },
        test("incomplete body blocks without error and completes exactly once") {
          val decoder = new H1Decoder()
          val partial = decoder.feed(wire("POST /echo HTTP/1.1\r\nHost: x\r\nContent-Length: 10\r\n\r\nAB"))
          val blocked = partial == Right(Nil) && !decoder.poisoned
          val done    = decoder.feed(wire("CDEFGHIJ")) match {
            case Right(List(request)) =>
              request.target == "/echo" &&
              new String(request.body.toArray, "ISO-8859-1") == "ABCDEFGHIJ" &&
              !decoder.poisoned
            case _                    => false
          }
          assertTrue(blocked, done)
        },
        test("multiple Host headers classify InvalidMessage and poison") {
          assertTrue(
            codecRejectsAs[H1Error.InvalidMessage](
              "GET / HTTP/1.1\r\nHost: a\r\nHost: b\r\n\r\n",
            ),
          )
        },
        test("origin-form target without a Host header classifies InvalidMessage and poisons") {
          assertTrue(
            codecRejectsAs[H1Error.InvalidMessage](
              "GET / HTTP/1.1\r\n\r\n",
            ),
          )
        },
        test("a leading CRLF before the request line classifies InvalidMessage and poisons") {
          assertTrue(
            codecRejectsAs[H1Error.InvalidMessage](
              "\r\nGET / HTTP/1.1\r\nHost: x\r\n\r\n",
            ),
          )
        },
        test("absolute-form target without a Host header classifies InvalidMessage and poisons") {
          assertTrue(
            codecRejectsAs[H1Error.InvalidMessage](
              "GET http://127.0.0.1/ HTTP/1.1\r\n\r\n",
            ),
          )
        },
        test("empty Host header classifies InvalidMessage and poisons") {
          assertTrue(
            codecRejectsAs[H1Error.InvalidMessage](
              "GET / HTTP/1.1\r\nHost:\r\n\r\n",
            ),
          )
        },
        test("absolute-form target with a conflicting Host authority classifies InvalidMessage") {
          assertTrue(
            codecRejectsAs[H1Error.InvalidMessage](
              "GET http://evil.example/ HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n",
            ),
          )
        },
        test("absolute-form target with a conflicting URI port classifies InvalidMessage") {
          assertTrue(
            codecRejectsAs[H1Error.InvalidMessage](
              "GET http://127.0.0.1:8080/ HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n",
            ),
          )
        },
        test("absolute-form target with a conflicting Host port classifies InvalidMessage") {
          assertTrue(
            codecRejectsAs[H1Error.InvalidMessage](
              "GET http://127.0.0.1/ HTTP/1.1\r\nHost: 127.0.0.1:9090\r\n\r\n",
            ),
          )
        },
        test("absolute-form target with a scheme-default matching Host decodes") {
          assertTrue(
            completesWith("GET http://127.0.0.1/ HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n")(_.target == "http://127.0.0.1/"),
          )
        },
        test("absolute-form target with an explicit default port decodes") {
          assertTrue(
            completesWith("GET http://127.0.0.1:80/ HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n")(
              _.target == "http://127.0.0.1:80/",
            ),
          )
        },
        test("https absolute-form target with a scheme-default matching Host decodes") {
          assertTrue(
            completesWith("GET https://127.0.0.1/ HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n")(
              _.target == "https://127.0.0.1/",
            ),
          )
        },
        test("unannounced trailer without a Trailer header classifies AmbiguousFraming") {
          assertTrue(
            codecRejectsAs[H1Error.AmbiguousFraming](
              "POST /echo HTTP/1.1\r\nHost: x\r\nTransfer-Encoding: chunked\r\n\r\n0\r\nX-Sum: 1\r\n\r\n",
            ),
          )
        },
        test("trailer field not listed in Trailer classifies AmbiguousFraming") {
          assertTrue(
            codecRejectsAs[H1Error.AmbiguousFraming](
              "POST /echo HTTP/1.1\r\nHost: x\r\nTransfer-Encoding: chunked\r\nTrailer: X-Sum\r\n\r\n0\r\nX-Other: 1\r\n\r\n",
            ),
          )
        },
        test("announced trailer decodes with the trailer captured") {
          assertTrue(
            completesWith(
              "POST /echo HTTP/1.1\r\nHost: x\r\nTransfer-Encoding: chunked\r\nTrailer: X-Sum\r\n\r\n5\r\nhello\r\n0\r\nX-Sum: 1\r\n\r\n",
            ) { request =>
              new String(request.body.toArray, "ISO-8859-1") == "hello" &&
              request.trailers.headerValues("X-Sum") == List("1")
            },
          )
        },
        test("announced-but-absent trailer decodes with an empty trailer section") {
          assertTrue(
            completesWith(
              "POST /echo HTTP/1.1\r\nHost: x\r\nTransfer-Encoding: chunked\r\nTrailer: X-Sum\r\n\r\n5\r\nhello\r\n0\r\n\r\n",
            ) { request =>
              new String(request.body.toArray, "ISO-8859-1") == "hello" &&
              request.trailers.fields.isEmpty
            },
          )
        },
        test("unsupported Expect token classifies UnsupportedExpectation and poisons") {
          assertTrue(
            codecRejectsAs[H1Error.UnsupportedExpectation](
              "POST /echo HTTP/1.1\r\nHost: x\r\nExpect: gzip\r\nContent-Length: 5\r\n\r\nhello",
            ),
          )
        },
        test("empty Expect value classifies UnsupportedExpectation and poisons") {
          assertTrue(
            codecRejectsAs[H1Error.UnsupportedExpectation](
              "POST /echo HTTP/1.1\r\nHost: x\r\nExpect:\r\nContent-Length: 0\r\n\r\n",
            ),
          )
        },
        test("multiple Expect headers classify UnsupportedExpectation and poison") {
          assertTrue(
            codecRejectsAs[H1Error.UnsupportedExpectation](
              "POST /echo HTTP/1.1\r\nHost: x\r\nExpect: 100-continue\r\nExpect: 100-continue\r\nContent-Length: 5\r\n\r\nhello",
            ),
          )
        },
        test("a single 100-continue Expect with a body decodes") {
          assertTrue(
            completesWith(
              "POST /echo HTTP/1.1\r\nHost: x\r\nExpect: 100-continue\r\nContent-Length: 5\r\n\r\nhello",
            ) { request =>
              new String(request.body.toArray, "ISO-8859-1") == "hello"
            },
          )
        },
      ),
      suite("live reject, close, and no victim contamination")(
        test("CL.TE smuggled victim rejects 400 with no dispatch") {
          rejectsLive(
            "POST /echo HTTP/1.1\r\nHost: 127.0.0.1\r\nContent-Length: 4\r\nTransfer-Encoding: chunked\r\n\r\n0\r\n\r\nGET / HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n",
            400,
          )
        },
        test("TE.CL ordering smuggled victim rejects 400 with no dispatch") {
          rejectsLive(
            "POST /echo HTTP/1.1\r\nHost: 127.0.0.1\r\nTransfer-Encoding: chunked\r\nContent-Length: 4\r\n\r\n0\r\n\r\nGET / HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n",
            400,
          )
        },
        test("conflicting Content-Length smuggled victim rejects 400 with no dispatch") {
          rejectsLive(
            "POST /echo HTTP/1.1\r\nHost: 127.0.0.1\r\nContent-Length: 5\r\nContent-Length: 6\r\n\r\nGET / HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n",
            400,
          )
        },
        test("duplicated Content-Length smuggled victim rejects 400 with no dispatch") {
          rejectsLive(
            "POST /echo HTTP/1.1\r\nHost: 127.0.0.1\r\nContent-Length: 5\r\nContent-Length: 5\r\n\r\nGET / HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n",
            400,
          )
        },
        test("duplicated Transfer-Encoding rejects 400 with no dispatch") {
          rejectsLive(
            "POST /echo HTTP/1.1\r\nHost: 127.0.0.1\r\nTransfer-Encoding: chunked\r\nTransfer-Encoding: chunked\r\n\r\nGET / HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n",
            400,
          )
        },
        test("unsupported gzip coding rejects 400 with no dispatch") {
          rejectsLive(
            "POST /echo HTTP/1.1\r\nHost: 127.0.0.1\r\nTransfer-Encoding: gzip\r\n\r\nGET / HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n",
            400,
          )
        },
        test("transfer coding list rejects 400 with no dispatch") {
          rejectsLive(
            "POST /echo HTTP/1.1\r\nHost: 127.0.0.1\r\nTransfer-Encoding: gzip, chunked\r\n\r\nGET / HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n",
            400,
          )
        },
        test("empty Transfer-Encoding rejects 400 with no dispatch") {
          rejectsLive(
            "POST /echo HTTP/1.1\r\nHost: 127.0.0.1\r\nTransfer-Encoding:\r\n\r\nGET / HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n",
            400,
          )
        },
        test("whitespace before the header colon rejects 400 with no dispatch") {
          rejectsLive(
            "POST /echo HTTP/1.1\r\nHost : 127.0.0.1\r\nContent-Length: 0\r\n\r\nGET / HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n",
            400,
          )
        },
        test("control byte in a header value rejects 400 with no dispatch") {
          rejectsLive(
            "POST /echo HTTP/1.1\r\nHost: 127.0.0.1\u0001\r\nContent-Length: 0\r\n\r\n",
            400,
          )
        },
        test("control byte in a header name rejects 400 with no dispatch") {
          rejectsLive(
            "POST /echo HTTP/1.1\r\nHo\u007fst: 127.0.0.1\r\nContent-Length: 0\r\n\r\n",
            400,
          )
        },
        test("whitespace in the request target rejects 400 with no dispatch") {
          rejectsLive(
            "GET /vic tim HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n",
            400,
          )
        },
        test("bare LF line endings reject 400 with no dispatch") {
          rejectsLive(
            "GET /echo HTTP/1.1\nHost: 127.0.0.1\n\n",
            400,
          )
        },
        test("high byte in a header name rejects 400 with no dispatch") {
          rejectsLive(
            "POST /echo HTTP/1.1\r\nHoÿst: 127.0.0.1\r\nContent-Length: 0\r\n\r\n",
            400,
          )
        },
        test("high byte in a header value rejects 400 with no dispatch") {
          rejectsLive(
            "POST /echo HTTP/1.1\r\nHost: 127.0.0.1\r\nX-High: vÿ\r\nContent-Length: 0\r\n\r\n",
            400,
          )
        },
        test("obs-fold rejects 400 with no dispatch") {
          rejectsLive(
            "POST /echo HTTP/1.1\r\nHost: 127.0.0.1\r\n X-Folded: 1\r\nContent-Length: 0\r\n\r\nGET / HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n",
            400,
          )
        },
        test("Trailer without chunked rejects 400 with no dispatch") {
          rejectsLive(
            "POST /echo HTTP/1.1\r\nHost: 127.0.0.1\r\nContent-Length: 0\r\nTrailer: X-Sum\r\n\r\nGET / HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n",
            400,
          )
        },
        test("Content-Length inside trailers rejects 400 with no dispatch") {
          rejectsLive(
            "POST /echo HTTP/1.1\r\nHost: 127.0.0.1\r\nTransfer-Encoding: chunked\r\n\r\n0\r\nContent-Length: 5\r\n\r\nGET / HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n",
            400,
          )
        },
        test("Transfer-Encoding inside trailers rejects 400 with no dispatch") {
          rejectsLive(
            "POST /echo HTTP/1.1\r\nHost: 127.0.0.1\r\nTransfer-Encoding: chunked\r\n\r\n0\r\nTransfer-Encoding: chunked\r\n\r\nGET / HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n",
            400,
          )
        },
        test("corrupt chunk terminator with smuggled tail rejects 400 with no dispatch") {
          rejectsLive(
            "POST /echo HTTP/1.1\r\nHost: 127.0.0.1\r\nTransfer-Encoding: chunked\r\n\r\n5\r\nhelloXX\r\n0\r\n\r\nGET / HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n",
            400,
          )
        },
        test("header-count flood rejects 400 with no dispatch") {
          val head = new StringBuilder("POST /echo HTTP/1.1\r\nHost: 127.0.0.1\r\n")
          var i    = 0
          while (i < 101) {
            head.append("x-flood-").append(i.toString).append(": v\r\n")
            i += 1
          }
          head.append("\r\nGET / HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n")
          rejectsLive(head.toString, 400)
        },
        test("oversize declared body rejects 413 with no dispatch") {
          rejectsLive(
            "POST /echo HTTP/1.1\r\nHost: 127.0.0.1\r\nContent-Length: 2000000\r\n\r\nGET / HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n",
            413,
          )
        },
        test("Content-Length with inner whitespace rejects 400 with no dispatch") {
          rejectsLive(
            "POST /echo HTTP/1.1\r\nHost: 127.0.0.1\r\nContent-Length: 1 0\r\n\r\nGET / HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n",
            400,
          )
        },
        test("non-numeric Content-Length rejects 400 with no dispatch") {
          rejectsLive(
            "POST /echo HTTP/1.1\r\nHost: 127.0.0.1\r\nContent-Length: 12a\r\n\r\nGET / HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n",
            400,
          )
        },
        test("negative Content-Length rejects 400 with no dispatch") {
          rejectsLive(
            "POST /echo HTTP/1.1\r\nHost: 127.0.0.1\r\nContent-Length: -5\r\n\r\nGET / HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n",
            400,
          )
        },
        test("chunk extensions reject 400 with no dispatch") {
          rejectsLive(
            "POST /echo HTTP/1.1\r\nHost: 127.0.0.1\r\nTransfer-Encoding: chunked\r\n\r\n5;ext=x\r\nhello\r\n0\r\n\r\nGET / HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n",
            400,
          )
        },
        test("empty chunk-size line rejects 400 with no dispatch") {
          rejectsLive(
            "POST /echo HTTP/1.1\r\nHost: 127.0.0.1\r\nTransfer-Encoding: chunked\r\n\r\n\r\nGET / HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n",
            400,
          )
        },
        test("multiple Host headers reject 400 with no dispatch") {
          rejectsLive(
            "GET / HTTP/1.1\r\nHost: 127.0.0.1\r\nHost: evil.example\r\n\r\nGET / HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n",
            400,
          )
        },
        test("origin-form target without a Host header rejects 400 with no dispatch") {
          rejectsLive(
            "GET / HTTP/1.1\r\n\r\nGET / HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n",
            400,
          )
        },
        test("leading CRLF before the request line rejects 400 with no dispatch") {
          rejectsLive(
            "\r\nGET / HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\nGET / HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n",
            400,
          )
        },
        test("absolute-form target without a Host header rejects 400 with no dispatch") {
          rejectsLive(
            "GET http://127.0.0.1/ HTTP/1.1\r\n\r\nGET / HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n",
            400,
          )
        },
        test("empty Host header rejects 400 with no dispatch") {
          rejectsLive(
            "GET / HTTP/1.1\r\nHost:\r\n\r\nGET / HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n",
            400,
          )
        },
        test("absolute-form target with a conflicting Host authority rejects 400 with no dispatch") {
          rejectsLive(
            "GET http://evil.example/ HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\nGET / HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n",
            400,
          )
        },
        test("absolute-form target with a conflicting port rejects 400 with no dispatch") {
          rejectsLive(
            "GET http://127.0.0.1:8080/ HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\nGET / HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n",
            400,
          )
        },
        test("unannounced trailer without a Trailer header rejects 400 with no dispatch") {
          rejectsLive(
            "POST /echo HTTP/1.1\r\nHost: 127.0.0.1\r\nTransfer-Encoding: chunked\r\n\r\n0\r\nX-Sum: 1\r\n\r\nGET / HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n",
            400,
          )
        },
        test("trailer field not listed in Trailer rejects 400 with no dispatch") {
          rejectsLive(
            "POST /echo HTTP/1.1\r\nHost: 127.0.0.1\r\nTransfer-Encoding: chunked\r\nTrailer: X-Sum\r\n\r\n0\r\nX-Other: 1\r\n\r\nGET / HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n",
            400,
          )
        },
        test("unsupported Expect token rejects 417 with no dispatch") {
          rejectsLive(
            "POST /echo HTTP/1.1\r\nHost: 127.0.0.1\r\nExpect: gzip\r\nContent-Length: 5\r\n\r\nhelloGET / HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n",
            417,
          )
        },
        test("empty Expect value rejects 417 with no dispatch") {
          rejectsLive(
            "POST /echo HTTP/1.1\r\nHost: 127.0.0.1\r\nExpect:\r\nContent-Length: 0\r\n\r\nGET / HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n",
            417,
          )
        },
        test("multiple Expect headers reject 417 with no dispatch") {
          rejectsLive(
            "POST /echo HTTP/1.1\r\nHost: 127.0.0.1\r\nExpect: 100-continue\r\nExpect: 100-continue\r\nContent-Length: 5\r\n\r\nhelloGET / HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n",
            417,
          )
        },
        test("incomplete body dispatches nothing and closes with no response") {
          val counter = new AtomicInteger(0)
          withH1Server(countingRoutes(counter)) { port =>
            ZIO.attemptBlocking {
              val dirty                 = new Socket("127.0.0.1", port)
              dirty.setSoTimeout(10000)
              val outcome               =
                try {
                  val out = dirty.getOutputStream
                  out.write("POST /echo HTTP/1.1\r\nHost: 127.0.0.1\r\nContent-Length: 10\r\n\r\nAB".getBytes("UTF-8"))
                  out.flush()
                  dirty.shutdownOutput()
                  val in  = dirty.getInputStream
                  val buf = new Array[Byte](1024)
                  val n   = in.read(buf)
                  if (n == -1) "closed" else "bytes:" + n.toString
                } catch {
                  case _: EOFException             => "closed"
                  case _: java.net.SocketException => "closed"
                } finally dirty.close()
              val dispatchedBeforeFresh = counter.get()
              val fresh                 = new RawH1Client(port)
              try {
                fresh.sendRaw("GET / HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n")
                val victim = fresh.readResponse()
                assertTrue(
                  outcome == "closed",
                  dispatchedBeforeFresh == 0,
                  victim.status == 200 && victim.bodyText == "victim",
                  counter.get() == 1,
                )
              } finally fresh.close()
            }
          }
        },
      ),
      suite("live accept: no over-rejection")(
        test("Expect 100-continue receives an interim 100 Continue then the final 200 echo, dispatched once") {
          val counter = new AtomicInteger(0)
          withH1Server(countingRoutes(counter)) { port =>
            ZIO.attemptBlocking {
              val client = new RawH1Client(port)
              try {
                client.sendRaw(
                  "POST /echo HTTP/1.1\r\nHost: 127.0.0.1\r\nExpect: 100-continue\r\nContent-Length: 5\r\n\r\nhello",
                )
                val interim        = readInterimHead(client)
                val finalResponse  = client.readResponse()
                // Snapshot before the follow-up: assertTrue evaluates its
                // arguments eagerly, so the one-dispatch proof must be captured
                // before the reuse request dispatches.
                val dispatchedOnce = counter.get()
                client.sendRaw("GET / HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n")
                val followUp       = client.readResponse()
                assertTrue(
                  interim == "HTTP/1.1 100 Continue\r\n\r\n",
                  finalResponse.status == 200,
                  finalResponse.bodyText == "hello",
                  dispatchedOnce == 1,
                  followUp.status == 200 && followUp.bodyText == "victim",
                  counter.get() == 2,
                )
              } finally client.close()
            }
          }
        },
        test("valid chunked request with an announced trailer echoes with exactly one dispatch") {
          acceptsLive(
            "POST /echo HTTP/1.1\r\nHost: 127.0.0.1\r\nTransfer-Encoding: chunked\r\nTrailer: X-Sum\r\n\r\n5\r\nhello\r\n0\r\nX-Sum: 1\r\n\r\n",
            "hello",
          )
        },
        test("absolute-form target with a matching Host header is served with exactly one dispatch") {
          acceptsLive(
            "GET http://127.0.0.1/ HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n",
            "victim",
          )
        },
      ),
    ) @@ sequential
}
