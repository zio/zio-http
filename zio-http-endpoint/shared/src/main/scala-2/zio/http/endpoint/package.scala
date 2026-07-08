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
package zio.http

/**
  * Package object for `zio.http.endpoint` module (Scala 2.13).
  *
  * Exports:
  *   - `type |[+A, +B] = Either[A, B]` — Union-like syntax for endpoint
  *     error/output pairs, providing syntactic parity with Scala 3 union types
  *     without explicit `Either` in user code. Activated by a plain
  *     `import zio.http.endpoint._`.
  *
  *   - Extension methods `.implement` on `Endpoint` values (Scala 2.13).
  *
  *   - `EndpointResultHandler` — Type-class for dispatching handlers returning
  *     arbitrary effect types (ZIO, Future, etc.) into synchronous results.
  */
package object endpoint {

  import scala.language.implicitConversions

  /**
    * Union type alias for endpoint result types.
    *
    * Gives syntactic parity with Scala 3 union syntax (`Err | Output`)
    * without requiring the user to write `Either[Err, Output]` or import anything
    * beyond `zio.http.endpoint._`.
    *
    * NOTE: This is Scala 2.13 ONLY. Scala 3 has native `|` union types and
    * should NOT import this alias (it would shadow the native operator).
    * Defined locally and fresh here — NOT re-exporting `zio.http.ResultType.|`.
    */
  type |[+A, +B] = Either[A, B]
}
