package zio.http

import java.time.Duration
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

import scala.jdk.CollectionConverters._

import zio._
import zio.test._

/**
 * Lifecycle contract for the protocol-independent aggregate server handle.
 *
 * Locked semantics (Todo 5; engine wiring itself is Todo 8):
 *   - Running -> StopAccepting -> Draining -> (ForceClosing) -> Terminated.
 *   - Engines drain in parallel; a drain deadline force-closes only stuck
 *     engines.
 *   - `awaitShutdown` truly blocks until the terminal state.
 *   - `shutdown` is idempotent under repeated and concurrent calls.
 *   - Partial startup rolls back already-bound engines in reverse order.
 *   - The handle keeps no thread registry: each engine terminates its own
 *     threads behind requestStop/awaitDrain/forceClose.
 *
 * Every coordination point uses latches/barriers with bounded waits; no
 * sleep-polling anywhere on these paths.
 */
object AggregateLifecycleSpec extends ZIOSpecDefault {
  import AggregateLifecycleState._

  private val ShortWait = Duration.ofMillis(300)
  private val LongWait  = Duration.ofSeconds(15)

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("AggregateLifecycleSpec")(
      test("fresh handle is Running") {
        val handle = AggregateServerHandle.live(List(fakeEngine("e1"), fakeEngine("e2")))
        ZIO.attempt((handle.state, handle.isRunning)).map { case (state, running) =>
          assertTrue(state == Running, running)
        }
      },
      test("fast engines traverse stop, drain, terminated with no force close") {
        val log    = new ConcurrentLinkedQueue[String]()
        val handle = AggregateServerHandle.live(
          List(fakeEngine("e1", log = log), fakeEngine("e2", log = log)),
        )
        for {
          _ <- ZIO.attemptBlocking(handle.close())
          snapshot = log.asScala.toList
        } yield assertTrue(
          handle.state == Terminated,
          !handle.isRunning,
          snapshot.count(_.startsWith("stop-")) == 2,
          snapshot.count(_.startsWith("drain-")) == 2,
          snapshot.count(_.startsWith("close-")) == 0,
        )
      },
      test("stop-accepting is observable before drain completes") {
        val stopSeen = new CountDownLatch(1)
        val proceed  = new CountDownLatch(1)
        val engine   = new LifecycleEngine {
          val name                                   = "gated-stop"
          def requestStop(): Unit                    = {
            stopSeen.countDown()
            proceed.await(30, TimeUnit.SECONDS)
            ()
          }
          def awaitDrain(timeout: Duration): Boolean = true
          def forceClose(): Unit                     = ()
        }
        val handle   = AggregateServerHandle.live(List(engine))
        for {
          bg    <- ZIO.attemptBlocking(Thread.ofVirtual().start(() => handle.shutdown())).orDie
          _     <- ZIO.attemptBlocking(stopSeen.await(15, TimeUnit.SECONDS)).map(seen => assertTrue(seen))
          state <- ZIO.attempt(handle.state)
          _     <- ZIO.attempt(proceed.countDown())
          _     <- ZIO.attemptBlocking(bg.join(15000))
          _     <- ZIO.attemptBlocking(handle.awaitShutdown())
        } yield assertTrue(state == StopAccepting, handle.state == Terminated)
      },
      test("engines drain in parallel, not sequentially") {
        val entered                                             = new CountDownLatch(2)
        val release                                             = new CountDownLatch(1)
        def blockingEngine(engineName: String): LifecycleEngine = new LifecycleEngine {
          def name: String                           = engineName
          def requestStop(): Unit                    = ()
          def awaitDrain(timeout: Duration): Boolean = {
            entered.countDown()
            release.await(30, TimeUnit.SECONDS)
            true
          }
          def forceClose(): Unit                     = ()
        }
        val handle = AggregateServerHandle.live(List(blockingEngine("a"), blockingEngine("b")))
        for {
          bg         <- ZIO.attemptBlocking(Thread.ofVirtual().start(() => handle.shutdown())).orDie
          // Both engines must be inside awaitDrain at the same time. A sequential
          // drain would wedge here: the first engine blocks on `release` while the
          // second never enters, so `entered` could never reach zero.
          bothInside <- ZIO.attemptBlocking(entered.await(15, TimeUnit.SECONDS))
          _          <- ZIO.attempt(release.countDown())
          _          <- ZIO.attemptBlocking(bg.join(15000))
        } yield assertTrue(bothInside, handle.state == Terminated)
      },
      test("drain deadline force-closes only the stuck engine") {
        val entered         = new CountDownLatch(1)
        val stuckCloseCount = new AtomicInteger(0)
        val stuck           = new LifecycleEngine {
          val name                                   = "stuck"
          def requestStop(): Unit                    = ()
          // Deliberately ignores the deadline: the handle must enforce it by
          // interrupting this drain and force-closing. Interruptible, so no
          // lingering thread after the test.
          def awaitDrain(timeout: Duration): Boolean = {
            entered.countDown()
            Thread.sleep(30000)
            true
          }
          def forceClose(): Unit                     = {
            stuckCloseCount.incrementAndGet()
            throw new RuntimeException("close failed: stuck")
          }
        }
        val fast            = fakeEngine("fast")
        val handle          = AggregateServerHandle.live(
          List(stuck, fast),
          drainTimeout = Duration.ofMillis(400),
        )
        for {
          _ <- ZIO.attemptBlocking(handle.shutdownAndWait())
        } yield assertTrue(
          handle.state == Terminated,
          stuckCloseCount.get() == 1,
          fast.closes.get() == 0,
          handle.drainErrors.exists(_.getMessage == "close failed: stuck"),
        )
      },
      test("awaitShutdown blocks until shutdown completes, then returns at once") {
        val engine   = fakeEngine("e1")
        val handle   = AggregateServerHandle.live(List(engine))
        val entered  = new CountDownLatch(1)
        val returned = new CountDownLatch(1)
        for {
          _          <- ZIO.attemptBlocking {
            Thread
              .ofVirtual()
              .start(() => {
                entered.countDown()
                handle.awaitShutdown()
                returned.countDown()
              })
            ()
          }
          _          <- ZIO.attemptBlocking(entered.await(15, TimeUnit.SECONDS))
          timedOut   <- ZIO.attemptBlocking(returned.await(300, TimeUnit.MILLISECONDS))
          timedFalse <- ZIO.attemptBlocking(handle.awaitShutdown(ShortWait))
          _          <- ZIO.attempt(handle.shutdown())
          completed  <- ZIO.attemptBlocking(returned.await(15, TimeUnit.SECONDS))
          timedTrue  <- ZIO.attemptBlocking(handle.awaitShutdown(LongWait))
          _          <- ZIO.attemptBlocking(handle.awaitShutdown())
        } yield assertTrue(!timedOut, !timedFalse, completed, timedTrue)
      },
      test("repeated and concurrent shutdown executes exactly once") {
        val log    = new ConcurrentLinkedQueue[String]()
        val engine = fakeEngine("e1", log = log)
        val handle = AggregateServerHandle.live(List(engine))
        val gate   = new CountDownLatch(1)
        val done   = new CountDownLatch(8)
        for {
          joined <- ZIO.attemptBlocking {
            (1 to 8).foreach { _ =>
              Thread
                .ofVirtual()
                .start(() => {
                  gate.await(30, TimeUnit.SECONDS)
                  handle.shutdown()
                  done.countDown()
                  ()
                })
            }
            gate.countDown()
            done.await(30, TimeUnit.SECONDS)
          }
          _      <- ZIO.attemptBlocking(handle.awaitShutdown())
        } yield assertTrue(
          joined,
          engine.stops.get() == 1,
          engine.drains.get() == 1,
          engine.closes.get() == 0,
          handle.state == Terminated,
        )
      },
      test("start rolls back bound engines in reverse order on bind failure") {
        val log                                  = new ConcurrentLinkedQueue[String]()
        val binders: List[() => LifecycleEngine] = List(
          () => fakeEngine("e1", log = log),
          () => fakeEngine("e2", log = log),
          () => throw new RuntimeException("bind e3 failed"),
        )
        for {
          failure <- ZIO.attempt(AggregateServerHandle.start(binders)).either
        } yield assertTrue(
          failure.isLeft,
          failure.left.toOption.map(_.getMessage).contains("bind e3 failed"),
          log.asScala.toList == List("close-e2", "close-e1"),
        )
      },
      test("start binds all engines and returns a Running handle") {
        val log    = new ConcurrentLinkedQueue[String]()
        val handle = AggregateServerHandle.start(
          List(() => fakeEngine("e1", log = log), () => fakeEngine("e2", log = log)),
        )
        for {
          running <- ZIO.attempt(handle.isRunning)
          _       <- ZIO.attemptBlocking(handle.shutdownAndWait())
        } yield assertTrue(
          running,
          handle.state == AggregateLifecycleState.Terminated,
          log.asScala.toList.count(_.startsWith("close-")) == 0,
        )
      },
      test("start with a first-binder failure closes nothing") {
        val log                                  = new ConcurrentLinkedQueue[String]()
        val binders: List[() => LifecycleEngine] = List(
          () => throw new RuntimeException("bind e1 failed"),
          () => fakeEngine("e2", log = log),
        )
        for {
          failure <- ZIO.attempt(AggregateServerHandle.start(binders)).either
        } yield assertTrue(
          failure.isLeft,
          log.asScala.toList.isEmpty,
        )
      },
      test("rollback continues past a throwing close and suppresses it") {
        val e1                                   = fakeEngine("e1", failOnClose = true)
        val e2                                   = fakeEngine("e2")
        val binders: List[() => LifecycleEngine] = List(
          () => e1,
          () => e2,
          () => throw new RuntimeException("bind e3 failed"),
        )
        for {
          failure <- ZIO.attempt(AggregateServerHandle.start(binders)).either
        } yield assertTrue(
          failure.isLeft,
          e1.closes.get() == 1,
          e2.closes.get() == 1,
          failure.left.toOption.exists(_.getSuppressed.length == 1),
        )
      },
      test("engine stop failure is recorded but termination is still reached") {
        val bad    = fakeEngine("bad", failOnStop = true)
        val good   = fakeEngine("good")
        val handle = AggregateServerHandle.live(List(bad, good))
        for {
          _ <- ZIO.attemptBlocking(handle.shutdownAndWait())
        } yield assertTrue(
          handle.state == Terminated,
          handle.drainErrors.size == 1,
          good.drains.get() == 1,
        )
      },
      test("aggregate keeps no thread registry: shutdown delegates solely through LifecycleEngine") {
        // An engine owns a worker thread the way a Loom acceptor loop would:
        // it terminates the thread inside its own requestStop, and the handle
        // never sees the thread. Shutdown must still reach Terminated with the
        // worker gone, proving thread termination lives entirely behind
        // LifecycleEngine.
        val workerGate   = new CountDownLatch(1)
        val workerExited = new CountDownLatch(1)
        val worker       = Thread
          .ofVirtual()
          .start(() => {
            try workerGate.await(30, TimeUnit.SECONDS)
            catch { case _: InterruptedException => () }
            finally workerExited.countDown()
          })
        val engine       = new LifecycleEngine {
          val name                                   = "self-owning-engine"
          def requestStop(): Unit                    = {
            workerGate.countDown()
            worker.join(15000)
            ()
          }
          def awaitDrain(timeout: Duration): Boolean = !worker.isAlive
          def forceClose(): Unit                     = ()
        }
        val handle       = AggregateServerHandle.live(List(engine))
        for {
          _      <- ZIO.attemptBlocking(handle.shutdownAndWait())
          exited <- ZIO.attemptBlocking(workerExited.await(15, TimeUnit.SECONDS))
        } yield assertTrue(
          handle.state == Terminated,
          exited,
          !worker.isAlive,
          hasNoThreadRegistry,
        )
      },
    )

