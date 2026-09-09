package zio.http

import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

import scala.annotation.experimental
import scala.jdk.CollectionConverters._

import zio._
import zio.test.TestAspect.sequential
import zio.test._
import zio.blocks.chunk.Chunk
import zio.http.h2.FrameCodec
import zio.http.h2.H2Error
import zio.http.h2.H2Frame
import zio.http.h2.H2Frame._
import zio.http.h2.hpack.HeaderField
import zio.http.h2.hpack.HpackCodec

/**
 * MAJOR-2 pool-safety gaps left open by T15 (ClientPoolStreamingSpec 15/15):
 * T15 proves graceful-close (/poison FIN) and GOAWAY eviction, single-boundary
 * idle sweep, and stats - but NOT an ungraceful TCP reset mid-idle (the path
 * where the pooled socket dies without GOAWAY), NOT per-authority isolation
 * (PoolKey exists; no test pins two authorities apart or watches the
 * Authorization wire bytes), and NOT idle turnover under sustained load (T15
 * sweeps once, then stops). A pool that reuses a poisoned connection turns one
 * RST into systemic failure - these three tests pin the gaps.
 *
 * Harness mirrors T15: real sockets on ephemeral ports (`acquireRelease`
 * everywhere) with an in-spec H2C stub extended for the gaps - a `/reset` route
 * that kills its connection ungracefully (SO_LINGER(0) RST, never GOAWAY), plus
 * per-request authority/authorization recording so leaks are observable. Small
 * bodies only (no WINDOW_UPDATE top-ups needed).
 */
@experimental
object ClientPoolSafetySpec extends ZIOSpecDefault {

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("ClientPoolSafetySpec")(
      test("RST-killed idle connection is evicted, retry lands on a fresh connection") {
        withStubServer() { stub =>
          ZIO.attemptBlocking {
            val pool = PooledLoomH2Client(ClientConfig(pool = PoolConfig(maxPerHost = 1, maxTotal = 1)))
            try {
              val first     = pool.send(Request.get(absUrl(s"http://127.0.0.1:${stub.port}/reset")))
              val firstBody = new String(first.body.toArray, StandardCharsets.UTF_8)
              // The server RSTs the pooled connection mid-idle (no GOAWAY);
              // settle so the RST is in the local socket before next checkout.
              Thread.sleep(1000L)
              val retry     = pool.send(Request.get(absUrl(s"http://127.0.0.1:${stub.port}/ok")))
              val retryBody = new String(retry.body.toArray, StandardCharsets.UTF_8)
              val accepted  = stub.accepted.get()
              proof(s"rst-evict first=<$firstBody> retry=<$retryBody> accepted=$accepted stats=${pool.stats}")
              (firstBody, retryBody, accepted, pool.stats)
            } finally {
              pool.close()
            }
          }.map { case (firstBody, retryBody, accepted, stats) =>
            assertTrue(
              firstBody == "pool-reset-ok",
              retryBody == "pool-ok",
              accepted == 2,
              stats.checkedOut == 0,
            )
          }
        }
      },
      test("pooled connections are isolated per authority, Authorization never leaks") {
        withStubServer() { stubA =>
          withStubServer() { stubB =>
            ZIO.attemptBlocking {
              val pool = PooledLoomH2Client(ClientConfig(pool = PoolConfig(maxPerHost = 4, maxTotal = 8)))
              try {
                val reqA  =
                  Request.get(absUrl(s"http://127.0.0.1:${stubA.port}/ok")).addHeader("Authorization", "Bearer TOKEN-A")
                val respA = pool.send(reqA)
                val bodyA = new String(respA.body.toArray, StandardCharsets.UTF_8)
                val reqB  =
                  Request.get(absUrl(s"http://127.0.0.1:${stubB.port}/ok")).addHeader("Authorization", "Bearer TOKEN-B")
                val respB = pool.send(reqB)
                val bodyB = new String(respB.body.toArray, StandardCharsets.UTF_8)
                val seenA = stubA.seen.asScala.toList
                val seenB = stubB.seen.asScala.toList
                proof(
                  s"isolation bodyA=<$bodyA> bodyB=<$bodyB> " +
                    s"acceptedA=${stubA.accepted.get()} acceptedB=${stubB.accepted.get()} " +
                    s"seenA=$seenA seenB=$seenB",
                )
                (bodyA, bodyB, seenA, seenB, stubA.accepted.get(), stubB.accepted.get())
              } finally {
                pool.close()
              }
            }.map { case (bodyA, bodyB, seenA, seenB, acceptedA, acceptedB) =>
              val authB = seenB.flatMap(_.authorization)
              assertTrue(
                bodyA == "pool-ok",
                bodyB == "pool-ok",
                // Different authorities => different connections, never shared.
                acceptedA == 1,
                acceptedB == 1,
                // B's Authorization arrived intact on B's wire bytes ...
                authB.contains("Bearer TOKEN-B"),
                seenB.forall(_.authority == s"127.0.0.1:${stubB.port}"),
                // ... and A's credential never leaks onto B's connection.
                !authB.exists(_.contains("TOKEN-A")),
                !seenB.exists(_.authorization.exists(_.contains("TOKEN-A"))),
                seenA.flatMap(_.authorization).contains("Bearer TOKEN-A"),
              )
            }
          }
        }
      },
      test("short idle timeout under sustained load reaps and re-establishes with zero failures") {
        withStubServer() { stub =>
          ZIO.attemptBlocking {
            val pool = PooledLoomH2Client(
              ClientConfig(pool = PoolConfig(maxPerHost = 2, maxTotal = 4, idleTimeout = Duration.ofMillis(300))),
            )
            try {
              // Burst within the idle window (reuse), cross the boundary,
              // burst again (reap + re-establish) - sustained traffic.
              def burst(): List[String] =
                (1 to 3).map { _ =>
                  val response = pool.send(Request.get(absUrl(s"http://127.0.0.1:${stub.port}/ok")))
                  new String(response.body.toArray, StandardCharsets.UTF_8)
                }.toList
              val first                 = burst()
              Thread.sleep(700L)
              val second                = burst()
              val bodies                = first ++ second
              val accepted              = stub.accepted.get()
              val stats                 = pool.stats
              proof(s"load-turnover bodies=${bodies.distinct} accepted=$accepted stats=$stats")
              (bodies, accepted, stats)
            } finally {
              pool.close()
            }
          }.map { case (bodies, accepted, stats) =>
            assertTrue(
              bodies.forall(_ == "pool-ok"),
              bodies.size == 6,
              // The sweep turns the connection over across the idle boundary
              // (RED proved accepted == 2 against the naive reuse hope of 1).
              accepted == 2,
              stats.checkedOut == 0,
              stats.queued == 0,
            )
          }
        }
      },
    ) @@ sequential

