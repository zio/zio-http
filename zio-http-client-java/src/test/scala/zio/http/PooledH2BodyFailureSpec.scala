package zio.http

import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

import zio.blocks.chunk.Chunk
import zio.test.TestAspect.sequential
import zio.test._

import zio.http.h2.FrameCodec
import zio.http.h2.H2Error
import zio.http.h2.H2Frame
import zio.http.h2.H2Frame._
import zio.http.h2.hpack.HeaderField
import zio.http.h2.hpack.HpackCodec

/**
 * A pooled response body that dies mid-transfer must fail loudly, never come
 * back empty.
 *
 * The pooled leg streams bodies lazily through `Body.toArray`, which maps a
 * mid-pull `java.io.IOException` to an empty body
 * (`runCollect.getOrElse(Chunk.empty)` in the external blocks model). A server
 * that answers HEADERS, emits part of the body, then dies (FIN close here, no
 * END_STREAM ever) must therefore surface a failure out of the drain: the old
 * code returned an empty array and the caller could not tell a dead server from
 * an empty response.
 *
 * Plain JVM `try`/`finally` brackets only (no ZIO effects or DI): the mid-body
 * close is deterministic — the stub always closes before END_STREAM could exist
 * — so no sleeps or retries are needed.
 */
object PooledH2BodyFailureSpec extends ZIOSpecDefault {

  private val FirstChunk: Array[Byte] = Array.fill(16384)(0x42.toByte)

  def spec = suite("PooledH2BodyFailureSpec")(
    test("mid-body server close surfaces a failure instead of an empty body") {
      withDyingStubServer { port =>
        val pool = PooledLoomH2Client(ClientConfig())
        try {
          val response       = pool.send(Request.get(absUrl(s"http://127.0.0.1:$port/die")))
          val statusOk       = response.status == Status.Ok
          var error          = Option.empty[Throwable]
          var body           = Array.empty[Byte]
          // Eager drain: the pooled body streams lazily, so materialize it
          // here, inside the body, while the (dying) stub interaction is
          // still the live subject — never inside assertTrue, whose arrows
          // evaluate after the test's own teardown.
          try body = response.body.toArray
          catch { case failure: Throwable => error = Some(failure) }
          val transportCause = error.flatMap(unwindIOException)
          assertTrue(statusOk, error.isDefined, transportCause.isDefined)
        } finally pool.close()
      }
    },
    test("the body cap still trips as ResponseBodyTooLarge, not a transport failure") {
      withDyingStubServer { port =>
        val pool = PooledLoomH2Client(ClientConfig(maxResponseBodySize = 4096L))
        try {
          val response = pool.send(Request.get(absUrl(s"http://127.0.0.1:$port/big")))
          var error    = Option.empty[Throwable]
          try response.body.toArray
          catch { case failure: Throwable => error = Some(failure) }
          assertTrue(error.exists(_.isInstanceOf[ResponseBodyTooLarge]))
        } finally pool.close()
      }
    },
  ) @@ sequential

  /** Walks the cause chain for a transport `IOException`. */
  private def unwindIOException(failure: Throwable): Option[IOException] = {
    var current: Throwable         = failure
    var found: Option[IOException] = None
    while (current != null && found.isEmpty) {
      current match {
        case io: IOException => found = Some(io)
        case _               => current = current.getCause
      }
    }
    found
  }

  private def absUrl(raw: String): URL =
    URL.parse(raw).fold(err => throw new IllegalArgumentException("Invalid test URL: " + err), identity)

  /**
   * Minimal H2C stub: handshake, one request HEADERS, one 200 response HEADERS
   * (stream left open), one 16 KiB DATA frame (no END_STREAM), then a clean
   * socket close. The client therefore always observes HEADERS plus a mid-body
   * EOF — deterministically, whatever its pacing.
   */
  private def withDyingStubServer(use: Int => TestResult): TestResult = {
    val serverSocket = new ServerSocket()
    serverSocket.bind(new InetSocketAddress("127.0.0.1", 0))
    val port         = serverSocket.getLocalPort
    val done         = new CountDownLatch(1)
    val acceptor     = Thread
      .ofVirtual()
      .name("dying-stub-acceptor")
      .start(() => {
        try {
          val socket = serverSocket.accept()
          try serveOne(socket)
          finally closeQuietly(socket)
        } catch { case _: Throwable => () }
        finally done.countDown()
      })
    try use(port)
    finally {
      closeQuietly(serverSocket)
      done.await(10L, TimeUnit.SECONDS)
      ()
    }
  }

