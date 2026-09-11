package zio.http

import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

import scala.jdk.CollectionConverters._

/**
 * Lifecycle states of an aggregate (multi-engine) server handle.
 *
 * The terminal transition order is fixed: `Running` -> `StopAccepting` ->
 * `Draining` -> (`ForceClosing` only when the drain deadline expires for at
 * least one engine) -> `Terminated`.
 */
sealed trait AggregateLifecycleState
object AggregateLifecycleState {
  case object Running       extends AggregateLifecycleState
  case object StopAccepting extends AggregateLifecycleState
  case object Draining      extends AggregateLifecycleState
  case object ForceClosing  extends AggregateLifecycleState
  case object Terminated    extends AggregateLifecycleState
}

/**
 * One bound protocol engine managed by an [[AggregateServerHandle]].
 *
 * This is scaffolding for the lifecycle contract (Todo 5). Concrete engines
 * (H1/H2/...) are wired in Todo 8; this trait only fixes the shutdown
 * vocabulary every engine must speak.
 */
trait LifecycleEngine {

  /** Stable engine name, used in diagnostics. */
  def name: String

  /** Stop accepting new work and begin draining in-flight work. */
  def requestStop(): Unit

  /**
   * Wait up to `timeout` for in-flight work to drain. Returns true when the
   * engine drained in time. Implementations should honor interrupts.
   */
  def awaitDrain(timeout: Duration): Boolean

  /** Force-close whatever remains after the drain deadline. */
  def forceClose(): Unit
}

/**
 * Protocol-independent aggregate server handle (lifecycle scaffolding).
 *
 * Semantics locked by `AggregateLifecycleSpec`:
 *   - `shutdown` runs exactly once (repeated/concurrent calls are safe):
 *     request stop on every engine, drain all engines in parallel bounded by
 *     `drainTimeout`, force-close only the engines that missed the deadline,
 *     join owned threads, then reach `Terminated`.
 *   - `awaitShutdown` truly blocks until `Terminated`.
 *   - Engine errors never prevent termination; they are recorded in
 *     `drainErrors` (rollback close errors are attached as suppressed to the
 *     propagated bind failure instead).
 *   - Shutdown issued from an owned thread skips that thread's join
 *     (`skippedSelfJoins`) instead of deadlocking on a self-join.
 *
 * Deliberately NOT a [[ServerHandle]] (which is sealed to its own file): Todo 8
 * unifies the two once real engines are wired.
 */
