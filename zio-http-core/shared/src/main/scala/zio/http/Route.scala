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

import zio.blocks.endpoint.RoutePattern

final class Route[-Ctx] private (
  val pattern: RoutePattern[Any],
  val handler: Handler[Ctx, Any],
) {
  override def toString: String = s"Route(${pattern})"
}

object Route {
  def apply[Ctx, V](p: RoutePattern[V], h: Handler[Ctx, V]): Route[Ctx] =
    // SAFETY: `V` is only used to connect the compile-time route pattern
    // extraction type with the compile-time handler input type. Once a Route is
    // constructed we intentionally erase `V` because Routes stores
    // heterogeneously-typed handlers together. The value extracted by
    // `RoutePattern[V]` is only ever fed back into the corresponding
    // `Handler[Ctx, V]` through this constructor boundary.
    new Route(
      p.asInstanceOf[RoutePattern[Any]],
      h.asInstanceOf[Handler[Ctx, Any]],
    )
}
