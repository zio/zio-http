/*
 * Copyright 2021-2023 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.http.netty.client

import zio._

/**
 * A `ZPool[E, A]` is a pool of items of type `A`, each of which may be
 * associated with the acquisition and release of resources. An attempt to get
 * an item `A` from a pool may fail with an error of type `E`.
 */
trait ZPoolTest[+Err, Item] {

  /**
   * Retrieves an item from the pool in a scoped effect. Note that if
   * acquisition fails, then the returned effect will fail for that same reason.
   * Retrying a failed acquisition attempt will repeat the acquisition attempt.
   */
  def get: ZIO[Scope, Err, Item]

  /**
   * Invalidates the specified item. This will cause the pool to eventually
   * reallocate the item, although this reallocation may occur lazily rather
   * than eagerly.
   */
  def invalidate(item: Item): UIO[Unit]
}
object ZPoolTest {

  /**
   * Creates a pool from a fixed number of pre-allocated items. This method
   * should only be used when there is no cleanup or release operation
   * associated with items in the pool. If cleanup or release is required, then
   * the `make` constructor should be used instead.
   */
  def fromIterable[A](iterable: => Iterable[A]): ZIO[Scope, Nothing, ZPoolTest[Nothing, A]] =
    for {
      iterable <- ZIO.succeed(iterable)
      source   <- Ref.make(iterable.toList)
      get =
        if (iterable.isEmpty) ZIO.never
        else
          source.modify {
            case head :: tail => (head, tail)
            case Nil          => throw new IllegalArgumentException("No items in list!")
          }
      pool <- ZPoolTest.make(get, iterable.size)
    } yield pool

  /**
   * Makes a new pool of the specified fixed size. The pool is returned in a
   * `Scope`, which governs the lifetime of the pool. When the pool is shutdown
   * because the `Scope` is closed, the individual items allocated by the pool
   * will be released in some unspecified order.
   */
  def make[R, E, A](get: => ZIO[R, E, A], size: => Int): ZIO[R with Scope, Nothing, ZPoolTest[E, A]] =
    for {
      size <- ZIO.succeed(size)
      pool <- makeWith(get, size to size)(Strategy.None)
    } yield pool

  /**
   * Makes a new pool with the specified minimum and maximum sizes and time to
   * live before a pool whose excess items are not being used will be shrunk
   * down to the minimum size. The pool is returned in a `Scope`, which governs
   * the lifetime of the pool. When the pool is shutdown because the `Scope` is
   * used, the individual items allocated by the pool will be released in some
   * unspecified order.
   * {{{
   * ZIO.scoped {
   *   ZPool.make(acquireDbConnection, 10 to 20, 60.seconds).flatMap { pool =>
   *     ZIO.scoped {
   *       pool.get.flatMap {
   *         connection => useConnection(connection)
   *       }
   *     }
   *   }
   * }
   * }}}
   */
  def make[R, E, A](
    get: => ZIO[R, E, A],
    range: => Range,
    timeToLive: => Duration,
  ): ZIO[R with Scope, Nothing, ZPoolTest[E, A]] =
    makeWith[R, Any, E, A](get, range)(Strategy.TimeToLive(timeToLive))

  /**
   * A more powerful variant of `make` that allows specifying a `Strategy` that
   * describes how a pool whose excess items are not being used will be shrunk
   * down to the minimum size.
   */
  private def makeWith[R, R1, E, A](get: => ZIO[R, E, A], range: => Range)(
    strategy: => Strategy[R1, E, A],
  ): ZIO[R with R1 with Scope, Nothing, ZPoolTest[E, A]] =
    ZIO.uninterruptibleMask { restore =>
      for {
        id       <- ZIO.succeed(scala.util.Random.nextInt())
        _        <- ZIO.debug(s"$id: creating pool")
        _        <- ZIO.debug(s"$id: restore = $restore")
        get      <- ZIO.succeed(get)
        range    <- ZIO.succeed(range)
        strategy <- ZIO.succeed(strategy)
        env      <- ZIO.environment[R]
        down     <- Ref.make(false)
        state    <- Ref.make(State(0, 0))
        items    <- Queue.bounded[Attempted[E, A]](range.end)
        inv      <- Ref.make(Set.empty[A])
        initial  <- strategy.initial
        pool = DefaultPool(
          get.provideSomeEnvironment[Scope](env.union[Scope](_)),
          range,
          down,
          state,
          items,
          inv,
          strategy.track(initial),
        )
        fiber  <- restore(pool.initialize).forkDaemon
        shrink <- strategy.run(initial, pool.excess, pool.shrink).interruptible.forkDaemon
        _      <- ZIO.addFinalizer {
          ZIO.debug(s"$id: beginning finalization of pool") *>
            pool.shutdown *>
            ZIO.debug(s"$id: pool shutdown, beginning interruption of fiber") *>
            fiber.interrupt *>
            ZIO.debug(s"$id: fiber interrupted, beginning interruption of shrink") *>
            shrink.interrupt *>
            ZIO.debug(s"$id: shrink interrupted, finalization complete")
        }
      } yield pool
    }