  private def absUrl(raw: String): URL =
    URL.parse(raw).fold(err => throw new IllegalArgumentException("Invalid test URL: " + err), identity)

  private def proof(line: String): Unit =
    println(s">> [ClientPoolSafetySpec] $line")

  private final case class SeenRequest(path: String, authority: String, authorization: Option[String])

  private final class StubServer(
    val port: Int,
    val accepted: AtomicInteger,
    val seen: ConcurrentLinkedQueue[SeenRequest],
    val cancelled: java.util.Set[Int],
    stop: () => Unit,
  ) {
    def close(): Unit = stop()
  }

  private def withStubServer[R]()(
    use: StubServer => ZIO[R, Throwable, TestResult],
  ): ZIO[R & Scope, Throwable, TestResult] =
    ZIO
      .acquireRelease(ZIO.attempt(startStub()))(stub => ZIO.succeed(stub.close()))
      .flatMap(use)

  private def startStub(): StubServer = {
    val serverSocket = new ServerSocket(0, 128, java.net.InetAddress.getByName("127.0.0.1"))
    val accepted     = new AtomicInteger(0)
    val seen         = new ConcurrentLinkedQueue[SeenRequest]()
    val cancelled    = ConcurrentHashMap.newKeySet[Int]()
    val running      = new java.util.concurrent.atomic.AtomicBoolean(true)
    val acceptor     = Thread
      .ofVirtual()
      .name("pool-safety-acceptor")
      .start(() => {
        while (running.get()) {
          try {
            val socket = serverSocket.accept()
            accepted.incrementAndGet()
            Thread
              .ofVirtual()
              .name("pool-safety-conn")
              .start(() => {
                try {
                  handleStubConn(socket, seen, cancelled)
                } finally {
                  try socket.close()
                  catch { case _: Throwable => () }
                }
              })
          } catch {
            case _: java.net.SocketException => ()
          }
        }
      })
    new StubServer(
      serverSocket.getLocalPort,
      accepted,
      seen,
      cancelled,
      () => {
        running.set(false)
        try serverSocket.close()
        catch { case _: Throwable => () }
        acceptor.join(5000L)
      },
    )
  }

