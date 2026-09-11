package zio.http

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

import zio._
import zio.test._

/**
 * Baseline characterization of the existing [[ServerHandle]] close path.
 *
 * This spec locks the CURRENT (Wave 0) behavior so later lifecycle work can
 * change it deliberately rather than accidentally:
 *   - `shutdown` is idempotent (connector `close0` runs exactly once).
 *   - `awaitShutdown` returns immediately (it is currently a no-op).
 *   - `isRunning` flips from true to false after `shutdown`.
 *   - `close` / `shutdownAndWait` behave like `shutdown` + `awaitShutdown`.
 */
object AggregateLifecycleBaselineSpec extends ZIOSpecDefault {
  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("AggregateLifecycleBaselineSpec")(
      test("live handle reports running before shutdown") {
        val (handle, _) = liveHandle()
        ZIO.attempt(handle.isRunning).map(running => assertTrue(running))
      },
      test("shutdown flips isRunning to false") {
        val (handle, _) = liveHandle()
        for {
          _       <- ZIO.attempt(handle.shutdown())
          running <- ZIO.attempt(handle.isRunning)
        } yield assertTrue(!running)
      },
      test("shutdown is idempotent: connector close runs exactly once") {
        val (handle, closes) = liveHandle()
        for {
          _ <- ZIO.attempt(handle.shutdown())
          _ <- ZIO.attempt(handle.shutdown())
          _ <- ZIO.attempt(handle.shutdownAndWait())
          _ <- ZIO.attempt(handle.close())
        } yield assertTrue(closes.get() == 1)
      },
      test("awaitShutdown returns immediately without a prior shutdown") {
        val (handle, _) = liveHandle()
        // No latch, no blocking: the call itself must simply return.
        ZIO.attempt(handle.awaitShutdown()).map(_ => assertTrue(true))
      },
      test("concurrent shutdown calls still close exactly once") {
        val (handle, closes) = liveHandle()
        val startGate        = new CountDownLatch(1)
        val done             = new CountDownLatch(8)
        for {
          _ <- ZIO.attemptBlocking {
            (1 to 8).foreach { _ =>
              Thread
                .ofVirtual()
                .start(() => {
                  startGate.await(30, TimeUnit.SECONDS)
                  handle.shutdown()
                  done.countDown()
                })
            }
            startGate.countDown()
            done.await(30, TimeUnit.SECONDS)
          }.map(joined => assertTrue(joined))
        } yield assertTrue(closes.get() == 1)
      },
      test("bindings are passed through from the bound connectors") {
        val (handle, _) = liveHandle()
        ZIO
          .attempt(handle.bindings)
          .map(bindings =>
            assertTrue(
              bindings.size == 1,
              bindings.head.address == BoundAddress.Tcp("127.0.0.1", 18080),
            ),
          )
      },
    )

  private def liveHandle(): (ServerHandle, AtomicInteger) = {
    val closes  = new AtomicInteger(0)
    val running = new AtomicBoolean(true)
    val bound   = BoundConnectorHandle(
      BoundConnector(BoundAddress.Tcp("127.0.0.1", 18080), Protocol.H2C()),
      () => {
        closes.incrementAndGet()
        running.set(false)
        ()
      },
      () => running.get(),
    )
    (ServerHandle.live(List(bound)), closes)
  }
}