  private case class Attempted[+E, +A](result: Exit[E, A], finalizer: UIO[Any]) {
    def isFailure: Boolean = result.isFailure

    def forEach[R, E2](f: A => ZIO[R, E2, Any]): ZIO[R, E2, Any] =
      result match {
        case Exit.Failure(_) => ZIO.unit
        case Exit.Success(a) => f(a)
      }

    def toZIO: ZIO[Any, E, A] =
      ZIO.done(result)
  }

  private case class DefaultPool[R, E, A](
    creator: ZIO[Scope, E, A],
    range: Range,
    isShuttingDown: Ref[Boolean],
    state: Ref[State],
    items: Queue[Attempted[E, A]],
    invalidated: Ref[Set[A]],
    track: Exit[E, A] => UIO[Any],
  ) extends ZPoolTest[E, A] {

    /**
     * Returns the number of items in the pool in excess of the minimum size.
     */
    def excess: UIO[Int] =
      state.get.map { case State(free, size) => size - range.start min free }

    def get: ZIO[Scope, E, A] = {

      def acquire: UIO[Attempted[E, A]] =
        isShuttingDown.get.flatMap { down =>
          if (down) ZIO.interrupt
          else
            state.modify { case State(size, free) =>
              if (free > 0 || size >= range.end)
                (
                  items.take.flatMap { attempted =>
                    attempted.result match {
                      case Exit.Success(item) =>
                        invalidated.get.flatMap { set =>
                          if (set.contains(item)) finalizeInvalid(attempted) *> acquire
                          else ZIO.succeedNow(attempted)
                        }
                      case _                  =>
                        ZIO.succeedNow(attempted)
                    }
                  },
                  State(size, free - 1),
                )
              else if (size >= 0)
                allocate *> acquire -> State(size + 1, free + 1)
              else
                ZIO.interrupt       -> State(size, free)
            }.flatten
        }

      def release(attempted: Attempted[E, A]): UIO[Any] =
        attempted.result match {
          case Exit.Success(item) =>
            invalidated.get.flatMap { set =>
              if (set.contains(item)) finalizeInvalid(attempted)
              else
                state.update(state => state.copy(free = state.free + 1)) *>
                  items.offer(attempted) *>
                  track(attempted.result) *>
                  getAndShutdown.whenZIO(isShuttingDown.get)
            }

          case Exit.Failure(_) =>
            state.modify { case State(size, free) =>
              if (size <= range.start)
                allocate -> State(size, free + 1)
              else
                ZIO.unit -> State(size - 1, free)
            }.flatten
        }

      for {
        releaseAndAttempted <- ZIO.acquireRelease(acquire)(release(_)).withEarlyRelease.disconnect
        (release, attempted) = releaseAndAttempted
        _    <- release.when(attempted.isFailure)
        item <- attempted.toZIO
      } yield item
    }

    /**
     * Begins pre-allocating pool entries based on minimum pool size.
     */
    final def initialize: UIO[Unit] =
      ZIO.replicateZIODiscard(range.start) {
        ZIO.uninterruptibleMask { restore =>
          state.modify { case State(size, free) =>
            if (size < range.start && size >= 0)
              (
                for {
                  scope     <- Scope.make
                  exit      <- restore(scope.extend(creator)).exit
                  attempted <- ZIO.succeed(Attempted(exit, scope.close(Exit.succeed(()))))
                  _         <- items.offer(attempted)
                  _         <- track(attempted.result)
                  _         <- getAndShutdown.whenZIO(isShuttingDown.get)
                } yield attempted,
                State(size + 1, free + 1),
              )
            else
              ZIO.unit -> State(size, free)
          }.flatten
        }
      }

    def invalidate(item: A): UIO[Unit] =
      invalidated.update(_ + item)

    private def finalizeInvalid(attempted: Attempted[E, A]): UIO[Any] =
      attempted.forEach(a => invalidated.update(_ - a)) *>
        attempted.finalizer *>
        state.modify { case State(size, free) =>
          if (size <= range.start)
            allocate -> State(size, free + 1)
          else
            ZIO.unit -> State(size - 1, free)
        }.flatten

    /**
     * Shrinks the pool down, but never to less than the minimum size.
     */
    def shrink: UIO[Any] =
      ZIO.uninterruptible {
        state.modify { case State(size, free) =>
          if (size > range.start && free > 0)
            (
              items.take.flatMap { attempted =>
                attempted.forEach(a => invalidated.update(_ - a)) *>
                  attempted.finalizer *>
                  state.update(state => state.copy(size = state.size - 1))
              },
              State(size, free - 1),
            )
          else
            ZIO.unit -> State(size, free)
        }.flatten
      }

    private def allocate: UIO[Any] =
      ZIO.uninterruptibleMask { restore =>
        for {
          scope     <- Scope.make
          exit      <- restore(scope.extend(creator)).exit
          attempted <- ZIO.succeed(Attempted(exit, scope.close(Exit.succeed(()))))
          _         <- items.offer(attempted)
          _         <- track(attempted.result)
          _         <- getAndShutdown.whenZIO(isShuttingDown.get)
        } yield attempted
      }

    /**
     * Gets items from the pool and shuts them down as long as there are items
     * free, signalling shutdown of the pool if the pool is empty.
     */
    private def getAndShutdown: UIO[Unit] =
      state.modify { case State(size, free) =>
        if (free > 0)
          (
            items.take.foldCauseZIO(
              _ => ZIO.unit,
              attempted =>
                attempted.forEach(a => invalidated.update(_ - a)) *>
                  attempted.finalizer *>
                  state.update(state => state.copy(size = state.size - 1)) *>
                  getAndShutdown,
            ),
            State(size, free - 1),
          )
        else if (size > 0)
          ZIO.unit       -> State(size, free)
        else
          items.shutdown -> State(size - 1, free)
      }.flatten

    final def shutdown: UIO[Unit] =
      isShuttingDown.modify { down =>
        if (down)
          ZIO.unit                   -> true
        else
          getAndShutdown *> ZIO.unit -> true
      }.flatten
  }

