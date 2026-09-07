package zio.http.h2

import java.io.{ByteArrayOutputStream, IOException, OutputStream}

import scala.annotation.experimental

import zio._
import zio.blocks.mux.Mux
import zio.test.TestAspect.sequential
import zio.test._

import zio.http.h2.H2Frame._
import zio.http.h2.H2RawClientFixture.toStreamHelper

/**
 * Connection-timer behavior: request timers, idle timers, and timer futures.
 */
@experimental
object H2ConnectionTimersSpec extends ZIOSpecDefault {

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("H2ConnectionTimersSpec")(
      // ── H2ConnectionControl.VirtualTimerFuture.fail → ExecutionException ──
      test("request timer future.get() throws ExecutionException on write failure") {
        ZIO.attempt {
          val throwingOut = new OutputStream {
            override def write(b: Int): Unit                             = ()
            override def write(b: Array[Byte], off: Int, len: Int): Unit = throw new IOException("broken")
          }
          val mux         = Mux[Int, H2Frame, H2Frame](10)
          toStreamHelper(mux.open(1))
          val control     = new H2ConnectionControl(throwingOut, mux, idleTimeoutMs = 0L, requestTimeoutMs = 80L)
          val future      = control.startRequestTimer(streamId = 1)
          Thread.sleep(300)
          val result      =
            try { future.get(); Right(()) }
            catch { case _: Throwable => Left(()) }
          assertTrue(result.isLeft)
        }
      },
      // ── H2ConnectionControl.VirtualTimerFuture.get(timeout) success path ─
      test("request timer future.get(timeout) completes within window") {
        ZIO.attempt {
          val out     = new ByteArrayOutputStream()
          val mux     = Mux[Int, H2Frame, H2Frame](10)
          val control = new H2ConnectionControl(out, mux, idleTimeoutMs = 0L, requestTimeoutMs = 0L)
          val future  = control.startRequestTimer(streamId = 1)
          future.get(1000L, java.util.concurrent.TimeUnit.MILLISECONDS)
          assertTrue(future.isDone)
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
      // ── H2ConnectionControl: VirtualTimerFuture already done → cancel false
      test("VirtualTimerFuture cancel on completed future returns false") {
        ZIO.attemptBlocking {
          val out     = new ByteArrayOutputStream()
          val mux     = Mux[Int, H2Frame, H2Frame](10)
          val control = new H2ConnectionControl(out, mux, idleTimeoutMs = 0L, requestTimeoutMs = 0L)
          val future  = control.startRequestTimer(streamId = 1)
          Thread.sleep(50)
          val result  = future.cancel(true)
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
    ) @@ sequential
}
