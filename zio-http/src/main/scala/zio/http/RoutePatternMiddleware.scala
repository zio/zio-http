/*
 * Copyright 2023 the ZIO HTTP contributors.
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

import zio._

sealed abstract class RoutePatternMiddleware[-Env, A] {
  type PathInput
  type Context

  def routePattern: RoutePattern[PathInput]
  def middleware: Middleware[Env, Context]
  def zippable: Zippable.Out[PathInput, Context, A]

  def ->[Env, Err, I](handler: Handler[Env, Err, I, Response])(implicit
    input: RequestHandlerInput[A, I],
    trace: Trace,
  ): Route[Env, Err] = ???
}
object RoutePatternMiddleware                         {
  def apply[Env, PI, MC, Out](rp: RoutePattern[PI], mc: Middleware[Env, MC])(implicit
    z: Zippable.Out[PI, MC, Out],
  ): RoutePatternMiddleware[Env, Out] =
    new RoutePatternMiddleware[Env, Out] {
      type PathInput = PI
      type Context   = MC

      def routePattern: RoutePattern[PathInput]           = rp
      def middleware: Middleware[Env, Context]            = mc
      def zippable: Zippable.Out[PathInput, Context, Out] = z
    }
}
