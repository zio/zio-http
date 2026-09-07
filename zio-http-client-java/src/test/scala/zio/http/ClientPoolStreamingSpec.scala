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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

import scala.annotation.experimental

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
 * Todo 15: connection pooling + cancellation + client-side streaming over the
 * T14 [[LoomH2ClientDriver]] framing.
 *
 * Harnesses are real sockets on ephemeral ports (`acquireRelease` everywhere):
 * a minimal in-spec H2C stub (prior-knowledge, sequential streams per
 * connection) plays server so every pool mechanic is observable:
 * accepted-connection counts prove reuse/reclamation/eviction, a concurrent
 * gauge proves caps, recorded RST_STREAM ids prove cancellation, and an 8 MiB
 * `/big` route proves heap-bounded streaming.
 */
@experimental
object ClientPoolStreamingSpec extends ZIOSpecDefault {

  private val BigBodyBytes: Int   = 8 * 1024 * 1024
  private val HeapBoundBytes: Long = 4L * 1024L * 1024L

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("ClientPoolStreamingSpec")(
      test("N sequential requests over one pool entry reuse a single connection") {
        withStubServer(StubBehavior()) { stub =>
          ZIO.attemptBlocking {
            val pool = PooledLoomH2Client(ClientConfig(pool = PoolConfig(maxPerHost = 4)))
            try {
              val bodies = (1 to 5).map { _ =>
                val response = pool.send(Request.get(absUrl(s"http://127.0.0.1:${stub.port}/ok")))
                if (response.status != Status.Ok) throw new AssertionError("status: " + response.status)
                new String(response.body.toArray, StandardCharsets.UTF_8)
              }
              val accepted = stub.accepted.get()
              proof(s"reuse sequential=5 accepted=$accepted bodies=${bodies.distinct}")
              (bodies, accepted)
            } finally {
              pool.close()
            }
          }.map { case (bodies, accepted) =>
            assertTrue(bodies.forall(_ == "pool-ok"), accepted == 1)
          }
        }
      },
      test("pool honors maxPerHost under concurrency") {
        withStubServer(StubBehavior(slowMs = 400)) { stub =>
          ZIO.attemptBlocking {
            val pool = PooledLoomH2Client(ClientConfig(pool = PoolConfig(maxPerHost = 2, maxTotal = 8)))
            try {
              val results = runConcurrent(4) {
                // Pool slots stay checked out until the lazy body is consumed:
                // always consume (or cancel) - an abandoned body pins its slot.
                val response = pool.send(Request.get(absUrl(s"http://127.0.0.1:${stub.port}/slow")))
                val body     = new String(response.body.toArray, StandardCharsets.UTF_8)
                response.status == Status.Ok && body == "pool-slow-ok"
              }
              val maxSeen = stub.maxConcurrent.get()
              proof(s"caps maxPerHost=2 maxConcurrentSeen=$maxSeen allOk=${results.forall(identity)}")
              (results, maxSeen)
            } finally {
              pool.close()
            }
          }.map { case (results, maxSeen) =>
            assertTrue(results.forall(identity), maxSeen <= 2)
          }
        }
      },
      test("idle connections past idleTimeout are reclaimed") {
        withStubServer(StubBehavior()) { stub =>
          ZIO.attemptBlocking {
            val pool = PooledLoomH2Client(
              ClientConfig(pool = PoolConfig(maxPerHost = 2, idleTimeout = Duration.ofMillis(300))),
            )
            try {
              pool.send(Request.get(absUrl(s"http://127.0.0.1:${stub.port}/ok")))
              val first = stub.accepted.get()
              Thread.sleep(800L)
              pool.send(Request.get(absUrl(s"http://127.0.0.1:${stub.port}/ok")))
              val second = stub.accepted.get()
              proof(s"idle-sweep first=$first second=$second")
              (first, second)
            } finally {
              pool.close()
            }
          }.map { case (first, second) =>
            assertTrue(first == 1, second == 2)
          }
        }
      },
      test("queue-full fails fast with a clear pool-exhausted error, never hangs") {
        withStubServer(StubBehavior(slowMs = 3000)) { stub =>
          ZIO.attemptBlocking {
            val pool = PooledLoomH2Client(
              ClientConfig(pool = PoolConfig(maxPerHost = 1, maxTotal = 1, queueSize = 0)),
            )
            try {
              val gate   = new CountDownLatch(1)
              val holder = Thread.ofVirtual().name("pool-exhaust-holder").start(() => {
                gate.countDown()
                pool.send(Request.get(absUrl(s"http://127.0.0.1:${stub.port}/slow")))
              })
              gate.await(5L, TimeUnit.SECONDS)
              Thread.sleep(300L)
              val start = java.lang.System.currentTimeMillis()
              var error = Option.empty[Throwable]
              try {
                pool.send(Request.get(absUrl(s"http://127.0.0.1:${stub.port}/ok")))
              } catch {
                case failure: Throwable => error = Some(failure)
              }
              val elapsed = java.lang.System.currentTimeMillis() - start
              holder.interrupt()
              holder.join(8000L)
              proof(s"exhausted error=${error.map(_.getMessage)} elapsedMs=$elapsed")
              (error, elapsed)
            } finally {
              pool.close()
            }
          }.map { case (error, elapsed) =>
            assertTrue(
              error.exists(e => e.isInstanceOf[IOException] && e.getMessage.contains("pool")),
              elapsed < 2500L,
            )
          }
        }
      },
      test("pool exhaustion with a queue waits a bounded time then fails clearly") {
        withStubServer(StubBehavior(slowMs = 5000)) { stub =>
          ZIO.attemptBlocking {
            val pool = PooledLoomH2Client(
              ClientConfig(
                connectTimeout = Duration.ofMillis(800),
                pool = PoolConfig(maxPerHost = 1, maxTotal = 1, queueSize = 1),
              ),
            )
            try {
              val gate   = new CountDownLatch(1)
              val holder = Thread.ofVirtual().name("pool-wait-holder").start(() => {
                gate.countDown()
                try {
                  pool.send(Request.get(absUrl(s"http://127.0.0.1:${stub.port}/slow")))
                } catch {
                  case _: Throwable => ()
                }
              })
              gate.await(5L, TimeUnit.SECONDS)
              Thread.sleep(300L)
              val start = java.lang.System.currentTimeMillis()
              var error = Option.empty[Throwable]
              try {
                pool.send(Request.get(absUrl(s"http://127.0.0.1:${stub.port}/ok")))
              } catch {
                case failure: Throwable => error = Some(failure)
              }
              val elapsed = java.lang.System.currentTimeMillis() - start
              holder.interrupt()
              holder.join(8000L)
              proof(s"bounded-wait error=${error.map(e => e.getClass.getSimpleName + ": " + e.getMessage)} elapsedMs=$elapsed")
              (error, elapsed)
            } finally {
              pool.close()
            }
          }.map { case (error, elapsed) =>
            assertTrue(
              error.exists(_.isInstanceOf[TimeoutException]),
              elapsed < 8000L,
            )
          }
        }
      },
      test("cancel while awaiting headers ends the waiter and releases the slot") {
        withStubServer(StubBehavior(slowMs = 5000)) { stub =>
          ZIO.attemptBlocking {
            val pool = PooledLoomH2Client(ClientConfig(pool = PoolConfig(maxPerHost = 1, maxTotal = 1)))
            try {
              val exchange = pool.sendCancellable(Request.get(absUrl(s"http://127.0.0.1:${stub.port}/slow")))
              val outcome  = new AtomicReference[Either[Throwable, Response]]()
              val waiter   = Thread.ofVirtual().name("pool-cancel-await").start(() => {
                try {
                  outcome.set(Right(exchange.await()))
                } catch {
                  case failure: Throwable => outcome.set(Left(failure))
                }
              })
              Thread.sleep(400L)
              exchange.cancel()
              waiter.join(8000L)
              val stats = pool.stats
              val ended = !waiter.isAlive
              val wasCancelled = outcome.get() match {
                case Left(_: java.util.concurrent.CancellationException) => true
                case _                                                   => false
              }
              proof(s"cancel-headers outcome=${outcome.get()} cancelled=$wasCancelled threadEnded=$ended stats=$stats")
              // A cancelled (socket-closed) connection is evicted, never repooled:
              // the next request must transparently open a fresh connection.
              val retry   = pool.send(Request.get(absUrl(s"http://127.0.0.1:${stub.port}/ok")))
              val retry2  = new String(retry.body.toArray, StandardCharsets.UTF_8)
              (wasCancelled, ended, stats, retry2, stub.accepted.get())
            } finally {
              pool.close()
            }
          }.map { case (wasCancelled, ended, stats, retry2, accepted) =>
            assertTrue(
              wasCancelled,
              ended,
              stats.checkedOut == 0,
              retry2 == "pool-ok",
              accepted == 2,
            )
          }
        }
      },
      test("cancel during body delivers RST the server observes, with no slot leak") {
        withStubServer(StubBehavior()) { stub =>
          ZIO.attemptBlocking {
            val pool = PooledLoomH2Client(ClientConfig(pool = PoolConfig(maxPerHost = 1, maxTotal = 1)))
            try {
              val exchange = pool.sendCancellable(Request.get(absUrl(s"http://127.0.0.1:${stub.port}/gated-body")))
              val response = exchange.await()
              if (response.status != Status.Ok) throw new AssertionError("headers: " + response.status)
              val outcome  = new AtomicReference[Either[Throwable, Array[Byte]]]()
              val consumer = Thread.ofVirtual().name("pool-cancel-consume").start(() => {
                try {
                  outcome.set(Right(response.body.toArray))
                } catch {
                  case failure: Throwable => outcome.set(Left(failure))
                }
              })
              Thread.sleep(400L)
              exchange.cancel()
              consumer.join(8000L)
              // The consumer's failure comes from the local close; the server
              // needs its own scheduling quantum to observe the RST - poll it.
              waitUntil("server-rst", 5000L) { stub.cancelled.contains(1) }
              val stats     = pool.stats
              val rstSeen   = stub.cancelled.contains(1)
              val ended     = !consumer.isAlive
              val wasCancelled = outcome.get() match {
                case Left(_: java.util.concurrent.CancellationException) => true
                case _                                                   => false
              }
              proof(s"cancel-body outcome=${outcome.get().map(_.length)} rstSeen=$rstSeen threadEnded=$ended stats=$stats")
              val retry     = pool.send(Request.get(absUrl(s"http://127.0.0.1:${stub.port}/ok")))
              val retryBody = new String(retry.body.toArray, StandardCharsets.UTF_8)
              (wasCancelled, rstSeen, ended, stats, retryBody, stub.accepted.get())
            } finally {
              pool.close()
            }
          }.map { case (wasCancelled, rstSeen, ended, stats, retryBody, accepted) =>
            assertTrue(
              wasCancelled,
              rstSeen,
              ended,
              stats.checkedOut == 0,
              retryBody == "pool-ok",
              accepted == 2,
            )
          }
        }
      },
      test("large response bodies stream with bounded heap and no full buffering") {
        withStubServer(StubBehavior()) { stub =>
          ZIO.attemptBlocking {
            val pool = PooledLoomH2Client(ClientConfig(pool = PoolConfig(maxPerHost = 1, maxTotal = 1)))
            try {
              java.lang.System.gc()
              Thread.sleep(150L)
              val heapBefore = java.lang.Runtime.getRuntime.totalMemory() - java.lang.Runtime.getRuntime.freeMemory()
              val response   = pool.send(Request.get(absUrl(s"http://127.0.0.1:${stub.port}/big")))
              val bodyStream = response.body.toStream
              val lazyProof  = bodyStream.knownChunk.isEmpty
              val total      = bodyStream.runFold(0L)((count: Long, _: Byte) => count + 1L) match {
                case Right(count) => count
                case Left(_)      => -1L
              }
              java.lang.System.gc()
              Thread.sleep(150L)
              val heapAfter = java.lang.Runtime.getRuntime.totalMemory() - java.lang.Runtime.getRuntime.freeMemory()
              val delta     = heapAfter - heapBefore
              proof(s"stream total=$total lazy=$lazyProof heapDelta=$delta bound=$HeapBoundBytes")
              // The lease is released once the lazy body is fully consumed:
              // the next request must reuse the same connection.
              val reuseBody = new String(
                pool.send(Request.get(absUrl(s"http://127.0.0.1:${stub.port}/ok"))).body.toArray,
                StandardCharsets.UTF_8,
              )
              (total, lazyProof, delta, stub.accepted.get(), pool.stats, reuseBody)
            } finally {
              pool.close()
            }
          }.map { case (total, lazyProof, delta, accepted, stats, reuseBody) =>
            assertTrue(
              total == BigBodyBytes.toLong,
              lazyProof,
              delta < HeapBoundBytes,
              accepted == 1,
              stats.checkedOut == 0,
              reuseBody == "pool-ok",
            )
          }
        }
      },
      test("per-request deadline maps to TimeoutException") {
        withStubServer(StubBehavior(slowMs = 5000)) { stub =>
          ZIO.attemptBlocking {
            val pool = PooledLoomH2Client(
              ClientConfig(deadline = DeadlineConfig(requestTimeout = Some(Duration.ofMillis(600)))),
            )
            try {
              var error = Option.empty[Throwable]
              try {
                pool.send(Request.get(absUrl(s"http://127.0.0.1:${stub.port}/slow")))
              } catch {
                case failure: Throwable => error = Some(failure)
              }
              proof(s"deadline error=${error.map(e => e.getClass.getSimpleName + ": " + e.getMessage)}")
              error
            } finally {
              pool.close()
            }
          }.map { error =>
            assertTrue(error.exists(_.isInstanceOf[TimeoutException]))
          }
        }
      },
      test("stream deadline fires mid-body as TimeoutException") {
        withStubServer(StubBehavior(bodyDelayMs = 5000)) { stub =>
          ZIO.attemptBlocking {
            val pool = PooledLoomH2Client(
              ClientConfig(deadline = DeadlineConfig(streamTimeout = Some(Duration.ofMillis(600)))),
            )
            try {
              val response = pool.send(Request.get(absUrl(s"http://127.0.0.1:${stub.port}/slow-body")))
              val headersOk = response.status == Status.Ok
              var error     = Option.empty[Throwable]
              try {
                response.body.toArray
              } catch {
                case failure: Throwable => error = Some(failure)
              }
              proof(s"stream-deadline headersOk=$headersOk error=${error.map(e => e.getClass.getSimpleName + ": " + e.getMessage)}")
              (headersOk, error)
            } finally {
              pool.close()
            }
          }.map { case (headersOk, error) =>
            assertTrue(headersOk, error.exists(_.isInstanceOf[TimeoutException]))
          }
        }
      },
      test("cancel during queue leaves slot accounting intact") {
        withStubServer(StubBehavior(slowMs = 2500)) { stub =>
          ZIO.attemptBlocking {
            val pool = PooledLoomH2Client(
              ClientConfig(pool = PoolConfig(maxPerHost = 1, maxTotal = 1, queueSize = 4)),
            )
            try {
              val gate   = new CountDownLatch(1)
              val holder = Thread.ofVirtual().name("pool-q-holder").start(() => {
                gate.countDown()
                try {
                  val response = pool.send(Request.get(absUrl(s"http://127.0.0.1:${stub.port}/slow")))
                  response.body.toArray
                } catch {
                  case _: Throwable => ()
                }
              })
              gate.await(5L, TimeUnit.SECONDS)
              Thread.sleep(300L)
              val first  = new AtomicReference[Either[Throwable, Status]]()
              val second = new AtomicReference[Either[Throwable, Status]]()
              val waiter1 = Thread.ofVirtual().name("pool-q-waiter-1").start(() => {
                try {
                  first.set(Right(pool.send(Request.get(absUrl(s"http://127.0.0.1:${stub.port}/ok"))).status))
                } catch {
                  case failure: Throwable => first.set(Left(failure))
                }
              })
              val waiter2 = Thread.ofVirtual().name("pool-q-waiter-2").start(() => {
                try {
                  val response = pool.send(Request.get(absUrl(s"http://127.0.0.1:${stub.port}/ok")))
                  val body     = new String(response.body.toArray, StandardCharsets.UTF_8)
                  if (response.status == Status.Ok && body == "pool-ok") second.set(Right(response.status))
                  else second.set(Left(new AssertionError("bad ok: " + response.status + " " + body)))
                } catch {
                  case failure: Throwable => second.set(Left(failure))
                }
              })
              waitUntil("queue-drain", 5000L) { pool.stats.queued >= 2 }
              waiter1.interrupt()
              waiter1.join(8000L)
              holder.join(8000L)
              waiter2.join(8000L)
              val stats = pool.stats
              val firstFailed = first.get() match {
                case Left(_: Throwable) => true
                case _                  => false
              }
              val secondOk = second.get() match {
                case Right(Status.Ok) => true
                case _                => false
              }
              proof(s"queue-cancel first=${first.get()} second=${second.get()} stats=$stats")
              (firstFailed, secondOk, stats)
            } finally {
              pool.close()
            }
          }.map { case (firstFailed, secondOk, stats) =>
            assertTrue(
              firstFailed,
              secondOk,
              stats.checkedOut == 0,
            )
          }
        }
      },
      test("double close is idempotent and send-after-close is a clear error") {
        withStubServer(StubBehavior()) { stub =>
          ZIO.attemptBlocking {
            val pool = PooledLoomH2Client(ClientConfig())
            pool.send(Request.get(absUrl(s"http://127.0.0.1:${stub.port}/ok")))
            pool.close()
            pool.close()
            var error = Option.empty[Throwable]
            try {
              pool.send(Request.get(absUrl(s"http://127.0.0.1:${stub.port}/ok")))
            } catch {
              case failure: Throwable => error = Some(failure)
            }
            proof(s"double-close error=${error.map(e => e.getClass.getSimpleName + ": " + e.getMessage)}")
            error
          }.map { error =>
            assertTrue(error.exists(e => e.isInstanceOf[IllegalStateException] && e.getMessage.contains("closed")))
          }
        }
      },
      test("server-closed pooled connections are evicted, never black-holed") {
        withStubServer(StubBehavior()) { stub =>
          ZIO.attemptBlocking {
            val pool = PooledLoomH2Client(ClientConfig(pool = PoolConfig(maxPerHost = 1, maxTotal = 1)))
            try {
              val first = pool.send(Request.get(absUrl(s"http://127.0.0.1:${stub.port}/poison")))
              val firstStatus = first.status
              val firstBody = new String(first.body.toArray, StandardCharsets.UTF_8)
              // The server closed the pooled connection after responding; at
              // most one subsequent send may observe the dead socket, and the
              // pool must recover onto a fresh connection by itself.
              var attempts  = 0
              var recovered = false
              while (!recovered && attempts < 3) {
                attempts += 1
                try {
                  val response = pool.send(Request.get(absUrl(s"http://127.0.0.1:${stub.port}/ok")))
                  recovered = new String(response.body.toArray, StandardCharsets.UTF_8) == "pool-ok"
                } catch {
                  case _: IOException => ()
                }
              }
              proof(s"evict first=<$firstBody> status=$firstStatus recovered=$recovered attempts=$attempts accepted=${stub.accepted.get()}")
              (firstBody, firstStatus, recovered, stub.accepted.get())
            } finally {
              pool.close()
            }
          }.map { case (firstBody, firstStatus, recovered, accepted) =>
            assertTrue(firstBody == "pool-poison-ok", firstStatus == Status.Ok, recovered, accepted == 2)
          }
        }
      },
      test("GOAWAY connections are evicted with a clear error") {
        withStubServer(StubBehavior()) { stub =>
          ZIO.attemptBlocking {
            val pool = PooledLoomH2Client(ClientConfig(pool = PoolConfig(maxPerHost = 1, maxTotal = 1)))
            try {
              var error = Option.empty[Throwable]
              try {
                pool.send(Request.get(absUrl(s"http://127.0.0.1:${stub.port}/goaway")))
              } catch {
                case failure: Throwable => error = Some(failure)
              }
              val retry = pool.send(Request.get(absUrl(s"http://127.0.0.1:${stub.port}/ok")))
              val retryBody = new String(retry.body.toArray, StandardCharsets.UTF_8)
              proof(s"goaway error=${error.map(e => e.getClass.getSimpleName + ": " + e.getMessage)} retry=$retryBody accepted=${stub.accepted.get()}")
              (error, retryBody, stub.accepted.get())
            } finally {
              pool.close()
            }
          }.map { case (error, retryBody, accepted) =>
            assertTrue(
              error.exists(e => e.isInstanceOf[IOException] && e.getMessage.contains("GOAWAY")),
              retryBody == "pool-ok",
              accepted == 2,
            )
          }
        }
      },
      test("POST bodies still round-trip through pooled connections") {
        withStubServer(StubBehavior()) { stub =>
          ZIO.attemptBlocking {
            val pool = PooledLoomH2Client(ClientConfig(pool = PoolConfig(maxPerHost = 1, maxTotal = 1)))
            try {
              val request  = Request.post(absUrl(s"http://127.0.0.1:${stub.port}/echo"), Body.fromString("pool-echo-123"))
              val response = pool.send(request)
              val body     = new String(response.body.toArray, StandardCharsets.UTF_8)
              proof(s"echo body=$body accepted=${stub.accepted.get()}")
              (body, stub.accepted.get())
            } finally {
              pool.close()
            }
          }.map { case (body, accepted) =>
            assertTrue(body == "13", accepted == 1)
          }
        }
      },
    ) @@ sequential

