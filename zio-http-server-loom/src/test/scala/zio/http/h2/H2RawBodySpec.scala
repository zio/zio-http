package zio.http.h2

import java.nio.charset.StandardCharsets

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

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
  BindAddress,
  Body,
  BoundAddress,
  Connector,
  LoomServer,
  Method,
  Request,
  Response,
  Route,
  Routes,
  Status,
  handler,
}

/**
 * Raw-body access + HMAC-before-decode round-trip proof.
 *
 * The H2 wire path must preserve request bytes exactly: `H2Transport`
 * reassembles DATA frames byte-identically under the general
 * `Connector.maxRequestBodySize` bound, and handlers tap the raw bytes via the
 * synchronous `Body.toChunk` accessor BEFORE any decoding runs. This spec pins
 * that contract against a real loopback `LoomServer` using a fixed HMAC-SHA256
 * test vector (RFC 4231, Test Case 1, independently verified with OpenSSL — see
 * `ExpectedMacHex` below):
 *
 *   - Key (20 bytes): 0x0b repeated 20 times
 *   - Message: "Hi There" (8 ASCII bytes, no trailing newline)
 *   - HMAC-SHA256:
 *     b0344c61d8db38535ca8afceaf0bf12b881dc200c9833da726e9376c2e32cff7
 *
 * Byte fidelity is what makes the MAC meaningful: any re-encoding, trim, or
 * dropped byte on the H2 path changes the MAC, so matching the vector proves
 * the raw bytes survived the round-trip untouched.
 *
 * Body-tap guidance: in v4 the handler runs on the stream thread with the fully
 * reassembled body, so `request.body.toChunk` is the HMAC tap point — hash
 * first, decode the bytes into domain types second. Verification,
 * deduplication, and reconciliation of MACs against stored keys is NOT done
 * here: that business logic belongs to Qaizn. The handoff line is the hex MAC
 * string this spec asserts on — Qaizn consumes `(keyId, messageBytes, macHex)`
 * triples produced exactly this way and owns accept/reject.
 */
object H2RawBodySpec extends ZIOSpecDefault {

  /** RFC 4231 Test Case 1 key: 20 bytes of 0x0b. */
  private val TestKey: Array[Byte] =
    Array.fill(20)(0x0b.toByte)

  /** RFC 4231 Test Case 1 message: "Hi There". */
  private val TestMessage: Chunk[Byte] =
    Chunk.fromArray("Hi There".getBytes(StandardCharsets.US_ASCII))

  /**
   * RFC 4231 Test Case 1 HMAC-SHA256, lowercase hex. Independently verified
   * with `printf 'Hi There' | openssl dgst -sha256 -mac HMAC -macopt
   * hexkey:0b...0b` (20 x 0x0b): b0344c61...9376c2e32cff7.
   */
  private val ExpectedMacHex: String =
    "b0344c61d8db38535ca8afceaf0bf12b881dc200c9833da726e9376c2e32cff7"

  /**
   * Raw-byte HMAC-SHA256 tap: hash `bytes` exactly as received, no decoding.
   */
  private def hmacSha256Hex(key: Array[Byte], bytes: Chunk[Byte]): String = {
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(new SecretKeySpec(key, "HmacSHA256"))
    val out = new StringBuilder(64)
    val sum = mac.doFinal(bytes.toArray)
    var i   = 0
    while (i < sum.length) {
      out.append(Character.forDigit((sum(i) >> 4) & 0xf, 16))
      out.append(Character.forDigit(sum(i) & 0xf, 16))
      i += 1
    }
    out.toString
  }

