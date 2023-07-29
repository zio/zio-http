/*
 * Copyright 2021 - 2023 Sporta Technologies PVT LTD & the ZIO HTTP contributors.
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

package zio.http.endpoint.internal

import zio._

private[http] class MemoizedZIO[K, E, A] private (compute: K => IO[E, A]) { self =>
  private val mapRef: Ref[Map[K, Promise[E, A]]] = Ref.unsafe.make(Map[K, Promise[E, A]]())(Unsafe.unsafe)

  def get(k: K)(implicit trace: zio.http.Trace): IO[E, A] = {
    ZIO.fiberIdWith { fiberId =>
      for {
        effect <- mapRef.modify[IO[E, A]] { map =>
          map.get(k) match {
            case Some(promise) => (promise.await, map)
            case None          =>
              val promise = Promise.unsafe.make[E, A](fiberId)(Unsafe.unsafe)
              (compute(k).exit.tap(exit => promise.done(exit)).flatten, map + (k -> promise))
          }
        }
        result <- effect
      } yield result
    }
  }
}
private[http] object MemoizedZIO                                          {
  def apply[K, E, A](compute: K => IO[E, A]): MemoizedZIO[K, E, A] = new MemoizedZIO(compute)
}