  private case class State(size: Int, free: Int)

  /**
   * A `Strategy` describes the protocol for how a pool whose excess items are
   * not being used should shrink down to the minimum pool size.
   */
  private trait Strategy[-Environment, -Err, -Item] {

    /**
     * Describes the type of the state maintained by the strategy.
     */
    type State

    /**
     * Describes how the initial state of the strategy should be allocated.
     */
    def initial: URIO[Environment, State]

    /**
     * Describes how the state of the strategy should be updated when an item is
     * added to the pool or returned to the pool.
     */
    def track(state: State)(item: Exit[Err, Item]): UIO[Unit]

    /**
     * Describes how excess items that are not being used should shrink down.
     */
    def run(state: State, getExcess: UIO[Int], shrink: UIO[Any]): UIO[Unit]
  }

  private object Strategy {

    /**
     * A strategy that does nothing to shrink excess items. This is useful when
     * the minimum size of the pool is equal to its maximum size and so there is
     * nothing to do.
     */
    case object None extends Strategy[Any, Any, Any] {
      type State = Any
      def initial: UIO[Any]                                                 =
        ZIO.unit
      def track(state: Any)(attempted: Exit[Any, Any]): UIO[Unit]           =
        ZIO.unit
      def run(state: Any, getExcess: UIO[Int], shrink: UIO[Any]): UIO[Unit] =
        ZIO.unit
    }

    /**
     * A strategy that shrinks the pool down to its minimum size if items in the
     * pool have not been used for the specified duration.
     */
    final case class TimeToLive(timeToLive: Duration) extends Strategy[Any, Any, Any] {
      type State = (Clock, Ref[java.time.Instant])
      def initial: UIO[State]                                                                           =
        for {
          clock <- ZIO.clock
          now   <- Clock.instant
          ref   <- Ref.make(now)
        } yield (clock, ref)
      def track(state: (Clock, Ref[java.time.Instant]))(attempted: Exit[Any, Any]): UIO[Unit]           = {
        val (clock, ref) = state
        for {
          now <- clock.instant
          _   <- ref.set(now)
        } yield ()
      }
      def run(state: (Clock, Ref[java.time.Instant]), getExcess: UIO[Int], shrink: UIO[Any]): UIO[Unit] = {
        val (clock, ref) = state
        getExcess.flatMap { excess =>
          if (excess <= 0)
            clock.sleep(timeToLive) *> run(state, getExcess, shrink)
          else
            ref.get.zip(clock.instant).flatMap { case (start, end) =>
              val duration = java.time.Duration.between(start, end)
              if (duration >= timeToLive) shrink *> run(state, getExcess, shrink)
              else clock.sleep(timeToLive) *> run(state, getExcess, shrink)
            }
        }
      }
    }
  }

}