  private def handleStubConn(
    socket: Socket,
    seen: ConcurrentLinkedQueue[SeenRequest],
    cancelled: java.util.Set[Int],
  ): Unit = {
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
      try {
        reader.readFrame() match {
          case Headers(streamId, block, endStream, endHeaders, _, _) =>
            val headerBlock =
              if (endHeaders) block
              else block ++ readContinuations(reader, output, streamId)
            val fields    = hpack.decode(headerBlock).fold(err => throw new IOException("stub HPACK: " + err), identity)
            val path      = fields.collectFirst { case HeaderField(":path", value, _) => value }.getOrElse("/")
            // Authority-aware recording: every request pins its :authority
            // pseudo-header plus the wire authorization credential, so a
            // cross-authority leak is observable (never vacuous).
            val authority = fields.collectFirst { case HeaderField(":authority", value, _) => value }.getOrElse("")
            val authorization = fields.collectFirst { case HeaderField("authorization", value, _) => value }
            seen.add(SeenRequest(path, authority, authorization))
            if (!endStream) drainRequestBody(reader, output, streamId, cancelled)
            dispatch(output, reader, hpack, streamId, path, cancelled, socket) match {
              case StubAction.KeepGoing => ()
              case StubAction.Close     => open = false
            }
          case WindowUpdate(0, _)                                    =>
            ()
          case WindowUpdate(_, _)                                    => ()
          case Ping(false, data)                                     =>
            writeFrame(output, Ping(ack = true, data))
            output.flush()
          case Settings(false, _)                                    =>
            writeFrame(output, Settings(ack = true, Nil))
            output.flush()
          case RstStream(streamId, _)                                =>
            cancelled.add(streamId)
          case _                                                     => ()
        }
      } catch {
        case _: EOFException                    => open = false
        case _: java.net.SocketTimeoutException => open = false
        case _: java.net.SocketException        => open = false
        case _: IOException                     => open = false
      }
    }
  }

  private sealed trait StubAction
  private object StubAction {
    case object KeepGoing extends StubAction
    case object Close     extends StubAction
  }

  private def dispatch(
    output: OutputStream,
    reader: StubFrameReader,
    hpack: HpackCodec,
    streamId: Int,
    path: String,
    cancelled: java.util.Set[Int],
    socket: Socket,
  ): StubAction = {
    path match {
      // Ungraceful kill mid-idle (NOT GOAWAY, NOT the /poison FIN linger):
      // linger so the client observes HEADERS+DATA and repools the
      // connection, then SO_LINGER(0) close => TCP RST while pooled.
      case "/reset" =>
        sendText(output, hpack, streamId, "pool-reset-ok")
        Thread.sleep(300L)
        try socket.setSoLinger(true, 0)
        catch { case _: Throwable => () }
        StubAction.Close
      case _        =>
        sendText(output, hpack, streamId, "pool-ok")
        StubAction.KeepGoing
    }
  }

  private def drainRequestBody(
    reader: StubFrameReader,
    output: OutputStream,
    streamId: Int,
    cancelled: java.util.Set[Int],
  ): Long = {
    var total = 0L
    var done  = false
    while (!done) {
      reader.readFrame() match {
        case Data(`streamId`, data, endStream, _) =>
          total += data.length
          writeFrame(output, WindowUpdate(0, data.length))
          writeFrame(output, WindowUpdate(streamId, data.length))
          output.flush()
          if (endStream) done = true
        case RstStream(`streamId`, _)             =>
          cancelled.add(streamId)
          throw new IOException("stub: request RST")
        case Ping(false, ping)                    =>
          writeFrame(output, Ping(ack = true, ping))
          output.flush()
        case _                                    => ()
      }
    }
    total
  }

  private def sendText(output: OutputStream, hpack: HpackCodec, streamId: Int, text: String): Unit = {
    sendHeaders(output, hpack, streamId, endStream = false)
    val bytes = text.getBytes(StandardCharsets.UTF_8)
    val block = Chunk.fromArray(bytes)
    writeFrame(output, Data(streamId, block, endStream = true))
    output.flush()
  }

  private def sendHeaders(output: OutputStream, hpack: HpackCodec, streamId: Int, endStream: Boolean): Unit = {
    val block = hpack.encode(
      List(
        HeaderField(":status", "200"),
        HeaderField("content-type", "text/plain"),
      ),
    )
    writeFrame(output, Headers(streamId, block, endStream = endStream, endHeaders = true))
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