  /**
   * Structural half of the ownership contract: the aggregate handle must not
   * maintain a second list of threads (nor self-join bookkeeping for one).
   * Thread termination is owned entirely by each LifecycleEngine/listener
   * implementation behind requestStop/awaitDrain/forceClose.
   */
  private def hasNoThreadRegistry: Boolean = {
    val banned      = Set(
      "ownedThreads",
      "ownedJoinTimeout",
      "selfJoinsSkipped",
      "joinOwnedThreads",
      "skippedSelfJoins",
    )
    val fieldNames  = classOf[AggregateServerHandle].getDeclaredFields.map(_.getName).toSet
    val methodNames = classOf[AggregateServerHandle].getDeclaredMethods.map(_.getName).toSet
    (fieldNames ++ methodNames).intersect(banned).isEmpty
  }

  private final class FakeEngine(
    val name: String,
    stopLatch: CountDownLatch = new CountDownLatch(0),
    drainRelease: CountDownLatch = new CountDownLatch(0),
    drainResult: Boolean = true,
    failOnStop: Boolean = false,
    failOnClose: Boolean = false,
    log: ConcurrentLinkedQueue[String] = new ConcurrentLinkedQueue[String](),
  ) extends LifecycleEngine {
    val stops  = new AtomicInteger(0)
    val drains = new AtomicInteger(0)
    val closes = new AtomicInteger(0)

    def requestStop(): Unit = {
      stops.incrementAndGet()
      log.add("stop-" + name)
      stopLatch.countDown()
      if (failOnStop) throw new RuntimeException("stop failed: " + name)
    }

    def awaitDrain(timeout: Duration): Boolean = {
      drains.incrementAndGet()
      log.add("drain-" + name)
      drainRelease.await(30, TimeUnit.SECONDS)
      log.add("drained-" + name)
      drainResult
    }

    def forceClose(): Unit = {
      closes.incrementAndGet()
      log.add("close-" + name)
      if (failOnClose) throw new RuntimeException("close failed: " + name)
    }
  }

  private def fakeEngine(
    name: String,
    stopLatch: CountDownLatch = new CountDownLatch(0),
    drainRelease: CountDownLatch = new CountDownLatch(0),
    drainResult: Boolean = true,
    failOnStop: Boolean = false,
    failOnClose: Boolean = false,
    log: ConcurrentLinkedQueue[String] = new ConcurrentLinkedQueue[String](),
  ): FakeEngine =
    new FakeEngine(name, stopLatch, drainRelease, drainResult, failOnStop, failOnClose, log)
}