  private def serveOne(socket: Socket): Unit = {
    socket.setSoTimeout(20000)
    val input      = socket.getInputStream
    val output     = socket.getOutputStream
    val reader     = new StubFrameReader(input)
    readPreface(input)
    var negotiated = false
    while (!negotiated) {
      reader.readFrame() match {
        case Settings(false, _) => negotiated = true
        case _                  => ()
      }
    }
    writeFrame(output, Settings(ack = false, Nil))
    writeFrame(output, Settings(ack = true, Nil))
    output.flush()
    val hpack      = new HpackCodec()
    var open       = true
    while (open) {
      reader.readFrame() match {
        case Headers(streamId, block, endStream, endHeaders, _, _) =>
          val headerBlock =
            if (endHeaders) block
            else block ++ readContinuations(reader, output, streamId)
          val fields      = hpack.decode(headerBlock).fold(err => throw new IOException("stub HPACK: " + err), identity)
          val path        = fields.collectFirst { case HeaderField(":path", value, _) => value }.getOrElse("/")
          if (endStream) {
            // 200 HEADERS with the stream left open, then per-path DATA.
            val responseBlock = hpack.encode(
              List(
                HeaderField(":status", "200"),
                HeaderField("content-type", "application/octet-stream"),
              ),
            )
            writeFrame(output, Headers(streamId, responseBlock, endStream = false, endHeaders = true))
            path match {
              case "/die" =>
                // One DATA frame without END_STREAM, then die immediately:
                // the client observes HEADERS plus a mid-body transport
                // failure (its first per-DATA WINDOW_UPDATE hits the closed
                // socket). No interlock, no sleep: the missing END_STREAM
                // alone makes the failure deterministic whatever the pacing.
                writeFrame(output, Data(streamId, Chunk.fromArray(FirstChunk), endStream = false))
                output.flush()
                open = false
              case _      =>
                // Past-the-cap DATA while staying alive: the client must trip
                // the body cap (16 KiB + 16 KiB against a 4 KiB cap) before
                // any transport failure can occur. Waits for the client's
                // WINDOW_UPDATE so the close below cannot race the drain.
                writeFrame(output, Data(streamId, Chunk.fromArray(FirstChunk), endStream = false))
                writeFrame(output, Data(streamId, Chunk.fromArray(FirstChunk), endStream = false))
                output.flush()
                var waiting = true
                while (waiting) {
                  reader.readFrame() match {
                    case WindowUpdate(_, _) => waiting = false
                    case Ping(false, data)  =>
                      writeFrame(output, Ping(ack = true, data))
                      output.flush()
                    case _                  => ()
                  }
                }
                open = false
            }
          }
        case WindowUpdate(0, _)                                    => ()
        case WindowUpdate(_, _)                                    => ()
        case Ping(false, data)                                     =>
          writeFrame(output, Ping(ack = true, data))
          output.flush()
        case Settings(false, _)                                    =>
          writeFrame(output, Settings(ack = true, Nil))
          output.flush()
        case _                                                     => ()
      }
    }
  }

  private def readContinuations(reader: StubFrameReader, output: OutputStream, streamId: Int): Chunk[Byte] = {
    var collected = Chunk.empty[Byte]
    var done      = false
    while (!done) {
      reader.readFrame() match {
        case Continuation(`streamId`, block, endHeaders) =>
          collected = collected ++ block
          if (endHeaders) done = true
        case Ping(false, data)                           =>
          writeFrame(output, Ping(ack = true, data))
          output.flush()
        case _                                           => ()
      }
    }
    collected
  }

  private def readPreface(input: InputStream): Unit = {
    val want = "PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n".getBytes(StandardCharsets.US_ASCII)
    val seen = new Array[Byte](want.length)
    var off  = 0
    while (off < seen.length) {
      val read = input.read(seen, off, seen.length - off)
      if (read < 0) throw new EOFException("stub: EOF in preface")
      off += read
    }
    if (!java.util.Arrays.equals(seen, want)) throw new IOException("stub: bad preface")
  }

  private def writeFrame(output: OutputStream, frame: H2Frame): Unit = {
    val bytes = FrameCodec.encode(frame).toArray
    output.write(bytes, 0, bytes.length)
  }

  private def closeQuietly(socket: Socket): Unit =
    if (socket != null) {
      try socket.close()
      catch { case _: Throwable => () }
    }

  private def closeQuietly(socket: ServerSocket): Unit =
    if (socket != null) {
      try socket.close()
      catch { case _: Throwable => () }
    }

  private final class StubFrameReader(input: InputStream) {
    private var buffer = Chunk.empty[Byte]

    def readFrame(): H2Frame = {
      var result: H2Frame = null
      while (result == null) {
        FrameCodec.decode(buffer) match {
          case Right((decoded, rest))         =>
            buffer = rest
            result = decoded
          case Left(H2Error.InsufficientData) =>
            val chunk = new Array[Byte](8192)
            val read  = input.read(chunk)
            if (read < 0) throw new EOFException("stub: EOF mid-frame")
            buffer = buffer ++ Chunk.fromArray(java.util.Arrays.copyOf(chunk, read))
          case Left(error)                    =>
            throw new IOException("stub: bad frame: " + error)
        }
      }
      result
    }
  }
}