  private val HmacRoutes: Routes[Any] =
    Routes(
      Route(
        RoutePattern(Method.POST, "/hmac"),
        handler { (req: Request) =>
          // v4 preserves bytes: tap the raw chunk BEFORE any decoding.
          // Verification/dedup/reconciliation belong to Qaizn, not here.
          responseAsResult(
            Response(status = Status.Ok, body = Body.fromString(hmacSha256Hex(TestKey, req.body.toChunk))),
          )
        },
      ),
      Route(
        RoutePattern(Method.POST, "/echo"),
        handler { (req: Request) =>
          responseAsResult(Response(status = Status.Ok, body = req.body))
        },
      ),
    )

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("H2RawBodySpec")(
      test("chunk request body (single DATA frame) HMAC matches the fixed test vector") {
        withServer { port =>
          ZIO.attemptBlocking {
            val client = new RawH2Client(port)
            try {
              val mac  = client.post("/hmac", TestMessage, streamId = 1)
              val echo = client.post("/echo", TestMessage, streamId = 3)
              assertTrue(
                mac.status == 200,
                mac.bodyText == ExpectedMacHex,
                echo.status == 200,
                echo.body == TestMessage,
              )
            } finally client.close()
          }
        }
      },
      test("streamed request body (split DATA frames) HMAC matches the same vector, byte-identical") {
        withServer { port =>
          ZIO.attemptBlocking {
            val client = new RawH2Client(port)
            try {
              val first  = TestMessage.slice(0, 4)
              val second = TestMessage.slice(4, TestMessage.length)
              val mac    = client.postSplit("/hmac", first, second, streamId = 1)
              val echo   = client.postSplit("/echo", first, second, streamId = 3)
              assertTrue(
                mac.status == 200,
                mac.bodyText == ExpectedMacHex,
                echo.status == 200,
                echo.body == TestMessage,
              )
            } finally client.close()
          }
        }
      },
      test("one flipped bit changes the MAC: the tap is byte-sensitive, not a constant") {
        withServer { port =>
          ZIO.attemptBlocking {
            val client = new RawH2Client(port)
            try {
              val tamperedArray = "Hi There".getBytes(StandardCharsets.US_ASCII)
              tamperedArray(2) = (tamperedArray(2) ^ 0x01).toByte
              val tampered      = Chunk.fromArray(tamperedArray)
              val mac           = client.post("/hmac", tampered, streamId = 1)
              val echo          = client.post("/echo", tampered, streamId = 3)
              assertTrue(
                mac.status == 200,
                mac.bodyText != ExpectedMacHex,
                mac.bodyText.length == 64,
                echo.status == 200,
                echo.body == tampered,
              )
            } finally client.close()
          }
        }
      },
      test("chunk body toChunk round-trips and HMACs without a server") {
        ZIO.succeed {
          val chunkBody = Body.fromChunk(TestMessage)
          assertTrue(
            chunkBody.toChunk == TestMessage,
            hmacSha256Hex(TestKey, chunkBody.toChunk) == ExpectedMacHex,
          )
        }
      },
    ) @@ sequential

  private def withServer[R](
    use: Int => ZIO[R, Throwable, TestResult],
  ): ZIO[R & Scope, Throwable, TestResult] =
    ZIO
      .acquireRelease(
        ZIO.attempt {
          val connector = Connector(bind = BindAddress.localhost(0))
          new LoomServer(connector).serve(HmacRoutes, Context.empty)
        },
      )(handle => ZIO.succeed(handle.shutdownAndWait()))
      .flatMap { handle =>
        val port = handle.bindings.head.address match {
          case BoundAddress.Tcp(_, value) => value
          case other                      => throw new AssertionError("Expected TCP binding but found: " + other)
        }
        use(port)
      }

  /** POST sends on top of the shared raw client (single + split DATA). */
  private implicit final class RawBodyOps(val client: RawH2Client) {
    private def encoder: HpackEncoder = new HpackEncoder()

    /** POST whose body fits in one DATA frame. */
    def post(path: String, body: Chunk[Byte], streamId: Int): H2RawClientFixture.RawResponse = {
      client.sendFrame(makeHeaders("POST", path, streamId, endStream = body.isEmpty))
      if (body.nonEmpty) client.sendFrame(Data(streamId, body, endStream = true))
      client.awaitResponse(streamId)
    }

    /** POST split across two DATA frames (streaming delivery). */
    def postSplit(
      path: String,
      first: Chunk[Byte],
      second: Chunk[Byte],
      streamId: Int,
    ): H2RawClientFixture.RawResponse = {
      client.sendFrame(makeHeaders("POST", path, streamId, endStream = false))
      client.sendFrame(Data(streamId, first, endStream = false))
      client.sendFrame(Data(streamId, second, endStream = true))
      client.awaitResponse(streamId)
    }

    private def makeHeaders(method: String, path: String, streamId: Int, endStream: Boolean): H2Frame.Headers = {
      val pseudo = List(
        HeaderField(":method", method),
        HeaderField(":path", path),
        HeaderField(":scheme", "http"),
        HeaderField(":authority", s"127.0.0.1:${client.port}"),
      )
      H2Frame.Headers(
        streamId = streamId,
        headerBlock = encoder.encode(pseudo),
        endStream = endStream,
        endHeaders = true,
      )
    }
  }
}
