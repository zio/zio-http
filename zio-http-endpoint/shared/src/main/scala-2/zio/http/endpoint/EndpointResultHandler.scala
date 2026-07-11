/*
 * Copyright 2026 the ZIO HTTP contributors.
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
package zio.http.endpoint

/**
 * Bridges an arbitrary effect type `F[_]` into a synchronous result, so a
 * single `.implement` method can accept handlers returning any effect for which
 * an instance exists.
 *
 * NOTE (Scala 2.13): this type currently has NO consumer. Unlike Scala 3, the
 * Scala 2 `.implement` signature is `Input => Err | Output` (not
 * `Input => F[Err | Output]`), so it never resolves an `EndpointResultHandler`.
 * It is retained for parity with the Scala 3 side and for a future
 * effect-polymorphic `.implement`; see decisions.md.
 *
 * The core endpoint bridge is synchronous: `zio.http.Handler#handle` returns a
 * `Response` directly (Loom-backed). `EndpointResultHandler` is the one seam
 * that turns a user's `F[R]` into the `R` the bridge encodes. Keeping this
 * abstract means the endpoint module never imports any specific effect system;
 * ZIO / Future / etc. support is added purely by providing an instance,
 * typically from an integration module, without a compile-time dependency here.
 *
 * The identity instance (`resultHandlerId`) covers plain functions returning a
 * value directly.
 */
trait EndpointResultHandler[F[_]] {

  /** Runs `fa` to its value on the current (virtual) thread. */
  def run[A](fa: F[A]): A
}

object EndpointResultHandler {

  def apply[F[_]](implicit handler: EndpointResultHandler[F]): EndpointResultHandler[F] = handler

  /** Identity effect: a handler that returns its result directly. */
  type Id[A] = A

  implicit val resultHandlerId: EndpointResultHandler[Id] = new EndpointResultHandler[Id] {
    def run[A](fa: Id[A]): A = fa
  }
}
