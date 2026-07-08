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
 * Marker for endpoint input fields that are intentionally unused.
 *
 * Mirrors `zio.blocks.endpoint.PathVar.Ignored` pattern: used at the INPUT
 * schema level to annotate a field that the developer has explicitly marked as
 * not consumed by the handler.
 *
 * In the endpoint Input type definition, wrap a field with `.unused()` to
 * suppress the "field never used" warning:
 *
 * ```scala
 * case class MyInput(
 *   userId: Int,
 *   debugFlag: Boolean.unused, // Marked, unconsumed -> no warning (suppressed)
 * )
 * ```
 *
 * Four combinations of (isMarked, consumed) emit warnings:
 *   - plain, unconsumed -> "field X was defined but never used"
 *   - plain, consumed -> no warning (normal)
 *   - marked, unconsumed -> no warning (suppressed)
 *   - marked, consumed -> "field X was marked .unused but is referenced" (lint)
 */
private[endpoint] sealed trait Unused[A] {
  def value: A
}

private[endpoint] object Unused {
  def apply[A](a: A): Unused[A] = new Unused[A] {
    def value = a
  }

  implicit final class UnusedOps[A](a: A) {
    def unused: Unused[A] = Unused(a)
  }
}
