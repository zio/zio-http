package zio.http.h2

import java.io.{ByteArrayOutputStream, IOException, InputStream, OutputStream}
import java.nio.charset.StandardCharsets

import scala.annotation.experimental

import zio._
import zio.blocks.chunk.Chunk
import zio.blocks.context.Context
import zio.blocks.endpoint.RoutePattern
import zio.blocks.mux.Mux
import zio.test.TestAspect.sequential
import zio.test._

import zio.http.{BindAddress, Connector, DefectHandler, Handler, Response, Route, Routes, ServerHandle}
import zio.http.h2.H2Frame._
import zio.http.h2.H2RawClientFixture.{RawH2Client, SimpleRoutes, withRawServer}
import zio.http.h2.hpack.{HeaderField, Hpack}

/**
 * Connection-lifecycle behavior: protocol-error shutdowns, writer failures,
 * GOAWAY drain, and shutdown safety.
 */
@experimental
object H2ConnectionLifecycleSpec extends ZIOSpecDefault {

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("H2ConnectionLifecycleSpec")(
      // ── H2Connection.handleConnectionFrame catch-all ────────────────────
      test("connection-level Priority frame causes protocol error") {
        withRawServer { port =>
          ZIO.attemptBlocking {
            val client = new RawH2Client(port)
            try {
              client.sendRaw(
                FrameCodec.encode(Priority(streamId = 0, dependency = 0, weight = 15, exclusive = false)).toArray,
              )
              client.socket.setSoTimeout(3000)
              val closed =
                try client.socket.getInputStream.read() == -1
                catch { case _: IOException => true }
              assertTrue(closed)
            } finally {
              try client.close()
              catch { case _: Exception => () }
            }
          }
        }
      },
      // ── H2Transport.awaitHeaders: non-Headers initial frame → error ──────
      test("sending Data before Headers on new stream causes error") {
        withRawServer { port =>
          ZIO.attemptBlocking {
            val client = new RawH2Client(port)
            try {
              client.sendRaw(
                FrameCodec
                  .encode(Data(streamId = 1, data = Chunk.fromArray("hello".getBytes), endStream = true))
                  .toArray,
              )
              client.socket.setSoTimeout(3000)
              val closed =
                try client.socket.getInputStream.read() == -1
                catch { case _: IOException => true }
              assertTrue(closed)
            } finally {
              try client.close()
              catch { case _: Exception => () }
            }
          }
        }
      },
      // ── H2Connection NonFatal error in writerLoop → writer I/O failure ───
      test("writerLoop IOException causes shutdown via writer-io-failure path") {
        ZIO.attemptBlocking {
          val preface       = "PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n".getBytes(StandardCharsets.US_ASCII)
          val settingsBytes = FrameCodec.encode(Settings(ack = false, Nil)).toArray
          val handshake     = preface ++ settingsBytes
          val headersBytes  = FrameCodec
            .encode(
              Headers(
                streamId = 1,
                headerBlock = Hpack.encode(
                  List(
                    HeaderField(":method", "GET"),
                    HeaderField(":path", "/"),
                    HeaderField(":scheme", "http"),
                    HeaderField(":authority", "localhost"),
                  ),
                ),
                endStream = true,
                endHeaders = true,
              ),
            )
            .toArray

          val allInputBytes = handshake ++ headersBytes
          var inputPos      = 0
          val fakeIn        = new InputStream {
            override def read(): Int                                     = -1
            override def read(buf: Array[Byte], off: Int, len: Int): Int = {
              if (inputPos >= allInputBytes.length) -1
              else {
                val n = math.min(len, allInputBytes.length - inputPos)
                java.lang.System.arraycopy(allInputBytes, inputPos, buf, off, n)
                inputPos += n
                n
              }
            }
          }

          var writeCount = 0
          val fakeOut    = new OutputStream {
            override def write(b: Int): Unit                             = ()
            override def write(b: Array[Byte]): Unit                     = check()
            override def write(b: Array[Byte], off: Int, len: Int): Unit = {
              writeCount += 1
              if (writeCount > 3) throw new IOException("writer-injected-failure")
            }
            override def flush(): Unit                                   = ()
            private def check(): Unit                                    = ()
          }

          val routes     = Routes(Route(RoutePattern.GET, Handler.succeed(Response.ok)))
          val connection = new H2Connection(fakeIn, fakeOut, maxConcurrentStreams = 10)
          val completed  = new java.util.concurrent.atomic.AtomicBoolean(false)
          val thread     = new Thread(() => {
            try connection.run(_ => ())
            catch { case _: Throwable => () }
            finally completed.set(true)
          })
          thread.start()
          thread.join(3000)
          assertTrue(completed.get())
        }
      },
      // ── H2Connection.writerLoop: drain Left(MuxError) via goaway ─────────
      test("goaway while stream active exercises writerLoop error drain path") {
        withRawServer { port =>
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
                          HeaderField(":method", "POST"),
                          HeaderField(":path", "/"),
                          HeaderField(":scheme", "http"),
                          HeaderField(":authority", s"127.0.0.1:$port"),
                          HeaderField("content-length", "1000"),
                        ),
                      ),
                      endStream = false,
                      endHeaders = true,
                    ),
                  )
                  .toArray,
              )
              Thread.sleep(20)
              client.sendRaw(
                FrameCodec
                  .encode(GoAway(lastStreamId = 0, errorCode = H2Error.Code.NO_ERROR, debugData = Chunk.empty))
                  .toArray,
              )
              client.socket.setSoTimeout(3000)
              val closed =
                try client.socket.getInputStream.read() == -1
                catch { case _: IOException => true }
              assertTrue(closed)
            } finally {
              try client.close()
              catch { case _: Exception => () }
            }
          }
        }
      },
      // ── H2Connection.checkedIncrement: overflow check ─────────────────────
      test("H2Connection rejects WindowUpdate that overflows connection window") {
        withRawServer { port =>
          ZIO.attemptBlocking {
            val client = new RawH2Client(port)
            try {
              client.sendRaw(FrameCodec.encode(WindowUpdate(streamId = 0, increment = Int.MaxValue)).toArray)
              client.socket.setSoTimeout(3000)
              val closed =
                try client.socket.getInputStream.read() == -1
                catch { case _: IOException => true }
              assertTrue(closed)
            } finally {
              try client.close()
              catch { case _: Exception => () }
            }
          }
        }
      },
      // ── H2Connection: shutdown called twice is idempotent ────────────────
      test("shutting down server twice is safe") {
        ZIO
          .acquireRelease(
            ZIO.attempt(
              ServerHandle.live(
                List(
                  new H2Transport(
                    SimpleRoutes,
                    Context.empty,
                    Connector(bind = BindAddress.localhost(0)),
                    DefectHandler.default,
                  ).start(),
                ),
              ),
            ),
          )(h => ZIO.succeed(h.shutdownAndWait()))
          .flatMap { handle =>
            ZIO.attempt {
              handle.shutdownAndWait()
              handle.shutdownAndWait()
              assertTrue(true)
            }
          }
      },
      // ── H2ConnectionControl: sendRstStream on non-existent stream ─────────
      test("sendRstStream on non-existent stream does not throw") {
        ZIO.attemptBlocking {
          val out     = new ByteArrayOutputStream()
          val mux     = Mux[Int, H2Frame, H2Frame](10)
          val control = new H2ConnectionControl(out, mux, idleTimeoutMs = 0L, requestTimeoutMs = 0L)
          control.sendRstStream(streamId = 99, errorCode = H2Error.Code.CANCEL)
          assertTrue(out.size() > 0)
        }
      },
      test("H2Connection NonFatal propagates through run loop") {
        ZIO.attemptBlocking {
          val preface       = "PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII)
          val settingsBytes = FrameCodec.encode(Settings(ack = false, Nil)).toArray
          val handshake     = preface ++ settingsBytes ++ FrameCodec.encode(Settings(ack = true, Nil)).toArray
          var calls         = 0
          val fakeIn        = new java.io.InputStream {
            override def read(): Int                                     = -1
            override def read(buf: Array[Byte], off: Int, len: Int): Int = {
              calls += 1
              if (calls == 1) {
                val n = math.min(len, handshake.length)
                java.lang.System.arraycopy(handshake, 0, buf, off, n)
                n
              } else throw new RuntimeException("injected")
            }
          }
          val fakeOut       = new ByteArrayOutputStream()
          val conn          = new H2Connection(fakeIn, fakeOut, maxConcurrentStreams = 10)
          val errRef        = new java.util.concurrent.atomic.AtomicReference[Throwable](null)
          val t             = new Thread(() =>
            try conn.run(_ => ())
            catch { case e: Throwable => errRef.set(e) },
          )
          t.start(); t.join(3000)
          val err           = errRef.get()
          assertTrue(err != null && err.isInstanceOf[RuntimeException])
        }
      },
      // ── GOAWAY drain: early-exit when nothing is in flight ──────────────
      test("graceful drain with zero in-flight streams closes fast") {
        ZIO.attemptBlocking {
          val fakeIn    = new java.io.ByteArrayInputStream(Array.emptyByteArray)
          val fakeOut   = new ByteArrayOutputStream()
          val conn      = new H2Connection(fakeIn, fakeOut, maxConcurrentStreams = 10, drainTimeoutMs = 5000L)
          val start     = java.lang.System.nanoTime()
          conn.initiateGracefulShutdown()
          val elapsedMs = (java.lang.System.nanoTime() - start) / 1000000L
          assertTrue(elapsedMs < 1000L)
        }
      },
      // ── GOAWAY drain: absorbed interrupts restore interrupt status ──────
      test("graceful drain restores interrupt status after absorbing interrupt") {
        ZIO.attemptBlocking {
          val fakeIn       = new java.io.ByteArrayInputStream(Array.emptyByteArray)
          val fakeOut      = new ByteArrayOutputStream()
          val conn         = new H2Connection(fakeIn, fakeOut, maxConcurrentStreams = 10, drainTimeoutMs = 10000L)
          val mux          = Mux[Int, H2Frame, H2Frame](10)
          val opened: Any  = mux.open(1)
          val field        = classOf[H2Connection].getDeclaredField("activeStreams")
          field.setAccessible(true)
          val streams      = field.get(conn).asInstanceOf[java.util.concurrent.ConcurrentHashMap[Int, Any]]
          streams.put(1, opened)
          val sawInterrupt = new java.util.concurrent.atomic.AtomicBoolean(false)
          val drainThread  = new Thread(() => {
            conn.initiateGracefulShutdown()
            sawInterrupt.set(Thread.currentThread().isInterrupted)
          })
          drainThread.start()
          Thread.sleep(300)
          drainThread.interrupt()
          Thread.sleep(100)
          streams.remove(1)
          drainThread.join(4000)
          assertTrue(!drainThread.isAlive && sawInterrupt.get())
        }
      },
    ) @@ sequential
}
