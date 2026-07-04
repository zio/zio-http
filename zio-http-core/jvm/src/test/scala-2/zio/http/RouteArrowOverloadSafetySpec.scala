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
import zio.blocks.endpoint.PathCodec._
import zio.blocks.endpoint.RoutePattern.MethodSyntax
import zio.blocks.scope.Scope
import zio.http.Method.GET
import zio.http.PathVarHandler.handler
import zio.http.RouteBinding._

/**
 * Todo 7 (route-pattern-typed-vars): verifies `RoutePattern.->`'s overload-resolution safety
 * (D9/D12) and D12's "no Route-level middleware" boundary on Scala 2.13.
 *
 * Unlike Scala 3 (a single `transparent inline` macro dispatching internally), Scala 2.13's `->`
 * genuinely IS two declared overloads on `RoutePatternArrowOps` (`RouteBinding.scala`):
 *   - `def ->[Ctx, Req](h: Handler[Ctx, Req]): Route[Ctx] = macro ...` (macro-derived path)
 *   - `def ->[Ctx](h: Handler[Ctx, A]): Route[Ctx]` (pre-built-Handler passthrough, no macro)
 * This spec proves ordinary Scala overload resolution picks the correct one for each call shape
 * with ZERO "ambiguous reference to overloaded definition" errors, that both shapes run correctly
 * (including combined in one `Routes(...)`), and that the D12 safety net (bare
 * `pattern -> handler(fn) @@ mw` without a `Routes(...)` wrapper) really does fail immediately,
 * with the exact compiler error captured and asserted on - identical in shape to Scala 3's
 * behavior (see the Scala 3 sibling spec's module doc for the operator-precedence finding).
 */
object RouteArrowOverloadSafetySpec extends ZIOSpecDefault {

  private val scope = Scope.global
  private val req   = Request.get(URL.root)

  def spec: Spec[Any, Nothing] = suite("RouteArrowOverloadSafety (Scala 2.13)")(
    suite("(a) the two `->` overloads never collide ambiguously")(
      test("macro-derived `handler(fn)` overload resolves and runs (no ambiguous-overload error)") {
        val route  = GET / int("id") -> handler((id: Int) => Response.text(s"macro:$id"))
        val result = route.handler.handle(req, Context.empty, 1, scope)
        assertTrue(result == ResultType.responseAsResult(Response.text("macro:1")))
      },
      test("pre-built `Handler[Ctx,A]` overload resolves and runs (no ambiguous-overload error)") {
        val route  = GET / int("id") -> Handler.succeed(Response.text("prebuilt"))
        val result = route.handler.handle(req, Context.empty, 1, scope)
        assertTrue(result == ResultType.responseAsResult(Response.text("prebuilt")))
      },
      test("both overloads combine in a single Routes(...) with zero ambiguity/interference") {
        val routes = Routes(
          GET / int("id")            -> handler((id: Int) => Response.text(s"macro:$id")),
          Method.POST / "ping"       -> Handler.succeed(Response.text("prebuilt-pong")),
        )
        val macroRoute    = routes.routes.head
        val prebuiltRoute = routes.routes(1)
        val macroResult    = macroRoute.handler.handle(req, Context.empty, 9, scope)
        val prebuiltResult = prebuiltRoute.handler.handle(req, Context.empty, (), scope)
        assertTrue(
          routes.size == 2,
          macroResult == ResultType.responseAsResult(Response.text("macro:9")),
          prebuiltResult == ResultType.responseAsResult(Response.text("prebuilt-pong")),
        )
      },
      test("a pre-built Handler over a real 2-segment value tuple resolves to the non-macro overload, not the macro one") {
        val route  = GET / int("a") / int("b") -> Handler.succeed(Response.text("both-ignored"))
        val result = route.handler.handle(req, Context.empty, (1, 2), scope)
        assertTrue(result == ResultType.responseAsResult(Response.text("both-ignored")))
      },
    ),
    suite("(c) D12 boundary: bare `pattern -> handler(fn) @@ mw` fails cleanly without the Routes(...) wrapper")(
      test("bare, UN-parenthesized `pattern -> handler(fn) @@ mw` fails immediately - `@@` binds to the Handler, not the Route") {
        // Same Scala operator-precedence rule as Scala 3: `@@` (first char `@`, the highest
        // "all other special characters" precedence bucket) binds TIGHTER than `->` (first char
        // `-`, the lower "+ -" bucket) - `pattern -> handler(fn) @@ mw` parses as
        // `pattern -> (handler(fn) @@ mw)`. Verified via a real `core.jvm[2.13.18].test.compile`
        // run (not just this typeCheck) - see notepad.
        assertZIO(typeCheck {
          """import zio.blocks.endpoint.PathCodec._
import zio.blocks.endpoint.RoutePattern.MethodSyntax
import zio.http.PathVarHandler.handler
import zio.http.RouteBinding._
import zio.http.{Response, Method, Middleware}

val pattern = Method.GET / int("id")
pattern -> handler((id: Int) => Response.text("x")) @@ Middleware.identity
"""
        })(Assertion.isLeft(Assertion.containsString("value @@ is not a member of") && Assertion.containsString("Handler")))
      },
      test("explicitly parenthesized `(pattern -> handler(fn)) @@ mw` fails with the D12-documented `Route` message") {
        assertZIO(typeCheck {
          """import zio.blocks.endpoint.PathCodec._
import zio.blocks.endpoint.RoutePattern.MethodSyntax
import zio.http.PathVarHandler.handler
import zio.http.RouteBinding._
import zio.http.{Response, Method, Middleware}

val pattern = Method.GET / int("id")
(pattern -> handler((id: Int) => Response.text("x"))) @@ Middleware.identity
"""
        })(Assertion.isLeft(Assertion.equalTo("value @@ is not a member of zio.http.Route[Any]")))
      },
      test("bare `pattern -> Handler.succeed(...) @@ mw` (pre-built overload) fails the same way - `@@` binds to the Handler") {
        assertZIO(typeCheck {
          """import zio.blocks.endpoint.PathCodec._
import zio.blocks.endpoint.RoutePattern.MethodSyntax
import zio.http.RouteBinding._
import zio.http.{Response, Method, Middleware, Handler}

val pattern = Method.GET / int("id")
pattern -> Handler.succeed(Response.ok) @@ Middleware.identity
"""
        })(Assertion.isLeft(Assertion.containsString("value @@ is not a member of") && Assertion.containsString("Handler")))
      },
      test("sanity: the properly-wrapped `Routes(pattern -> handler(fn)) @@ mw` idiom compiles cleanly (control case)") {
        assertZIO(typeCheck {
          """import zio.blocks.endpoint.PathCodec._
import zio.blocks.endpoint.RoutePattern.MethodSyntax
import zio.http.PathVarHandler.handler
import zio.http.RouteBinding._
import zio.http.{Response, Method, Middleware, Routes}

val pattern = Method.GET / int("id")
Routes(pattern -> handler((id: Int) => Response.text("x"))) @@ Middleware.identity[Any]
"""
        })(Assertion.isRight(Assertion.anything))
      },
    ),
  )
}
