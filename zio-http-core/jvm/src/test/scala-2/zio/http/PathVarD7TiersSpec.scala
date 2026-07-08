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
 * Tier-2-only capability types: never a `SegmentCodec`-capturable primitive
 * (Int/Long/String/ Boolean/UUID), so they can NEVER be classified as a PathVar
 * candidate - always Context-resolved. Declared at package scope (matching this
 * package's existing convention, e.g. `PathVarHandlerBindingSpec.scala`'s own
 * package-level `BasketId`) with distinct names to avoid any collision with
 * that unrelated case class.
 */
final case class D7TierCartId(value: String)
final case class D7TierUserRef(value: String)

/**
 * Todo 10 (route-pattern-typed-vars): a dedicated, single-file, per-tier
 * ISOLATION suite for D7's 4-tier handler-parameter resolution chain, plus the
 * D6 order-independence PROPERTY test, on Scala 2.13.
 *
 * `PathVarHandlerBindingSpec.scala` and `RouteArrowOverloadSafetySpec.scala`
 * already exercise these mechanisms as part of reproducing the draft's Worked
 * Examples and proving overload- resolution safety, but every case there mixes
 * at least two tiers together (e.g. Worked Example 9 combines PathVar + Context
 * + Request in one handler). THIS file's job is different: each test below
 * isolates exactly ONE D7 tier - the other tiers/capabilities are verifiably
 * absent from both the route pattern and the handler signature, not merely
 * unused - so a future reader can point at a single, minimal test per tier
 * instead of reverse-engineering tier boundaries out of a combined example.
 *
 * This file DELIBERATELY does NOT re-prove:
 *   - the unused-PathVar WARNING CONTENT, per-var separateness, or the real
 *     `-Xfatal-warnings` build-level proofs (see
 *     `PathVarHandlerBindingSpec.scala`, tests 14-16, and its
 *     `FatalWarningsProof` helper object - Todo 6's deliverable), or
 *   - the `->` OVERLOAD-RESOLUTION SAFETY between the macro-derived and
 *     pre-built-`Handler` call shapes, or the D12 "no Route-level middleware"
 *     boundary (see `RouteArrowOverloadSafetySpec.scala` - Todo 7's
 *     deliverable).
 * Those two files remain the single source of truth for warning text and
 * overload safety.
 */
object PathVarD7TiersSpec extends ZIOSpecDefault {

  private val scope = Scope.global
  private val req   = Request.get(URL.root)

