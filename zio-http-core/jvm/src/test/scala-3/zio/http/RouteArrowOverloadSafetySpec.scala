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

import zio.blocks.context.Context
import zio.blocks.endpoint.PathCodec._
import zio.blocks.endpoint.RoutePattern.{MethodSyntax, RoutePatternOps}
import zio.blocks.scope.Scope
import zio.http.RouteBinding._
import zio.test._

/**
 * Todo 7 (route-pattern-typed-vars): verifies `RoutePattern.->`'s
 * overload-resolution safety (D9/D12) and D12's "no Route-level middleware"
 * boundary on Scala 3.
 *
 * Scala 3's `->` is a SINGLE `transparent inline` macro (not two literal
 * overloads) that dispatches internally on the TYPE of its argument (see
 * `RouteBinding.scala`'s module doc) - so there is no possibility of the
 * compiler reporting an "ambiguous overload" error between the
 * macro-derived-`handler(fn)` path and the pre-built-`Handler[Ctx,V]` path;
 * both shapes are accepted by the SAME single extension method. This spec
 * proves both shapes compile and run correctly side-by-side (including combined
 * in one `Routes(...)`), and that the D12 safety net (bare
 * `pattern -> handler(fn) @@ mw`, without a `Routes(...)` wrapper, must NOT
 * silently compile) really does fail immediately, with the exact compiler error
 * captured and asserted on.
 */
object RouteArrowOverloadSafetySpec extends ZIOSpecDefault {

  private def urlPath(segments: String*): URL = segments.foldLeft(URL.root)(_ / _)

  private def run[Ctx](route: Route[Ctx], context: Context[Ctx], method: Method, path: Path): Response | Halt = {
    val extracted = route.pattern.decode(method, path).getOrElse(throw new RuntimeException("path did not match"))
    route.handler.handle(Request.get(URL.root.copy(path = path)), context, extracted, Scope.global)
  }

  def spec = suite("RouteArrowOverloadSafety (Scala 3)")(
    suite("(a) the two `->` call shapes never collide ambiguously")(
      test("macro-derived `handler(fn)` shape compiles and runs") {
        val route  = Method.GET / int("id") -> handler((id: Int) => Response.text(s"macro:$id"))
        val result = run(route, Context.empty, Method.GET, urlPath("1").path)
        assertTrue(result == Response.text("macro:1"))
      },
      test("pre-built `Handler[Ctx,V]` shape compiles and runs") {
        val route  = Method.GET / int("id") -> Handler.succeed(Response.text("prebuilt"))
        val result = run(route, Context.empty, Method.GET, urlPath("1").path)
        assertTrue(result == Response.text("prebuilt"))
      },
      test("both shapes combine in a single Routes(...) with zero ambiguity/interference") {
        val routes         = Routes(
          Method.GET / int("id") -> handler((id: Int) => Response.text(s"macro:$id")),
          Method.POST / "ping"   -> Handler.succeed(Response.text("prebuilt-pong")),
        )
        val macroResult    = run(routes.routes.head, Context.empty, Method.GET, urlPath("9").path)
        val prebuiltResult = run(routes.routes(1), Context.empty, Method.POST, urlPath("ping").path)
        assertTrue(
          routes.size == 2,
          macroResult == Response.text("macro:9"),
          prebuiltResult == Response.text("prebuilt-pong"),
        )
      },
      test(
        "a pre-built Handler whose Vars type is a real (non-phantom) tuple is never misidentified as a macro-derived handler",
      ) {
        // `Handler.succeed`'s `Vars` is `Any`/`Nothing`-ish (no declared vars), never a
        // `PathVar[...] *: ... *: EmptyTuple` shape - `arrowImpl`'s `tryDecomposePathVarTuple`
        // check is what tells the two shapes apart; this asserts that check is not fooled by a
        // pre-built Handler over a real 2-segment value tuple.
        val route  = Method.GET / int("a") / int("b") -> Handler.succeed(Response.text("both-ignored"))
        val result = run(route, Context.empty, Method.GET, urlPath("1", "2").path)
        assertTrue(result == Response.text("both-ignored"))
      },
    ),
    suite("(c) D12 boundary: bare `pattern -> handler(fn) @@ mw` fails cleanly without the Routes(...) wrapper")(
      test(
        "bare, UN-parenthesized `pattern -> handler(fn) @@ mw` fails immediately - `@@` binds to the Handler, not the Route",
      ) {
        // Scala operator precedence: `@@`'s first character (`@`) is in the highest-precedence
        // "all other special characters" bucket, while `->`'s first character (`-`) is in the
        // lower "+ -" bucket - so `pattern -> handler(fn) @@ mw` parses as
        // `pattern -> (handler(fn) @@ mw)`, NOT `(pattern -> handler(fn)) @@ mw`. This is a real
        // finding (the plan's draft wording assumed the latter): the safety net still holds (the
        // compile still fails immediately, with no silent misparse or wrong-behavior compile),
        // but the exact message names `Handler`, not `Route`. Verified via a real
        // `core.jvm[3.8.3].test.compile` run (not just this typeCheck) - see notepad.
        assertZIO(typeCheck {
          """import zio.blocks.endpoint.PathCodec._
import zio.blocks.endpoint.RoutePattern.{MethodSyntax, RoutePatternOps}
import zio.http.RouteBinding._
import zio.http.{Response, Method, Middleware}

val pattern = Method.GET / int("id")
pattern -> handler((id: Int) => Response.text("x")) @@ Middleware.identity
"""
        })(
          Assertion.isLeft(
            Assertion.containsString("value @@ is not a member of") && Assertion.containsString("Handler"),
          ),
        )
      },
      test("explicitly parenthesized `(pattern -> handler(fn)) @@ mw` fails with the D12-documented `Route` message") {
        assertZIO(typeCheck {
          """import zio.blocks.endpoint.PathCodec._
import zio.blocks.endpoint.RoutePattern.{MethodSyntax, RoutePatternOps}
import zio.http.RouteBinding._
import zio.http.{Response, Method, Middleware}

val pattern = Method.GET / int("id")
(pattern -> handler((id: Int) => Response.text("x"))) @@ Middleware.identity
"""
        })(Assertion.isLeft(Assertion.equalTo("value @@ is not a member of zio.http.Route[Any]")))
      },
      test(
        "bare `pattern -> Handler.succeed(...) @@ mw` (pre-built shape) fails the same way - `@@` binds to the Handler",
      ) {
        assertZIO(typeCheck {
          """import zio.blocks.endpoint.PathCodec._
import zio.blocks.endpoint.RoutePattern.{MethodSyntax, RoutePatternOps}
import zio.http.{Response, Method, Middleware, Handler}

val pattern = Method.GET / int("id")
pattern -> Handler.succeed(Response.ok) @@ Middleware.identity
"""
        })(
          Assertion.isLeft(
            Assertion.containsString("value @@ is not a member of") && Assertion.containsString("Handler"),
          ),
        )
      },
      test(
        "sanity: the properly-wrapped `Routes(pattern -> handler(fn)) @@ mw` idiom compiles cleanly (control case)",
      ) {
        assertZIO(typeCheck {
          """import zio.blocks.endpoint.PathCodec._
import zio.blocks.endpoint.RoutePattern.{MethodSyntax, RoutePatternOps}
import zio.http.RouteBinding._
import zio.http.{Response, Method, Middleware, Routes}

val pattern = Method.GET / int("id")
Routes(pattern -> handler((id: Int) => Response.text("x"))) @@ Middleware.identity
"""
        })(Assertion.isRight(Assertion.anything))
      },
    ),
  )
}
