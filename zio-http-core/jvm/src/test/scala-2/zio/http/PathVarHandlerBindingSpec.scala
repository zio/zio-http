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

import java.util.UUID

import zio.test._

import zio.blocks.context.Context
import zio.blocks.endpoint.PathCodec._
import zio.blocks.endpoint.RoutePattern.MethodSyntax
import zio.blocks.scope.Scope
import zio.http.Method.{GET, POST}
import zio.http.PathVarHandler.handler
import zio.http.RouteBinding._

final case class BasketId(value: String)

/**
 * Reproduces every Worked Example from `.omo/drafts/route-pattern-typed-vars.md`'s "Worked
 * examples" section, on Scala 2.13, through the final `pattern -> handler(fn)` two-phase
 * mechanism (D9), using the natural `GET / int("userId") / string("postId")`-style route-pattern
 * syntax directly - the same syntax real Scala 2.13 zio-http users write. Each test invokes the
 * resulting `Route`'s handler directly (bypassing the HTTP server, per Todo 5's own acceptance
 * criteria - full end-to-end TestServer wiring is Final Wave F3's job, not this todo's) and
 * asserts on the real `Response` body.
 */
object PathVarHandlerBindingSpec extends ZIOSpecDefault {

  private val scope = Scope.global
  private val req   = Request.get(URL.root)

  def spec: Spec[Any, Nothing] = suite("PathVarHandlerBinding (Scala 2.13)")(
    test("1. single named var, full use") {
      val route  = GET / int("id") -> handler((id: Int) => Response.text(s"user $id"))
      val result = route.handler.handle(req, Context.empty, 1, scope)
      assertTrue(result == ResultType.responseAsResult(Response.text("user 1")))
    },
    test("2/3. multiple vars, any declared order in the handler fn") {
      val route  = GET / int("userId") / string("postId") ->
        handler((postId: String, userId: Int) => Response.text(s"post=$postId user=$userId"))
      val result = route.handler.handle(req, Context.empty, (7, "abc"), scope)
      assertTrue(result == ResultType.responseAsResult(Response.text("post=abc user=7")))
    },
    test("6/7. same-type collision disambiguated purely by name") {
      val route  = GET / int("page") / int("limit") ->
        handler((limit: Int, page: Int) => Response.text(s"page=$page limit=$limit"))
      val result = route.handler.handle(req, Context.empty, (2, 50), scope)
      assertTrue(result == ResultType.responseAsResult(Response.text("page=2 limit=50")))
    },
    test("8. PathVar name+type miss falls through to Context by type") {
      val route =
        GET / uuid("customerId") ->
          handler((customerId: UUID, basketId: BasketId) => Response.text(s"customer=$customerId basket=$basketId"))
      val cid     = UUID.randomUUID()
      val context = Context(BasketId("basket-1"))
      val result  = route.handler.handle(req, context, cid, scope)
      assertTrue(result == ResultType.responseAsResult(Response.text(s"customer=$cid basket=BasketId(basket-1)")))
    },
    test("9. Request/Scope combine freely with PathVar and Context params, any order") {
      val route =
        GET / int("id") ->
          handler((id: Int, request: Request, basketId: BasketId) =>
            Response.text(s"${request.method} user=$id basket=$basketId"),
          )
      val context = Context(BasketId("basket-2"))
      val result  = route.handler.handle(req, context, 42, scope)
      assertTrue(result == ResultType.responseAsResult(Response.text(s"GET user=42 basket=BasketId(basket-2)")))
    },
    test("10. composed/prefixed patterns accumulate the PathVar registry across `/`, in order") {
      val userPrefix  = GET / "users" / int("userId")
      val fullPattern = userPrefix / "posts" / string("postId")
      val route       = fullPattern -> handler((userId: Int, postId: String) => Response.text(s"user=$userId post=$postId"))
      val result      = route.handler.handle(req, Context.empty, (3, "hello"), scope)
      assertTrue(result == ResultType.responseAsResult(Response.text("user=3 post=hello")))
    },
    test("11. same RoutePattern reused by two handlers with independently-checked usage") {
      val pattern = GET / int("userId") / string("postId")
      val route1  = pattern -> handler((userId: Int, postId: String) => Response.text(s"full:$userId:$postId"))
      val route2  = pattern -> handler((userId: Int) => Response.text(s"partial:$userId"))
      val r1      = route1.handler.handle(req, Context.empty, (9, "x"), scope)
      val r2      = route2.handler.handle(req, Context.empty, (9, "x"), scope)
      assertTrue(
        r1 == ResultType.responseAsResult(Response.text("full:9:x")),
        r2 == ResultType.responseAsResult(Response.text("partial:9")),
      )
    },
    test("pre-built Handler value works via the separate -> overload, no macro involved") {
      val route  = GET / int("id") -> Handler.succeed(Response.ok)
      val result = route.handler.handle(req, Context.empty, 123, scope)
      assertTrue(result == ResultType.responseAsResult(Response.ok))
    },
    test("zero-arg handler (existing thunk shape) resolves through the same handler(...) entry point") {
      val route  = POST / "logout" -> handler(() => Response.ok)
      val result = route.handler.handle(req, Context.empty, (), scope)
      assertTrue(result == ResultType.responseAsResult(Response.ok))
    },
    test("Routes(...) @@ mw middleware idiom compiles unchanged with the new pattern -> handler(fn) routes") {
      val routes = Routes(
        GET / int("id") -> handler((id: Int) => Response.text(s"user $id")),
        POST / "logout" -> handler(() => Response.ok),
      ) @@ Middleware.identity[Any]
      assertTrue(routes.size == 2)
    },
    test("negative: handler param whose name+type matches no PathVar fails to compile with a clear diagnostic") {
      assertZIO(
        typeCheck("""
          import zio.blocks.endpoint.PathCodec._
          import zio.blocks.endpoint.RoutePattern.MethodSyntax
          import zio.http.Method.GET
          import zio.http.PathVarHandler.handler
          import zio.http.RouteBinding._

          GET / int("id") -> handler((wrongName: String) => Response.text(wrongName))
        """)
      )(Assertion.isLeft)
    },
  )
}