final class AggregateServerHandle private (
  engines: List[LifecycleEngine],
  ownedThreads: List[Thread],
  drainTimeout: Duration,
  ownedJoinTimeout: Duration,
) {
  import AggregateLifecycleState._

  private val stateRef         = new AtomicReference[AggregateLifecycleState](Running)
  private val shutdownStarted  = new AtomicBoolean(false)
  private val terminal         = new CountDownLatch(1)
  private val errors           = new ConcurrentLinkedQueue[Throwable]()
  private val selfJoinsSkipped = new AtomicInteger(0)

  /** Current lifecycle state. */
  def state: AggregateLifecycleState = stateRef.get()

  /** True until the handle reaches `Terminated`. */
  def isRunning: Boolean = state != Terminated

  /** Number of owned-thread self-joins skipped by `shutdown`. */
  def skippedSelfJoins: Int = selfJoinsSkipped.get()

  /** Engine errors observed during the last shutdown, if any. */
  def drainErrors: List[Throwable] = errors.asScala.toList

  /** Begin the terminal transition. Idempotent and thread-safe. */
  def shutdown(): Unit =
    if (shutdownStarted.compareAndSet(false, true)) {
      try {
        stateRef.set(StopAccepting)
        engines.foreach(stopQuietly)
        stateRef.set(Draining)
        val drained = drainParallel()
        if (drained.exists(!_)) {
          stateRef.set(ForceClosing)
          engines.zip(drained).foreach { case (engine, ok) =>
            if (!ok) closeQuietly(engine)
          }
        }
        joinOwnedThreads()
      } finally {
        stateRef.set(Terminated)
        terminal.countDown()
      }
    }

  /**
   * Block until the handle reaches `Terminated`. Returns at once when the
   * handle is already terminated.
   */
  def awaitShutdown(): Unit = {
    terminal.await()
    ()
  }

  /** Block up to `timeout` for the terminal state. */
  def awaitShutdown(timeout: Duration): Boolean =
    terminal.await(timeout.toMillis, TimeUnit.MILLISECONDS)

  /** `shutdown` followed by a blocking `awaitShutdown`. */
  def shutdownAndWait(): Unit = {
    shutdown()
    awaitShutdown()
  }

  /** Alias for `shutdownAndWait`. */
  def close(): Unit = shutdownAndWait()

  private def stopQuietly(engine: LifecycleEngine): Unit =
    try engine.requestStop()
    catch { case error: Throwable => errors.add(error) }

  private def closeQuietly(engine: LifecycleEngine): Unit =
    try engine.forceClose()
    catch { case error: Throwable => errors.add(error) }

  /**
   * Drain every engine concurrently, bounded by `drainTimeout` plus a grace
   * period for misbehaving engines. Returns per-engine drain outcomes.
   */
  private def drainParallel(): List[Boolean] = {
    val outcomes   = new ConcurrentHashMap[LifecycleEngine, Boolean]()
    val workers    = engines.map { engine =>
      Thread
        .ofVirtual()
        .start(() => {
          try outcomes.put(engine, engine.awaitDrain(drainTimeout))
          catch {
            case error: Throwable =>
              errors.add(error)
              outcomes.put(engine, false)
          }
        })
    }
    val joinMillis = drainTimeout.toMillis + AggregateServerHandle.DrainJoinGraceMillis
    workers.foreach(_.join(joinMillis))
    workers.filter(_.isAlive).foreach(_.interrupt())
    engines.map(engine => outcomes.getOrDefault(engine, false))
  }

  /**
   * Join owned threads (e.g. acceptor loops) so `awaitShutdown` only returns
   * once they have exited. Never joins the calling thread; interrupts owned
   * threads that ignore the join timeout instead of hanging shutdown.
   */
  private def joinOwnedThreads(): Unit =
    ownedThreads.foreach { thread =>
      if (thread eq Thread.currentThread()) selfJoinsSkipped.incrementAndGet()
      else {
        thread.join(ownedJoinTimeout.toMillis)
        if (thread.isAlive) thread.interrupt()
      }
      ()
    }
}

object AggregateServerHandle {
  private val DrainJoinGraceMillis = 2000L

  val DefaultDrainTimeout: Duration     = Duration.ofSeconds(30)
  val DefaultOwnedJoinTimeout: Duration = Duration.ofSeconds(10)

  /** Wrap already-bound engines. */
  def live(
    engines: List[LifecycleEngine],
    ownedThreads: List[Thread] = Nil,
    drainTimeout: Duration = DefaultDrainTimeout,
    ownedJoinTimeout: Duration = DefaultOwnedJoinTimeout,
  ): AggregateServerHandle =
    new AggregateServerHandle(engines, ownedThreads, drainTimeout, ownedJoinTimeout)

  /**
   * Bind engines in order; when a binder fails, force-close the engines bound
   * so far in reverse order, attach rollback errors as suppressed, and rethrow
   * the bind failure. Nothing is leaked on partial startup.
   */
  def start(
    binders: List[() => LifecycleEngine],
    ownedThreads: List[Thread] = Nil,
    drainTimeout: Duration = DefaultDrainTimeout,
    ownedJoinTimeout: Duration = DefaultOwnedJoinTimeout,
  ): AggregateServerHandle = {
    val bound = List.newBuilder[LifecycleEngine]
    try binders.foreach(binder => bound += binder())
    catch {
      case bindFailure: Throwable =>
        bound.result().reverse.foreach { engine =>
          try engine.forceClose()
          catch { case rollbackFailure: Throwable => bindFailure.addSuppressed(rollbackFailure) }
        }
        throw bindFailure
    }
    live(bound.result(), ownedThreads, drainTimeout, ownedJoinTimeout)
  }
}
