/*
 * Copyright 2022-2023 John A. De Goes and the ZIO Contributors
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
import zio.internal.Platform

trait ZKeyedPoolTest[+Err, -Key, Item] {

  /**
   * Retrieves an item from the pool belonging to the given key in a scoped
   * effect. Note that if acquisition fails, then the returned effect will fail
   * for that same reason. Retrying a failed acquisition attempt will repeat the
   * acquisition attempt.
   */
  def get(key: Key): ZIO[Scope, Err, Item]

  /**
   * Invalidates the specified item. This will cause the pool to eventually
   * reallocate the item, although this reallocation may occur lazily rather
   * than eagerly.
   */
  def invalidate(item: Item): UIO[Unit]

}

object ZKeyedPoolTest {

  /**
   * Makes a new pool of the specified fixed size. The pool is returned in a
   * `Scope`, which governs the lifetime of the pool. When the pool is shutdown
   * because the `Scope` is closed, the individual items allocated by the pool
   * will be released in some unspecified order.
   */
  def make[Key, Env: EnvironmentTag, Err, Item](
    get: Key => ZIO[Env, Err, Item],
    size: => Int,
  ): ZIO[Env with Scope, Nothing, ZKeyedPoolTest[Err, Key, Item]] =
    make(get, _ => size)

  /**
   * Makes a new pool of the specified fixed size. The pool is returned in a
   * `Scope`, which governs the lifetime of the pool. When the pool is shutdown
   * because the `Scope` is closed, the individual items allocated by the pool
   * will be released in some unspecified order.
   *
   * The size of the underlying pools can be configured per key.
   */
  def make[Key, Env: EnvironmentTag, Err, Item](
    get: Key => ZIO[Env, Err, Item],
    size: Key => Int,
  ): ZIO[Env with Scope, Nothing, ZKeyedPoolTest[Err, Key, Item]] =
    makeWith(get, (key: Key) => { val s = size(key); s to s })(_ => None)

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
  ): ZIO[Env with Scope, Nothing, ZKeyedPoolTest[Err, Key, Item]] =
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
  ): ZIO[Env with Scope, Nothing, ZKeyedPoolTest[Err, Key, Item]] =
    makeWith(get, range)((key: Key) => Some(timeToLive(key)))

  private def makeWith[Key, Env: EnvironmentTag, Err, Item](get: Key => ZIO[Env, Err, Item], range: Key => Range)(
    ttl: Key => Option[Duration],
  ): ZIO[Env with Scope, Nothing, ZKeyedPoolTest[Err, Key, Item]] =
    for {
      environment <- ZIO.environment[Env]
      fiberId     <- ZIO.fiberId
      map         <- ZIO.succeed(Platform.newConcurrentMap[Key, MapValue[Err, Item]]()(Unsafe.unsafe))
      scope       <- ZIO.scope
      getOrCreatePool = (key: Key) =>
        ZIO.suspendSucceed {
          var value = map.get(key)
          if (value eq null) {
            ZIO.uninterruptibleMask { restore =>
              val promise  = Promise.unsafe.make[Nothing, ZPoolTest[Err, Item]](fiberId)(Unsafe.unsafe)
              value = MapValue.Pending(promise)
              val previous = map.putIfAbsent(key, value)
              if (previous eq null) {
                restore(
                  scope
                    .extend(
                      ZPoolTest
                        .make(
                          get(key).provideEnvironment(environment),
                          range(key),
                          ttl(key).getOrElse(Duration.Infinity),
                        ),
                    ),
                ).foldCauseZIO(
                  cause => {
                    map.remove(key, value)
                    promise.failCause(cause) *> ZIO.failCause(cause)
                  },
                  pool => {
                    map.put(key, MapValue.Complete(pool))
                    promise.succeed(pool).as(pool)
                  },
                )
              } else {
                previous match {
                  case MapValue.Complete(pool)   => ZIO.succeed(pool)
                  case MapValue.Pending(promise) => restore(promise.await)
                }
              }
            }
          } else {
            value match {
              case MapValue.Complete(pool)   => ZIO.succeed(pool)
              case MapValue.Pending(promise) => promise.await
            }
          }
        }
      activePools     = ZIO.suspendSucceed {
        ZIO.foreach(Chunk.fromJavaIterator(map.values.iterator)) {
          case MapValue.Complete(pool)   => ZIO.succeed(pool)
          case MapValue.Pending(promise) => promise.await
        }
      }
    } yield DefaultKeyedPool(getOrCreatePool, activePools)

  private final case class DefaultKeyedPool[Err, Key, Item](
    getOrCreatePool: Key => ZIO[Any, Nothing, ZPoolTest[Err, Item]],
    activePools: ZIO[Any, Nothing, Chunk[ZPoolTest[Err, Item]]],
  ) extends ZKeyedPoolTest[Err, Key, Item] {

    override def get(key: Key): ZIO[Scope, Err, Item] =
      getOrCreatePool(key).flatMap(_.get)

    def invalidate(item: Item): UIO[Unit] =
      activePools.flatMap(ZIO.foreachDiscard(_)(_.invalidate(item)))
  }

  private sealed trait MapValue[Err, Item]

  private object MapValue {
    final case class Complete[Err, Item](pool: ZPoolTest[Err, Item])                     extends MapValue[Err, Item]
    final case class Pending[Err, Item](promise: Promise[Nothing, ZPoolTest[Err, Item]]) extends MapValue[Err, Item]
  }
}
