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

/**
 * Phase 1 of the name+type-aware path-var handler binding mechanism (D9), Scala 2.13.
 *
 * `handler(fn)` reads `fn`'s parameter list (names + types, invisible in the type system
 * otherwise) and resolves each parameter EAGERLY, without any route pattern:
 *   - a parameter of type `Request` or `zio.blocks.scope.Scope` is bound directly (D7 tier 3);
 *   - a parameter whose type is one of the five types `SegmentCodec` can ever capture (`Int`,
 *     `Long`, `String`, `Boolean`, `java.util.UUID`) is left OPEN as a `PathVar[Name,Type]`
 *     requirement - only [[RouteBinding]]'s `->` operator (phase 2), which alone has access to
 *     a route pattern's declared `PathVars`, can resolve it (D7 tier 1);
 *   - any other parameter type is resolved EAGERLY from `Context[Ctx]` by type (D7 tier 2) - `Ctx`
 *     is inferred as the intersection of every such type (or `Any` if there are none).
 *
 * This is a NEW, separate entry point (deliberately NOT the existing `zio.http.handler`/
 * `ToHandler`-based package function, which is left completely untouched) - import
 * `zio.http.PathVarHandler.handler` at call sites that need name+type-aware path-var binding.
 * A companion `RouteBinding.->` operator (phase 2) consumes the `Handler[Ctx, RequiredVars]`
 * this macro returns.
 */
object PathVarHandler {
  // `Handler[-Ctx, -Vars]` is contravariant in BOTH parameters, so the declared bound for a
  // whitebox-refined return type must be the NARROWEST type (`Nothing`), not the widest (`Any`):
  // a computed `Handler[Ctx, ReqVars]` is a subtype of `Handler[Nothing, Nothing]` for ANY
  // `Ctx`/`ReqVars` (contravariance flips `Nothing <: X` into a trivially-true subtype check),
  // whereas it would NOT generally be a subtype of `Handler[Any, Any]`.
  def handler[H](fn: H): Handler[Nothing, Nothing] = macro PathVarHandlerMacros.handlerImpl[H]
}
