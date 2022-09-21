package zio.http.internal

import zio._

import java.util.concurrent.ConcurrentHashMap

trait ZKeyedPool[+Err, -Key, Item] {

  /**
   * Retrieves an item from the pool belonging to the given key in a scoped
   * effect. Note that if acquisition fails, then the returned effect will fail
   * for that same reason. Retrying a failed acquisition attempt will repeat the
   * acquisition attempt.
   */
  def get(key: Key)(implicit trace: Trace): ZIO[Scope, Err, Item]

  /**
   * Invalidates the specified item. This will cause the pool to eventually
   * reallocate the item, although this reallocation may occur lazily rather
   * than eagerly.
   */
  def invalidate(item: Item)(implicit trace: Trace): UIO[Unit]

}

object ZKeyedPool {

  /**
   * Makes a new pool of the specified fixed size. The pool is returned in a
   * `Scope`, which governs the lifetime of the pool. When the pool is shutdown
   * because the `Scope` is closed, the individual items allocated by the pool
   * will be released in some unspecified order.
   */
  def make[Key, Env: EnvironmentTag, Err, Item](get: Key => ZIO[Env, Err, Item], size: => Int)(implicit
    trace: Trace,
  ): ZIO[Env with Scope, Nothing, ZKeyedPool[Err, Key, Item]] =
    make(get, _ => size)

  /**
   * Makes a new pool of the specified fixed size. The pool is returned in a
   * `Scope`, which governs the lifetime of the pool. When the pool is shutdown
   * because the `Scope` is closed, the individual items allocated by the pool
   * will be released in some unspecified order.
   *
   * The size of the underlying pools can be configured per key.
   */
  def make[Key, Env: EnvironmentTag, Err, Item](get: Key => ZIO[Env, Err, Item], size: Key => Int)(implicit
    trace: Trace,
  ): ZIO[Env with Scope, Nothing, ZKeyedPool[Err, Key, Item]] = {
    makeWith(get, (key: Key) => { val s = size(key); s to s })(_ => None)
  }

  /**
   * Makes a new pool with the specified minimum and maximum sizes and time to
   * live before a pool whose excess items are not being used will be shrunk
   * down to the minimum size. The pool is returned in a `Scope`, which governs
   * the lifetime of the pool. When the pool is shutdown because the `Scope` is
   * used, the individual items allocated by the pool will be released in some
   * unspecified order.
   */
  def make[Key, Env: EnvironmentTag, Err, Item](
    get: Key => ZIO[Env, Err, Item],
    range: => Range,
    timeToLive: => Duration,
  )(implicit
    trace: Trace,
  ): ZIO[Env with Scope, Nothing, ZKeyedPool[Err, Key, Item]] =
    make(get, _ => range, _ => timeToLive)

  /**
   * Makes a new pool with the specified minimum and maximum sizes and time to
   * live before a pool whose excess items are not being used will be shrunk
   * down to the minimum size. The pool is returned in a `Scope`, which governs
   * the lifetime of the pool. When the pool is shutdown because the `Scope` is
   * used, the individual items allocated by the pool will be released in some
   * unspecified order.
   *
   * The size of the underlying pools can be configured per key.
   */
  def make[Key, Env: EnvironmentTag, Err, Item](
    get: Key => ZIO[Env, Err, Item],
    range: Key => Range,
    timeToLive: Key => Duration,
  )(implicit
    trace: Trace,
  ): ZIO[Env with Scope, Nothing, ZKeyedPool[Err, Key, Item]] =
    makeWith(get, range)((key: Key) => Some(timeToLive(key)))

  private def makeWith[Key, Env: EnvironmentTag, Err, Item](get: Key => ZIO[Env, Err, Item], range: Key => Range)(
    ttl: Key => Option[Duration],
  )(implicit
    trace: Trace,
  ): ZIO[Env with Scope, Nothing, ZKeyedPool[Err, Key, Item]] =
    for {
      environment <- ZIO.environment[Env]
      fiberId     <- ZIO.fiberId
      scope       <- ZIO.scope
      pools       <- ZIO.succeed(new ConcurrentHashMap[Key, MapValue[Err, Item]])
      getOrCreatePool = (key: Key) =>
        ZIO.suspendSucceed {
          var value = pools.get(key)
          if (value eq null) {
            ZIO.uninterruptibleMask { restore =>
              val promise  = Promise.unsafe.make[Nothing, ZPool[Err, Item]](fiberId)(Unsafe.unsafe)
              value = MapValue.Pending(promise)
              val previous = pools.putIfAbsent(key, value)
              if (previous eq null) {
                restore(
                  scope
                    .extend(
                      ZPool
                        .make(
                          get(key).provideEnvironment(environment),
                          range(key),
                          ttl(key).getOrElse(Duration.Infinity),
                        ),
                    ),
                ).foldCauseZIO(
                  cause => {
                    pools.remove(key, value)
                    promise.failCause(cause) *> ZIO.failCause(cause)
                  },
                  pool => {
                    pools.put(key, MapValue.Complete(pool))
                    promise.succeed(pool).as(pool)
                  },
                )
              } else {
                previous match {
                  case MapValue.Complete(pool)   => ZIO.succeedNow(pool)
                  case MapValue.Pending(promise) => restore(promise.await)
                }
              }
            }
          } else {
            value match {
              case MapValue.Complete(pool)   => ZIO.succeedNow(pool)
              case MapValue.Pending(promise) => promise.await
            }
          }
        }
    } yield DefaultKeyedPool(pools, getOrCreatePool)

  private final case class DefaultKeyedPool[Err, Key, Item](
    pools: ConcurrentHashMap[Key, MapValue[Err, Item]],
    getOrCreatePool: Key => ZIO[Any, Nothing, ZPool[Err, Item]],
  ) extends ZKeyedPool[Err, Key, Item] {

    override def get(key: Key)(implicit trace: Trace): ZIO[Scope, Err, Item] =
      getOrCreatePool(key).flatMap(_.get)

    def invalidate(item: Item)(implicit trace: Trace): UIO[Unit] =
      ZIO.foreachDiscard(Chunk.fromJavaIterable(pools.values)) {
        case MapValue.Complete(pool)   => pool.invalidate(item)
        case MapValue.Pending(promise) => promise.await.flatMap(_.invalidate(item))
      }
  }

  private sealed trait MapValue[Err, Item]

  private object MapValue {
    final case class Complete[Err, Item](pool: ZPool[Err, Item])                     extends MapValue[Err, Item]
    final case class Pending[Err, Item](promise: Promise[Nothing, ZPool[Err, Item]]) extends MapValue[Err, Item]
  }
}
