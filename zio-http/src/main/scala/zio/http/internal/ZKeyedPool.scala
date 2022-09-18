package zio.http.internal

import zio._
import zio.stm.{TMap, TPromise, ZSTM}

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
  def make[Key, Env: EnvironmentTag, Err, Item](get: Key => ZIO[Env, Err, Item], range: => Range, timeToLive: => Duration)(implicit
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
  def make[Key, Env: EnvironmentTag, Err, Item](get: Key => ZIO[Env, Err, Item], range: Key => Range, timeToLive: Key => Duration)(
    implicit trace: Trace,
  ): ZIO[Env with Scope, Nothing, ZKeyedPool[Err, Key, Item]] =
    makeWith(get, range)((key: Key) => Some(timeToLive(key)))

  private def makeWith[Key, Env: EnvironmentTag, Err, Item](get: Key => ZIO[Env, Err, Item], range: Key => Range)(
    ttl: Key => Option[Duration],
  )(implicit
    trace: Trace,
  ): ZIO[Env with Scope, Nothing, ZKeyedPool[Err, Key, Item]] =
    for {
      activePools   <- TMap.empty[Key, TPromise[Nothing, ZPool[Err, Item]]].commit
      acquiredItems <- TMap.empty[Item, Key].commit
      env           <- ZIO.environment[Env]
      createPool = (key: Key) =>
        ttl(key) match {
          case Some(timeToLive) =>
            ZPool
              .make(get(key), range(key), timeToLive)
              .provideSomeEnvironment[Scope](_.union[Env](env))
          case None             =>
            ZPool
              .make(get(key), range(key).end)
              .provideSomeEnvironment[Scope](_.union[Env](env))
        }
    } yield DefaultKeyedPool[Err, Key, Item](
      createPool,
      activePools,
      acquiredItems,
    )

  private final case class DefaultKeyedPool[Err, Key, Item](
    createPool: Key => ZIO[Scope, Nothing, ZPool[Err, Item]],
    activePools: TMap[Key, TPromise[Nothing, ZPool[Err, Item]]],
    acquiredItems: TMap[Item, Key],
  ) extends ZKeyedPool[Err, Key, Item] {

    override def get(key: Key)(implicit trace: Trace): ZIO[Scope, Err, Item] =
      ZIO.uninterruptibleMask { outerRestore =>
        outerRestore {
          activePools
            .get(key)
            .flatMap {
              case Some(promise) =>
                promise.await.map { pool =>
                  acquireFrom(key, pool)
                }
              case None          =>
                TPromise.make[Nothing, ZPool[Err, Item]].flatMap { promise =>
                  activePools
                    .put(key, promise)
                    .as {
                      outerRestore {
                        ZIO.uninterruptibleMask { innerRestore =>
                          for {
                            pool <- createPool(key)
                            _    <- promise.succeed(pool).commit
                            item <- innerRestore(acquireFrom(key, pool))
                          } yield item
                        }
                      }
                    }
                }
            }
            .commit
        }.flatten
      }

    override def invalidate(item: Item)(implicit trace: Trace): UIO[Unit] =
      ZIO.uninterruptibleMask { restore =>
        restore {
          acquiredItems
            .get(item)
            .flatMap {
              case None      => ZSTM.succeed(ZIO.unit)
              case Some(key) =>
                activePools.get(key).flatMap {
                  case None              => ZSTM.succeed(ZIO.unit)
                  case Some(poolPromise) =>
                    poolPromise.poll.map {
                      case None              => ZIO.unit
                      case Some(Left(_))     => ZIO.unit
                      case Some(Right(pool)) => pool.invalidate(item)
                    }

                }
            }
            .commit
        }.flatten
      }

    private def acquireFrom(key: Key, pool: ZPool[Err, Item]): ZIO[Scope, Err, Item] =
      ZIO.uninterruptibleMask { restore =>
        restore(pool.get).tap { item =>
          Scope.addFinalizer(acquiredItems.delete(item).commit) *>
            acquiredItems.put(item, key).commit
        }
      }
  }
}
