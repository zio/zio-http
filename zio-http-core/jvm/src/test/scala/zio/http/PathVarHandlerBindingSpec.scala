package zio.http

import zio.blocks.context.Context
import zio.blocks.endpoint.PathCodec._
import zio.blocks.endpoint.RoutePattern.{MethodSyntax, RoutePatternOps}
import zio.blocks.scope.Scope
import zio.http.RouteBinding._
import zio.test._

import java.util.UUID

/**
 * Reproduces every Worked Example from `.omo/drafts/route-pattern-typed-vars.md`'s "Worked
 * examples" section, in the FINAL `pattern -> handler(fn)` syntax (D9/D12), on Scala 3.
 */
object PathVarHandlerBindingSpec extends ZIOSpecDefault {

  final case class BasketId(value: String)

  private def urlPath(segments: String*): URL = segments.foldLeft(URL.root)(_ / _)

  private def run[Ctx](route: Route[Ctx], context: Context[Ctx], method: Method, path: Path): Response | Halt = {
    val extracted = route.pattern.decode(method, path).getOrElse(throw new RuntimeException("path did not match"))
    route.handler.handle(Request.get(URL.root.copy(path = path)), context, extracted, Scope.global)
  }

  def spec = suite("PathVarHandlerBinding (Scala 3)")(
    test("1. single named var, full use") {
      val route  = Method.GET / int("id") -> handler((id: Int) => Response.text(s"user $id"))
      val result = run(route, Context.empty, Method.GET, urlPath("42").path)
      assertTrue(result == Response.text("user 42"))
    },
    test("2/3. multiple vars, any declared order in the handler fn") {
      val route = Method.GET / int("userId") / string("postId") ->
        handler((postId: String, userId: Int) => Response.text(s"post=$postId user=$userId"))
      val result = run(route, Context.empty, Method.GET, urlPath("7", "abc").path)
      assertTrue(result == Response.text("post=abc user=7"))
    },
    test("4. partial use compiles and returns correctly (unused-var warning verified separately)") {
      val route = Method.GET / int("userId") / string("postId") ->
        handler((userId: Int) => Response.text(s"user=$userId"))
      val result = run(route, Context.empty, Method.GET, urlPath("9", "ignored").path)
      assertTrue(result == Response.text("user=9"))
    },
    test("5. zero PathVars consumed still compiles and dispatches") {
      val route = Method.GET / int("userId") / string("postId") ->
        handler((request: Request) => Response.text("ignoring vars"))
      val result = run(route, Context.empty, Method.GET, urlPath("1", "x").path)
      assertTrue(result == Response.text("ignoring vars"))
    },
    test("6/7. same-type collision disambiguated purely by name") {
      val route = Method.GET / int("page") / int("limit") ->
        handler((limit: Int, page: Int) => Response.text(s"page=$page limit=$limit"))
      val result = run(route, Context.empty, Method.GET, urlPath("2", "50").path)
      assertTrue(result == Response.text("page=2 limit=50"))
    },
    test("8. PathVar name+type miss falls through to Context[Ctx] by type") {
      val route = Method.GET / uuid("customerId") ->
        handler((customerId: UUID, basketId: BasketId) => Response.text(s"customer=$customerId basket=${basketId.value}"))
      val customerId = UUID.randomUUID()
      val result      = run(route, Context(BasketId("cart-1")), Method.GET, urlPath(customerId.toString).path)
      assertTrue(result == Response.text(s"customer=$customerId basket=cart-1"))
    },
    test("9. Request/Scope combine freely with PathVar and Context params, any order") {
      val route = Method.GET / int("id") ->
        handler((id: Int, request: Request, basketId: BasketId) =>
          Response.text(s"${request.method} user=$id basket=${basketId.value}")
        )
      val result = run(route, Context(BasketId("cart-2")), Method.GET, urlPath("5").path)
      assertTrue(result == Response.text("GET user=5 basket=cart-2"))
    },
    test("10. composed/prefixed patterns accumulate the PathVar registry in order") {
      val userPrefix  = Method.GET / "users" / int("userId")
      val fullPattern = userPrefix / "posts" / string("postId")
      val route       = fullPattern -> handler((userId: Int, postId: String) => Response.text(s"u=$userId p=$postId"))
      val result      = run(route, Context.empty, Method.GET, urlPath("users", "3", "posts", "hi").path)
      assertTrue(result == Response.text("u=3 p=hi"))
    },
    test("11. same RoutePattern reused by two handlers with independently-checked usage") {
      val pattern       = Method.GET / int("userId") / string("postId")
      val fullRoute     = pattern -> handler((userId: Int, postId: String) => Response.text(s"full:$userId:$postId"))
      val partialRoute  = pattern -> handler((userId: Int) => Response.text(s"partial:$userId"))
      val fullResult    = run(fullRoute, Context.empty, Method.GET, urlPath("1", "a").path)
      val partialResult = run(partialRoute, Context.empty, Method.GET, urlPath("1", "a").path)
      assertTrue(fullResult == Response.text("full:1:a"), partialResult == Response.text("partial:1"))
    },
    test("pre-built Handler values still work via the separate `->` overload") {
      val route  = Method.GET / int("id") -> Handler.succeed(Response.ok)
      val result = run(route, Context.empty, Method.GET, urlPath("99").path)
      assertTrue(result == Response.ok)
    },
    test("Routes(...) @@ mw middleware idiom compiles unchanged") {
      val routes = Routes(
        Method.GET / int("id") -> handler((id: Int) => Response.text(s"user $id")),
        Method.POST / "logout" -> handler(() => Response.ok),
      ) @@ Middleware.identity
      assertTrue(routes.size == 2)
    },
    test("negative: a handler param matching no PathVar and no Context type fails to compile at ->") {
      assertZIO(typeCheck {
        """import zio.blocks.endpoint.PathCodec._
import zio.blocks.endpoint.RoutePattern.{MethodSyntax, RoutePatternOps}
import zio.http.RouteBinding._
import zio.http._

val pattern = Method.GET / int("id")
pattern -> handler((wrongName: Int) => Response.text("x"))
"""
      })(Assertion.isLeft(Assertion.anything))
    },
  )
}