  private def absUrl(raw: String): URL =
    URL.parse(raw).fold(err => throw new IllegalArgumentException("Invalid test URL: " + err), identity)

  private def proof(line: String): Unit =
    println(s">> [ClientPoolStreamingSpec] $line")

  private def waitUntil(what: String, timeoutMs: Long)(condition: => Boolean): Unit = {
    val deadline = java.lang.System.currentTimeMillis() + timeoutMs
    while (!condition && java.lang.System.currentTimeMillis() < deadline) Thread.sleep(20L)
    if (!condition) throw new IllegalStateException(s"Timed out waiting for $what")
  }

  private def runConcurrent(n: Int)(task: => Boolean): List[Boolean] = {
    val results = new ConcurrentHashMap[Int, Boolean]()
    val threads = (0 until n).map { index =>
      Thread.ofVirtual().name(s"pool-concurrent-$index").start(() => {
        try {
          results.put(index, task)
        } catch {
          case _: Throwable => results.put(index, false)
        }
      })
    }
    threads.foreach(_.join(30000L))
    (0 until n).map(results.getOrDefault(_, false)).toList
  }

  private final case class StubBehavior(slowMs: Long = 0L, bodyDelayMs: Long = 0L)

  private final class StubServer(
    val port: Int,
    val accepted: AtomicInteger,
    val concurrent: AtomicInteger,
    val maxConcurrent: AtomicInteger,
    val cancelled: java.util.Set[Int],
    stop: () => Unit,
  ) {
    def close(): Unit = stop()
  }

