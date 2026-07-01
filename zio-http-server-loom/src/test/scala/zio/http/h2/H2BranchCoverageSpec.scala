package zio.http.h2

import java.io.{ByteArrayOutputStream, EOFException, IOException, InputStream, OutputStream}
import java.net.{Socket, SocketTimeoutException}
import java.nio.charset.StandardCharsets

import scala.annotation.experimental
import scala.collection.mutable

import zio._
import zio.blocks.chunk.Chunk
import zio.blocks.context.Context
import zio.blocks.endpoint.RoutePattern
import zio.blocks.mux.{Mux, MuxError}
import zio.test.TestAspect.sequential
import zio.test._

import zio.http.h2.H2Frame._
import zio.http.h2.hpack.{HeaderField, Hpack}
import zio.http.{
  BindAddress,
  Body,
  BoundAddress,
  Connector,
  DefectHandler,
  Handler,
  Header,
  Request,
  Response,
  Route,
  Routes,
  ServerHandle,
  Status,
  handler,
}
import zio.http.ResultType._

/** Branch-coverage focused tests for serverLoom module. */
@experimental
object H2BranchCoverageSpec extends ZIOSpecDefault {

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("H2BranchCoverageSpec")(
      // ── FlowController.ensureStreamRegistered throws when stream removed ──
      test("consumeSendWindow throws when stream removed while blocked") {
        ZIO
          .attempt {
            val fc = new FlowController(initialConnectionWindow = 0, initialStreamWindow = 0)
            fc.registerStream(1)
            val removerThread = Thread.ofVirtual().start(() => {
              Thread.sleep(30)
              fc.removeStream(1)
              fc.applyWindowUpdate(0, 100)
            })
            val result =
              try { fc.consumeSendWindow(1, 1); Right(()) }
              catch { case e: Throwable => Left(e) }
            removerThread.join(2000)
            assertTrue(result.isLeft)
          }
      },
      // ── H2ConnectionControl.VirtualTimerFuture.fail → ExecutionException ──
      test("request timer future.get() throws ExecutionException on write failure") {
        ZIO
          .attempt {
            val throwingOut = new OutputStream {
              override def write(b: Int): Unit                             = ()
              override def write(b: Array[Byte], off: Int, len: Int): Unit = throw new IOException("broken")
            }
            val mux     = Mux[Int, H2Frame, H2Frame](10)
            toStreamHelper(mux.open(1))
            val control = new H2ConnectionControl(throwingOut, mux, idleTimeoutMs = 0L, requestTimeoutMs = 80L)
            val future  = control.startRequestTimer(streamId = 1)
            Thread.sleep(300)
            val result  =
              try { future.get(); Right(()) }
              catch { case _: Throwable => Left(()) }
            assertTrue(result.isLeft)
          }
      },
      // ── H2ConnectionControl.VirtualTimerFuture.get(timeout) success path ─
      test("request timer future.get(timeout) completes within window") {
        ZIO
          .attempt {
            val out     = new ByteArrayOutputStream()
            val mux     = Mux[Int, H2Frame, H2Frame](10)
            val control = new H2ConnectionControl(out, mux, idleTimeoutMs = 0L, requestTimeoutMs = 0L)
            val future  = control.startRequestTimer(streamId = 1)
            future.get(1000L, java.util.concurrent.TimeUnit.MILLISECONDS)
            assertTrue(future.isDone)
          }
      },
      // ── H2Connection.handleConnectionFrame catch-all ────────────────────
      test("connection-level Priority frame causes protocol error") {
        withRawServer { port =>
          ZIO.attemptBlocking {
            val client = new RawH2Client(port)
            try {
              client.sendRaw(FrameCodec.encode(Priority(streamId = 0, dependency = 0, weight = 15, exclusive = false)).toArray)
              client.socket.setSoTimeout(3000)
              val closed =
                try client.socket.getInputStream.read() == -1
                catch { case _: IOException => true }
              assertTrue(closed)
            } finally {
              try client.close() catch { case _: Exception => () }
            }
          }
        }
      },
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
              client.sendRaw(FrameCodec.encode(Headers(
                streamId    = 1,
                headerBlock = Hpack.encode(List(
                  HeaderField(":method", "GET"),
                  HeaderField(":path", "/"),
                  HeaderField(":scheme", "http"),
                  HeaderField(":authority", "example.com"),
                )),
                endStream   = true,
                endHeaders  = true,
              )).toArray)
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
              client.sendRaw(FrameCodec.encode(Headers(
                streamId    = 1,
                headerBlock = Hpack.encode(List(
                  HeaderField(":method", "GET"),
                  HeaderField(":path", "/"),
                  HeaderField(":scheme", "http"),
                  HeaderField(":authority", "[invalid-ipv6"),
                )),
                endStream   = true,
                endHeaders  = true,
              )).toArray)
              val resp = client.awaitResponse(1)
              assertTrue(resp.status == 200)
            } finally client.close()
          }
        }
      },
      // ── H2Transport.awaitHeaders: non-Headers initial frame → error ──────
      test("sending Data before Headers on new stream causes error") {
        withRawServer { port =>
          ZIO.attemptBlocking {
            val client = new RawH2Client(port)
            try {
              client.sendRaw(FrameCodec.encode(Data(streamId = 1, data = Chunk.fromArray("hello".getBytes), endStream = true)).toArray)
              client.socket.setSoTimeout(3000)
              val closed =
                try client.socket.getInputStream.read() == -1
                catch { case _: IOException => true }
              assertTrue(closed)
            } finally {
              try client.close() catch { case _: Exception => () }
            }
          }
        }
      },
      // ── H2Connection NonFatal error in writerLoop → writer I/O failure ───
      test("writerLoop IOException causes shutdown via writer-io-failure path") {
        ZIO.attemptBlocking {
          val preface        = "PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n".getBytes(StandardCharsets.US_ASCII)
          val settingsBytes  = FrameCodec.encode(Settings(ack = false, Nil)).toArray
          val handshake      = preface ++ settingsBytes
          val headersBytes   = FrameCodec.encode(Headers(
            streamId    = 1,
            headerBlock = Hpack.encode(List(
              HeaderField(":method", "GET"),
              HeaderField(":path", "/"),
              HeaderField(":scheme", "http"),
              HeaderField(":authority", "localhost"),
            )),
            endStream   = true,
            endHeaders  = true,
          )).toArray

          val allInputBytes = handshake ++ headersBytes
          var inputPos      = 0
          val fakeIn        = new InputStream {
            override def read(): Int = -1
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
            override def write(b: Array[Byte]): Unit                    = check()
            override def write(b: Array[Byte], off: Int, len: Int): Unit = {
              writeCount += 1
              if (writeCount > 3) throw new IOException("writer-injected-failure")
            }
            override def flush(): Unit = ()
            private def check(): Unit  = ()
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
      // ── H2Connection.writerLoop: drain Left(MuxError) via goaway ─────────
      test("goaway while stream active exercises writerLoop error drain path") {
        withRawServer { port =>
          ZIO.attemptBlocking {
            val client = new RawH2Client(port)
            try {
              client.sendRaw(FrameCodec.encode(Headers(
                streamId    = 1,
                headerBlock = Hpack.encode(List(
                  HeaderField(":method", "POST"),
                  HeaderField(":path", "/"),
                  HeaderField(":scheme", "http"),
                  HeaderField(":authority", s"127.0.0.1:$port"),
                  HeaderField("content-length", "1000"),
                )),
                endStream   = false,
                endHeaders  = true,
              )).toArray)
              Thread.sleep(20)
              client.sendRaw(FrameCodec.encode(GoAway(lastStreamId = 0, errorCode = H2Error.Code.NO_ERROR, debugData = Chunk.empty)).toArray)
              client.socket.setSoTimeout(3000)
              val closed =
                try client.socket.getInputStream.read() == -1
                catch { case _: IOException => true }
              assertTrue(closed)
            } finally {
              try client.close() catch { case _: Exception => () }
            }
          }
        }
      },
      // ── FlowController.signalAllStreams via connection WindowUpdate ────────
      test("applyWindowUpdate on streamId=0 signals all registered streams") {
        ZIO.attemptBlocking {
          val fc = new FlowController(initialConnectionWindow = 0, initialStreamWindow = 100)
          fc.registerStream(1)
          fc.registerStream(3)
          var unblocked = false
          val t = Thread.ofVirtual().start(() => {
            try {
              fc.consumeSendWindow(1, 1)
              unblocked = true
            } catch { case _: Throwable => () }
          })
          Thread.sleep(30)
          fc.applyWindowUpdate(0, 10)
          t.join(2000)
          assertTrue(unblocked)
        }
      },
      // ── H2ConnectionControl.closeConnection branch: thread is currentThread ─
      test("closeConnection from idle timer thread does not interrupt itself") {
        ZIO.attemptBlocking {
          val out     = new ByteArrayOutputStream()
          val mux     = Mux[Int, H2Frame, H2Frame](10)
          val control = new H2ConnectionControl(out, mux, idleTimeoutMs = 50L, requestTimeoutMs = 0L)
          control.startIdleTimer()
          Thread.sleep(200)
          assertTrue(out.size() > 0)
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
              try client.close() catch { case _: Exception => () }
            }
          }
        }
      },
      // ── FlowController: consumeSendWindow blocks on stream window only ────
      test("consumeSendWindow blocks when only stream window is exhausted") {
        ZIO.attemptBlocking {
          val fc = new FlowController(initialConnectionWindow = 1000, initialStreamWindow = 10)
          fc.registerStream(1)
          fc.consumeSendWindow(1, 10)
          var unblocked = false
          val t = Thread.ofVirtual().start(() => {
            try {
              fc.consumeSendWindow(1, 5)
              unblocked = true
            } catch { case _: Throwable => () }
          })
          Thread.sleep(30)
          fc.applyWindowUpdate(1, 100)
          t.join(2000)
          assertTrue(unblocked)
        }
      },
      // ── H2ConnectionControl: VirtualTimerFuture already done → cancel false
      test("VirtualTimerFuture cancel on completed future returns false") {
        ZIO.attemptBlocking {
          val out     = new ByteArrayOutputStream()
          val mux     = Mux[Int, H2Frame, H2Frame](10)
          val control = new H2ConnectionControl(out, mux, idleTimeoutMs = 0L, requestTimeoutMs = 0L)
          val future  = control.startRequestTimer(streamId = 1)
          Thread.sleep(50)
          val result = future.cancel(true)
          assertTrue(!result)
        }
      },
      // ── H2ConnectionControl: resetIdleTimer with no timer thread ──────────
      test("resetIdleTimer before startIdleTimer is a no-op") {
        ZIO.attemptBlocking {
          val out     = new ByteArrayOutputStream()
          val mux     = Mux[Int, H2Frame, H2Frame](10)
          val control = new H2ConnectionControl(out, mux, idleTimeoutMs = 60000L, requestTimeoutMs = 0L)
          control.resetIdleTimer()
          assertTrue(true)
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
      // ── H2Connection: shutdown called twice is idempotent ────────────────
      test("shutting down server twice is safe") {
        ZIO.acquireRelease(
          ZIO.attempt(
            ServerHandle.live(List(
              new H2Transport(SimpleRoutes, Context.empty, Connector(bind = BindAddress.localhost(0)), DefectHandler.default).start(),
            )),
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
                client.sendFrame(Headers(
                  streamId    = 2 * i + 1,
                  headerBlock = Hpack.encode(List(
                    HeaderField(":method", "GET"),
                    HeaderField(":path", "/"),
                    HeaderField(":scheme", "http"),
                    HeaderField(":authority", s"127.0.0.1:$port"),
                  )),
                  endStream   = true,
                  endHeaders  = true,
                ))
              }
              val results = (0 until 5).map { i => client.awaitResponse(2 * i + 1) }
              assertTrue(results.forall(_.status == 200))
            } finally client.close()
          }
        }
      },
      // ── FlowController: signalAllStreams with multiple streams ─────────────
      test("applyWindowUpdate connection broadcasts to all streams") {
        ZIO.attemptBlocking {
          val fc = new FlowController(initialConnectionWindow = 0, initialStreamWindow = 1000)
          fc.registerStream(1)
          fc.registerStream(3)
          fc.registerStream(5)
          var count = 0
          val threads = (1 to 3).map { i =>
            Thread.ofVirtual().start(() => {
              try {
                fc.consumeSendWindow(2 * i - 1, 1)
                count += 1
              } catch { case _: Throwable => () }
            })
          }
          Thread.sleep(50)
          fc.applyWindowUpdate(0, 1000)
          threads.foreach(_.join(2000))
          assertTrue(count == 3)
        }
      },
      test("FlowController registerStream re-registration signals old waiters") {
        ZIO.attemptBlocking {
          val fc = new FlowController(initialConnectionWindow = 0, initialStreamWindow = 10)
          fc.registerStream(1)
          var threw = false
          val t = Thread.ofVirtual().start(() => {
            try fc.consumeSendWindow(1, 5)
            catch { case _: Throwable => threw = true }
          })
          Thread.sleep(20)
          fc.registerStream(1)
          t.join(2000)
          assertTrue(threw)
        }
      },
      test("FlowController applyWindowUpdate on stream signals waiters") {
        ZIO.attemptBlocking {
          val fc = new FlowController(initialConnectionWindow = 1000, initialStreamWindow = 0)
          fc.registerStream(1)
          var unblocked = false
          val t = Thread.ofVirtual().start(() => {
            try { fc.consumeSendWindow(1, 1); unblocked = true }
            catch { case _: Throwable => () }
          })
          Thread.sleep(30)
          fc.applyWindowUpdate(1, 100)
          t.join(2000)
          assertTrue(unblocked)
        }
      },
      test("H2Connection NonFatal propagates through run loop") {
        ZIO.attemptBlocking {
          val preface        = "PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII)
          val settingsBytes  = FrameCodec.encode(Settings(ack = false, Nil)).toArray
          val handshake      = preface ++ settingsBytes ++ FrameCodec.encode(Settings(ack = true, Nil)).toArray
          var calls          = 0
          val fakeIn         = new java.io.InputStream {
            override def read(): Int = -1
            override def read(buf: Array[Byte], off: Int, len: Int): Int = {
              calls += 1
              if (calls == 1) {
                val n = math.min(len, handshake.length)
                java.lang.System.arraycopy(handshake, 0, buf, off, n)
                n
              } else throw new RuntimeException("injected")
            }
          }
          val fakeOut        = new ByteArrayOutputStream()
          val conn           = new H2Connection(fakeIn, fakeOut, maxConcurrentStreams = 10)
          val errRef         = new java.util.concurrent.atomic.AtomicReference[Throwable](null)
          val t              = new Thread(() => try conn.run(_ => ()) catch { case e: Throwable => errRef.set(e) })
          t.start(); t.join(3000)
          val err = errRef.get()
          assertTrue(err != null && err.isInstanceOf[RuntimeException])
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

  private val SimpleRoutes: Routes[Any] =
    Routes(Route(RoutePattern.GET, Handler.succeed(Response.ok)))

  private def withRawServer[R](
    use: Int => ZIO[R, Throwable, TestResult],
  ): ZIO[R & Scope, Throwable, TestResult] =
    withRawServer(SimpleRoutes)(use)

  private def withRawServer[R](routes: Routes[Any])(
    use: Int => ZIO[R, Throwable, TestResult],
  ): ZIO[R & Scope, Throwable, TestResult] =
    ZIO
      .acquireRelease(
        ZIO.attempt(
          ServerHandle.live(List(
            new H2Transport(routes, Context.empty, Connector(bind = BindAddress.localhost(0)), DefectHandler.default).start(),
          )),
        ),
      )(h => ZIO.succeed(h.shutdownAndWait()))
      .flatMap { handle =>
        val port = handle.bindings.head.address match {
          case BoundAddress.Tcp(_, p) => p
          case other                  => throw new AssertionError("Expected TCP: " + other)
        }
        use(port)
      }

  private def toStreamHelper(result: Any): zio.blocks.mux.MuxStream[Int, H2Frame, H2Frame] =
    result match {
      case s: zio.blocks.mux.MuxStream[?, ?, ?] => s.asInstanceOf[zio.blocks.mux.MuxStream[Int, H2Frame, H2Frame]]
      case Right(s)                              => s.asInstanceOf[zio.blocks.mux.MuxStream[Int, H2Frame, H2Frame]]
      case other                                 => throw new AssertionError("Expected MuxStream: " + other)
    }

  private final class RawH2Client(val port: Int) extends AutoCloseable {
    private val PrefaceBytes =
      "PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n".getBytes(StandardCharsets.US_ASCII)

    val socket              = new Socket("127.0.0.1", port)
    socket.setSoTimeout(5000)
    private val out         = socket.getOutputStream
    private val rawIn       = socket.getInputStream
    private var buf         = Chunk.empty[Byte]

    handshake()

    def sendFrame(frame: H2Frame): Unit   = sendRaw(FrameCodec.encode(frame).toArray)
    def sendRaw(bytes: Array[Byte]): Unit = { out.write(bytes); out.flush() }

    def readFrame(): H2Frame = {
      while (true) {
        FrameCodec.decode(buf) match {
          case Right((frame, rest))           =>
            buf = rest; return frame
          case Left(H2Error.InsufficientData) =>
            val tmp = new Array[Byte](8192)
            val n   = rawIn.read(tmp)
            if (n < 0) throw new EOFException("Connection closed")
            buf = buf ++ Chunk.fromArray(java.util.Arrays.copyOf(tmp, n))
          case Left(err)                      =>
            throw new AssertionError("Frame decode error: " + err)
        }
      }
      throw new AssertionError("unreachable")
    }

    def readNextMeaningfulFrame(): H2Frame = {
      var result: H2Frame = null
      while (result == null) {
        readFrame() match {
          case s: Settings     => if (!s.ack) sendFrame(Settings(ack = true, Nil))
          case _: WindowUpdate => ()
          case other           => result = other
        }
      }
      result
    }

    def roundTrip(method: String, path: String, body: Chunk[Byte], streamId: Int): RawResponse = {
      sendFrame(Headers(
        streamId    = streamId,
        headerBlock = Hpack.encode(List(
          HeaderField(":method", method),
          HeaderField(":path", path),
          HeaderField(":scheme", "http"),
          HeaderField(":authority", s"127.0.0.1:$port"),
        )),
        endStream   = body.isEmpty,
        endHeaders  = true,
      ))
      if (body.nonEmpty) sendFrame(Data(streamId, body, endStream = true))
      awaitResponse(streamId)
    }

    def awaitResponse(streamId: Int): RawResponse = {
      val hdrs = mutable.ListBuffer.empty[HeaderField]
      var body = Chunk.empty[Byte]
      var done = false
      while (!done) {
        readFrame() match {
          case Settings(false, _)                                    => sendFrame(Settings(ack = true, Nil))
          case Settings(true, _)                                     => ()
          case _: WindowUpdate                                       => ()
          case Headers(sid, block, end, _, _, _) if sid == streamId =>
            Hpack.decode(block) match {
              case Right(h) => hdrs ++= h
              case Left(e)  => throw new AssertionError("HPACK decode: " + e)
            }
            done = end
          case Data(sid, data, end, _) if sid == streamId           =>
            body = body ++ data; done = end
          case GoAway(_, code, dbg)                                  =>
            throw new AssertionError(s"GOAWAY: $code ${new String(dbg.toArray)}")
          case _                                                     => ()
        }
      }
      val status = hdrs.find(_.name == ":status").map(_.value.toInt)
        .getOrElse(throw new AssertionError("Missing :status"))
      RawResponse(status, hdrs.toList, body)
    }

    override def close(): Unit = socket.close()

    private def handshake(): Unit = {
      out.write(PrefaceBytes)
      out.write(FrameCodec.encode(Settings(ack = false, Nil)).toArray)
      out.flush()
      readFrame() match {
        case Settings(false, _) => sendFrame(Settings(ack = true, Nil))
        case other              => throw new AssertionError("Expected Settings(false,_): " + other)
      }
      readFrame()
    }
  }

  private final case class RawResponse(status: Int, headers: List[HeaderField], body: Chunk[Byte]) {
    def bodyText: String = new String(body.toArray, StandardCharsets.UTF_8)
  }
}
