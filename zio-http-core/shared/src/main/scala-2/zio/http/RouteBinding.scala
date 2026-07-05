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

import scala.language.experimental.macros

import zio.blocks.endpoint.RoutePattern

/**
 * Phase 2 of the name+type-aware path-var handler binding mechanism (D9), Scala
 * 2.13.
 *
 * An implicit-class extension method `->` targeting the EXTERNALLY-IMPORTED
 * `zio.blocks.endpoint.RoutePattern[A]{type PathVars=PV}` type (defined here,
 * in zio-http's own code - `RoutePattern.scala` itself, which lives in
 * zio-blocks, is untouched and out of scope). Two overloads, dispatched by
 * ordinary Scala overload resolution on the argument's shape (D9):
 *   - `->(h: Handler[Ctx, Req])` (macro-derived): matches `Req`'s open
 *     `PathVar` entries (left by [[PathVarHandler.handler]], phase 1) against
 *     `PV` by (name, type), in any order, rewires each to direct positional
 *     access into the pattern's real runtime value tuple, warns on every `PV`
 *     entry left unconsumed, and aborts if `Req` has an entry `PV` cannot
 *     satisfy.
 *   - `->(h: Handler[Ctx, A])` (pre-built handler passthrough, no macro): for
 *     callers who already have a `Handler` whose `Vars` is the pattern's own
 *     real value type (e.g. `Handler.succeed`), unaffected by any of the above.
 */
object RouteBinding {
  implicit final class RoutePatternArrowOps[A, PV](val self: RoutePattern[A] { type PathVars = PV }) extends AnyVal {
    def ->[Ctx, Req](h: Handler[Ctx, Req]): Route[Ctx] = macro RouteBindingMacros.arrowImpl[A, PV, Ctx, Req]

    def ->[Ctx](h: Handler[Ctx, A]): Route[Ctx] =
      Route(self.asInstanceOf[RoutePattern[A]], h)
  }
}
