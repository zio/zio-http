package zio.http.h2

import scala.annotation.experimental

import zio._
import zio.test.TestAspect.sequential
import zio.test._

/**
 * Flow-control send-window behavior: blocking, wake-ups, and waiter signaling.
 */
@experimental
object H2FlowControlSpec extends ZIOSpecDefault {

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("H2FlowControlSpec")(
      // ── FlowController.ensureStreamRegistered throws when stream removed ──
      test("consumeSendWindow throws when stream removed while blocked") {
        ZIO.attempt {
          val fc            = new FlowController(initialConnectionWindow = 0, initialStreamWindow = 0)
          fc.registerStream(1)
          val removerThread = Thread
            .ofVirtual()
            .start(() => {
              Thread.sleep(30)
              fc.removeStream(1)
              fc.applyWindowUpdate(0, 100)
            })
          val result        =
            try { fc.consumeSendWindow(1, 1); Right(()) }
            catch { case e: Throwable => Left(e) }
          removerThread.join(2000)
          assertTrue(result.isLeft)
        }
      },
      // ── FlowController.signalAllStreams via connection WindowUpdate ────────
      test("applyWindowUpdate on streamId=0 signals all registered streams") {
        ZIO.attemptBlocking {
          val fc        = new FlowController(initialConnectionWindow = 0, initialStreamWindow = 100)
          fc.registerStream(1)
          fc.registerStream(3)
          var unblocked = false
          val t         = Thread
            .ofVirtual()
            .start(() => {
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
      // ── FlowController: consumeSendWindow blocks on stream window only ────
      test("consumeSendWindow blocks when only stream window is exhausted") {
        ZIO.attemptBlocking {
          val fc        = new FlowController(initialConnectionWindow = 1000, initialStreamWindow = 10)
          fc.registerStream(1)
          fc.consumeSendWindow(1, 10)
          var unblocked = false
          val t         = Thread
            .ofVirtual()
            .start(() => {
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
      // ── FlowController: signalAllStreams with multiple streams ─────────────
      test("applyWindowUpdate connection broadcasts to all streams") {
        ZIO.attemptBlocking {
          val fc      = new FlowController(initialConnectionWindow = 0, initialStreamWindow = 1000)
          fc.registerStream(1)
          fc.registerStream(3)
          fc.registerStream(5)
          var count   = 0
          val threads = (1 to 3).map { i =>
            Thread
              .ofVirtual()
              .start(() => {
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
          val fc    = new FlowController(initialConnectionWindow = 0, initialStreamWindow = 10)
          fc.registerStream(1)
          var threw = false
          val t     = Thread
            .ofVirtual()
            .start(() => {
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
          val fc        = new FlowController(initialConnectionWindow = 1000, initialStreamWindow = 0)
          fc.registerStream(1)
          var unblocked = false
          val t         = Thread
            .ofVirtual()
            .start(() => {
              try { fc.consumeSendWindow(1, 1); unblocked = true }
              catch { case _: Throwable => () }
            })
          Thread.sleep(30)
          fc.applyWindowUpdate(1, 100)
          t.join(2000)
          assertTrue(unblocked)
        }
      },
    ) @@ sequential
}