  def spec: Spec[Any, Nothing] = suite("PathVarD7Tiers (Scala 2.13)")(
    suite("D7 tier 1 ONLY: PathVar name+type match - zero Context/Request/Scope involved")(
      test("a single PathVar, fully isolated (no other capability declared anywhere)") {
        val route  = GET / int("id") -> handler((id: Int) => Response.text(s"id=$id"))
        val result = route.handler.handle(req, Context.empty, 42, scope)
        assertTrue(result == ResultType.responseAsResult(Response.text("id=42")))
      },
      test(
        "multiple PathVars matched purely by name+type, handler fn order differs from the pattern's declared order",
      ) {
        val route  = GET / int("width") / string("label") ->
          handler((label: String, width: Int) => Response.text(s"$label:$width"))
        val result = route.handler.handle(req, Context.empty, (10, "box"), scope)
        assertTrue(result == ResultType.responseAsResult(Response.text("box:10")))
      },
      test("two same-typed PathVars disambiguated purely by NAME, zero Context involved") {
        val route  = GET / int("row") / int("col") ->
          handler((col: Int, row: Int) => Response.text(s"r=$row c=$col"))
        val result = route.handler.handle(req, Context.empty, (3, 9), scope)
        assertTrue(result == ResultType.responseAsResult(Response.text("r=3 c=9")))
      },
    ),
    suite("D7 tier 2 ONLY: Context fallback - the pattern declares ZERO PathVars at all")(
      test("a single handler param resolved purely from Context, pattern has no captured segments") {
        val route  = GET / "widgets" -> handler((cart: D7TierCartId) => Response.text(s"cart=${cart.value}"))
        val result = route.handler.handle(req, Context(D7TierCartId("cart-42")), (), scope)
        assertTrue(result == ResultType.responseAsResult(Response.text("cart=cart-42")))
      },
      test("multiple distinct Context capabilities resolve together, pattern still has ZERO PathVars") {
        val route   = GET / "checkout" ->
          handler((cart: D7TierCartId, user: D7TierUserRef) => Response.text(s"${user.value}:${cart.value}"))
        val context = Context(D7TierCartId("c-1"), D7TierUserRef("u-1"))
        val result  = route.handler.handle(req, context, (), scope)
        assertTrue(result == ResultType.responseAsResult(Response.text("u-1:c-1")))
      },
    ),
    suite("D7 tier 3 ONLY: Request and Scope, separately and together - zero PathVars in the pattern")(
      test("Request alone, zero PathVars, zero Context") {
        val route  = GET / "ping" -> handler((request: Request) => Response.text(s"method=${request.method}"))
        val result = route.handler.handle(req, Context.empty, (), scope)
        assertTrue(result == ResultType.responseAsResult(Response.text("method=GET")))
      },
      test("Scope alone, zero PathVars, zero Context") {
        val route =
          GET / "warmup" -> handler((s: Scope) => Response.text(if (s != null) "scope-received" else "no-scope"))
        val result = route.handler.handle(req, Context.empty, (), scope)
        assertTrue(result == ResultType.responseAsResult(Response.text("scope-received")))
      },
      test("Request AND Scope together, zero PathVars, zero Context") {
        val route  = GET / "combined" ->
          handler((request: Request, s: Scope) =>
            Response.text(s"${request.method}+${if (s != null) "scope" else "none"}"),
          )
        val result = route.handler.handle(req, Context.empty, (), scope)
        assertTrue(result == ResultType.responseAsResult(Response.text("GET+scope")))
      },
    ),
    suite("D7 tier 4 ONLY: unmatched name+type compile error - isolated from every other concern")(
      test(
        "a PathVar-eligible-typed param whose name matches nothing in the pattern fails to compile at `->`, with no Context/Request/Scope param present to confuse the diagnosis",
      ) {
        assertZIO(
          typeCheck("""
            import zio.blocks.endpoint.PathCodec._
            import zio.blocks.endpoint.RoutePattern.MethodSyntax
            import zio.http.Method.GET
            import zio.http.PathVarHandler.handler
            import zio.http.RouteBinding._

            GET / int("id") -> handler((totallyUnrelatedName: Int) => Response.text("unreachable"))
          """),
        )(Assertion.isLeft)
      },
    ),
    suite("D6 order-independence PROPERTY: the SAME pattern, 3 different handler param orderings, IDENTICAL results")(
      test(
        "3 distinct-typed PathVars, bound via 3 different declaration orderings, all produce the byte-identical response for the same input",
      ) {
        val pattern = GET / int("a") / string("b") / bool("c")

        val routeDeclOrder   = pattern -> handler((a: Int, b: String, c: Boolean) => Response.text(s"$a-$b-$c"))
        val routeReversed    = pattern -> handler((c: Boolean, b: String, a: Int) => Response.text(s"$a-$b-$c"))
        val routeMiddleFirst = pattern -> handler((b: String, a: Int, c: Boolean) => Response.text(s"$a-$b-$c"))

        val vars = (7, "x", true)

        val resultDeclOrder   = routeDeclOrder.handler.handle(req, Context.empty, vars, scope)
        val resultReversed    = routeReversed.handler.handle(req, Context.empty, vars, scope)
        val resultMiddleFirst = routeMiddleFirst.handler.handle(req, Context.empty, vars, scope)

        val expected = ResultType.responseAsResult(Response.text("7-x-true"))
        assertTrue(
          resultDeclOrder == expected,
          resultReversed == expected,
          resultMiddleFirst == expected,
          resultDeclOrder == resultReversed,
          resultReversed == resultMiddleFirst,
        )
      },
    ),
  )
}