  private def withStubServer[R](behavior: StubBehavior)(
    use: StubServer => ZIO[R, Throwable, TestResult],
  ): ZIO[R & Scope, Throwable, TestResult] =
    ZIO
      .acquireRelease(ZIO.attempt(startStub(behavior)))(stub => ZIO.succeed(stub.close()))
      .flatMap(use)

  private def startStub(behavior: StubBehavior): StubServer = {
    val serverSocket = new ServerSocket(0, 128, java.net.InetAddress.getByName("127.0.0.1"))
    val accepted     = new AtomicInteger(0)
    val concurrent   = new AtomicInteger(0)
    val maxSeen      = new AtomicInteger(0)
    val cancelled    = ConcurrentHashMap.newKeySet[Int]()
    val running      = new java.util.concurrent.atomic.AtomicBoolean(true)
    val acceptor     = Thread.ofVirtual().name("pool-stub-acceptor").start(() => {
      while (running.get()) {
        try {
          val socket = serverSocket.accept()
          accepted.incrementAndGet()
          val now = concurrent.incrementAndGet()
          var prev = maxSeen.get()
          while (now > prev && !maxSeen.compareAndSet(prev, now)) prev = maxSeen.get()
          Thread.ofVirtual().name("pool-stub-conn").start(() => {
            try {
              handleStubConn(socket, behavior, cancelled)
            } finally {
              concurrent.decrementAndGet()
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
      concurrent,
      maxSeen,
      cancelled,
      () => {
        running.set(false)
        try serverSocket.close()
        catch { case _: Throwable => () }
        acceptor.join(5000L)
      },
    )
  }

  private def handleStubConn(socket: Socket, behavior: StubBehavior, cancelled: java.util.Set[Int]): Unit = {
    socket.setSoTimeout(20000)
    val input  = socket.getInputStream
    val output = socket.getOutputStream
    val reader = new StubFrameReader(input)
    readPreface(input)
    // Client preface SETTINGS: read until the client's first SETTINGS, ack it.
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
    var sendWindow = 65535
    var open       = true
    while (open) {
      try {
        reader.readFrame() match {
          case Headers(streamId, block, endStream, endHeaders, _, _) =>
            val headerBlock =
              if (endHeaders) block
              else block ++ readContinuations(reader, output, streamId)
            val fields  = hpack.decode(headerBlock).fold(err => throw new IOException("stub HPACK: " + err), identity)
            val path    = fields.collectFirst { case HeaderField(":path", value, _) => value }.getOrElse("/")
            val requestBytes =
              if (endStream) 0L
              else drainRequestBody(reader, output, streamId, cancelled)
            dispatch(output, reader, hpack, streamId, path, requestBytes, behavior, cancelled, socket) match {
              case StubAction.KeepGoing => ()
              case StubAction.Close     => open = false
            }
            // Reads that arrive while a /big send is gated on the window are
            // consumed inside sendBig; top-level WindowUpdate/Ping housekeeping:
            sendWindow = sendWindow
          case WindowUpdate(0, increment) =>
            sendWindow += increment
          case WindowUpdate(_, _)         => ()
          case Ping(false, data)          =>
            writeFrame(output, Ping(ack = true, data))
            output.flush()
          case Settings(false, _)         =>
            writeFrame(output, Settings(ack = true, Nil))
            output.flush()
          case RstStream(streamId, _)     =>
            cancelled.add(streamId)
          case _                          => ()
        }
      } catch {
        case _: EOFException => open = false
        case _: java.net.SocketTimeoutException => open = false
        case _: java.net.SocketException        => open = false
        case _: IOException                     => open = false
      }
    }
  }

  private sealed trait StubAction
  private object StubAction {
    case object KeepGoing extends StubAction
    case object Close extends StubAction
  }

  private def dispatch(
    output: OutputStream,
    reader: StubFrameReader,
    hpack: HpackCodec,
    streamId: Int,
    path: String,
    requestBytes: Long,
    behavior: StubBehavior,
    cancelled: java.util.Set[Int],
    socket: Socket,
  ): StubAction = {
    path match {
      case "/goaway" =>
        writeFrame(output, GoAway(lastStreamId = 0, errorCode = H2Error.Code.NO_ERROR, debugData = Chunk.empty))
        output.flush()
        StubAction.Close
      case "/poison" =>
        sendText(output, hpack, streamId, "pool-poison-ok")
        output.flush()
        // Graceful linger: let the client's read observe HEADERS+DATA before
        // the FIN, so the test proves pool eviction - not a TCP RST race.
        Thread.sleep(300L)
        StubAction.Close
      case "/slow"   =>
        Thread.sleep(behavior.slowMs)
        sendText(output, hpack, streamId, "pool-slow-ok")
        StubAction.KeepGoing
      case "/slow-body" =>
        sendHeaders(output, hpack, streamId, endStream = false)
        output.flush()
        Thread.sleep(behavior.bodyDelayMs)
        sendData(output, reader, hpack, streamId, "pool-slow-body-ok".getBytes(StandardCharsets.UTF_8), cancelled)
        StubAction.KeepGoing
      case "/gated-body" =>
        // Headers first, then block READING (not sleeping) so a client RST is
        // observed deterministically instead of racing a sleep.
        sendHeaders(output, hpack, streamId, endStream = false)
        output.flush()
        var gated = true
        while (gated) {
          try {
            reader.readFrame() match {
              case RstStream(`streamId`, _) =>
                cancelled.add(streamId)
                gated = false
              case Ping(false, data) =>
                writeFrame(output, Ping(ack = true, data))
                output.flush()
              case _ => ()
            }
          } catch {
            case _: EOFException => gated = false
            case _: IOException  => gated = false
          }
        }
        StubAction.KeepGoing
      case "/big" =>
        sendHeaders(output, hpack, streamId, endStream = false)
        output.flush()
        sendBig(output, reader, streamId, BigBodyBytes, cancelled)
        StubAction.KeepGoing
      case "/echo" =>
        sendText(output, hpack, streamId, requestBytes.toString)
        StubAction.KeepGoing
      case _ =>
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
        case RstStream(`streamId`, _) =>
          cancelled.add(streamId)
          throw new IOException("stub: request RST")
        case Ping(false, ping) =>
          writeFrame(output, Ping(ack = true, ping))
          output.flush()
        case _ => ()
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

  private def sendData(
    output: OutputStream,
    reader: StubFrameReader,
    hpack: HpackCodec,
    streamId: Int,
    bytes: Array[Byte],
    cancelled: java.util.Set[Int],
  ): Unit = {
    var offset = 0
    var window = 65535
    while (offset < bytes.length) {
      while (window <= 0) {
        reader.readFrame() match {
          case WindowUpdate(_, increment) => window += increment
          case Ping(false, data)          =>
            writeFrame(output, Ping(ack = true, data))
            output.flush()
          case RstStream(`streamId`, _)   =>
            cancelled.add(streamId)
            throw new IOException("stub: body send RST")
          case _                          => ()
        }
      }
      val length = math.min(bytes.length - offset, math.min(16384, window))
      val chunk  = Chunk.fromArray(java.util.Arrays.copyOfRange(bytes, offset, offset + length))
      val last   = offset + length == bytes.length
      writeFrame(output, Data(streamId, chunk, endStream = last))
      output.flush()
      window -= length
      offset += length
    }
  }

  private def sendBig(
    output: OutputStream,
    reader: StubFrameReader,
    streamId: Int,
    total: Int,
    cancelled: java.util.Set[Int],
  ): Unit = {
    val frame = new Array[Byte](16384)
    java.util.Arrays.fill(frame, 'x'.toByte)
    var sent   = 0
    var window = 65535
    while (sent < total) {
      while (window <= 0) {
        reader.readFrame() match {
          case WindowUpdate(_, increment) => window += increment
          case Ping(false, data)          =>
            writeFrame(output, Ping(ack = true, data))
            output.flush()
          case RstStream(`streamId`, _)   =>
            cancelled.add(streamId)
            throw new IOException("stub: big send RST")
          case _                          => ()
        }
      }
      val length = math.min(total - sent, math.min(frame.length, window))
      val chunk  =
        if (length == frame.length) Chunk.fromArray(frame)
        else Chunk.fromArray(java.util.Arrays.copyOf(frame, length))
      val last   = sent + length == total
      writeFrame(output, Data(streamId, chunk, endStream = last))
      output.flush()
      window -= length
      sent += length
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
        case Ping(false, data) =>
          writeFrame(output, Ping(ack = true, data))
          output.flush()
        case _ => ()
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
          case Right((decoded, rest)) =>
            buffer = rest
            result = decoded
          case Left(H2Error.InsufficientData) =>
            val chunk = new Array[Byte](8192)
            val read  = input.read(chunk)
            if (read < 0) throw new EOFException("stub: EOF mid-frame")
            buffer = buffer ++ Chunk.fromArray(java.util.Arrays.copyOf(chunk, read))
          case Left(error) =>
            throw new IOException("stub: bad frame: " + error)
        }
      }
      result
    }
  }
}
