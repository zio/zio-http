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

import zio.test._

import zio.blocks.context.Context
import zio.blocks.endpoint.RoutePattern.MethodSyntax
import zio.blocks.scope.Scope
import zio.http.Method.GET
import zio.http.PathVarHandler.handler
import zio.http.ResultType.{responseAsResult, |}
import zio.http.RouteBinding._

final case class AuthSession(user: String)

object MiddlewareAuthSpec extends ZIOSpecDefault {

  private def dispatch(routes: Routes[Any], request: Request): Response | Halt = {
    val route     = routes.routes.head
    val extracted = route.pattern
      .decode(request.method, request.url.path)
      .getOrElse(throw new RuntimeException("path did not match"))
    route.handler.handle(request, Context.empty, extracted, Scope.global)
  }

  private val secured: Route[AuthSession] =
    GET / "secure" -> handler((session: AuthSession) => Response.text(s"hello ${session.user}"))

  private val basicRoutes: Routes[Any] =
    Routes(secured) @@ Middleware.basicAuth[AuthSession] { basic =>
      if (basic.username == "admin" && basic.password == "secret") Right(AuthSession(basic.username))
      else Left(Response.unauthorized)
    }

  def spec: Spec[Any, Nothing] = suite("MiddlewareAuth (Scala 2.13)")(
    test("no credentials -> unauthorized, handler never runs") {
      val result = dispatch(basicRoutes, Request.get(URL.root / "secure"))
      assertTrue(result == responseAsResult(Response.unauthorized))
    },
    test("valid credentials -> handler runs with injected session") {
      val request = Request.get(URL.root / "secure").addHeader(Header.Authorization.Basic("admin", "secret"))
      val result  = dispatch(basicRoutes, request)
      assertTrue(result == responseAsResult(Response.text("hello admin")))
    },
    test("wrong scheme (Bearer against basicAuth) -> unauthorized") {
      val request = Request.get(URL.root / "secure").addHeader(Header.Authorization.Bearer("some-token"))
      val result  = dispatch(basicRoutes, request)
      assertTrue(result == responseAsResult(Response.unauthorized))
    },
  )
}
