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

import zio.blocks.context.Context
import zio.blocks.scope.Scope
import zio.http.{Client, Halt, Request, Response, Route}

/**
 * Minimal in-process dispatcher used ONLY by this module's tests.
 *
 * Sends a [[Request]] directly through a [[Route]]'s [[zio.http.Handler]] with
 * no real TCP socket. This module's test sources cannot depend on
 * `zio-http-testkit` (out of scope / not a declared moduleDep), so this is a
 * hand-rolled substitute limited to what `core.jvm()` already exposes.
 *
 * Crucially, this does NOT bypass the endpoint's real request/response wire
 * codec: `route.handler` is exactly the [[zio.http.Handler]] that `.implement`
 * produced (built on [[EndpointCodec]]'s real JSON encode/decode over
 * [[zio.http.Body]]), so dispatching through it exercises the genuine HTTP
 * encode/decode path, not a shortcut that calls the user handler directly.
 */
private[endpoint] object InProcessDispatcher {

  /**
   * Dispatches `request` through `route`, resolving a `Halt` to its wrapped
   * [[Response]].
   */
  def dispatch(route: Route[Any], request: Request): Response = {
    val vars = route.pattern.decode(request.method, request.url.path) match {
      case Right(v)    => v
      case Left(error) => throw new RuntimeException(s"Route pattern did not match request $request: $error")
    }
    route.handler.handle(request, Context.empty, vars, Scope.global) match {
      case Left(response)        => response
      case Right(Halt(response)) => response
    }
  }

  /**
   * Wraps `route` as a [[Client]] that dispatches in-process, for exercising
   * `.call`.
   */
  def clientFor(route: Route[Any]): Client =
    new Client {
      def send(request: Request): Response = dispatch(route, request)
    }
}
