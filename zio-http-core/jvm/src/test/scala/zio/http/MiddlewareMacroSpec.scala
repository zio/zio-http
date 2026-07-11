package zio.http

import zio.test._
import zio.blocks.scope.Scope
import zio.blocks.context.Context

object MiddlewareMacroSpec extends ZIOSpecDefault {

  final case class AuthCtx(userId: String)
  final case class ReqId(value: String)

  private val req: Request = Request.get(URL.root)
  private def route[Ctx](h: Handler[Ctx, Any]): Routes[Ctx] =
    Routes(Route(zio.blocks.endpoint.RoutePattern.GET, h))

  private def exec[Ctx](routes: Routes[Ctx], ctx: Context[Ctx] = Context.empty)(using
    ev: zio.blocks.context.IsNominalType[Ctx],
  ): Response | Halt =
    routes.routes.toList.head.handler.handle(req, ctx, (), Scope.global)

  def spec = suite("MiddlewareMacro")(

    // ── Compilation tests ─────────────────────────────────────────
    suite("compilation")(
      test("basic pass-through (Request, Scope) => Response") {
        val m = Middleware.custom { (req: Request, scope: Scope) => Response.ok }
        assertTrue(m != null)
      },
      test("intercept alias") {
        val m = Middleware.intercept { (req: Request, scope: Scope) => Response.ok }
        assertTrue(m != null)
      },
      test("whenContext alias") {
        val m = Middleware.whenContext { (req: Request, scope: Scope, c: AuthCtx) =>
          if (c.userId.nonEmpty) Response.ok else Response.forbidden
        }
        assertTrue(m != null)
      },
      test("context injection return type") {
        val m = Middleware.custom { (req: Request, scope: Scope) =>
          (Response.ok, AuthCtx("test"))
        }
        assertTrue(m != null)
      },
      test("three-context injection") {
        val m = Middleware.custom { (req: Request, scope: Scope) =>
          (Response.ok, AuthCtx("a"), ReqId("b"))
        }
        assertTrue(m != null)
      },
    ),

    // ── Runtime functionality tests ───────────────────────────────
    suite("functionality")(
      test("pass-through middleware executes and wraps routes") {
        val m: Middleware[Any, Any] =
          Middleware.custom { (req: Request, scope: Scope) => Response.ok }
            .asInstanceOf[Middleware[Any, Any]]
        val base  = route[Any](Handler.succeed(Response(Status.Created)))
        val app   = base @@ m
        val result = exec(app)
        assertTrue(result == zio.http.ResultType.responseAsResult(Response.ok))
      },
      test("injected context arrives in downstream handler") {
        val m: Middleware[Any, Any] =
          Middleware.custom { (req: Request, scope: Scope) =>
            (Response.ok, AuthCtx("alice"))
          }.asInstanceOf[Middleware[Any, Any]]
        val downstream = Handler.extracted[Any, Any] { (_, ctx, _, _) =>
          val auth = ctx.asInstanceOf[Context[AuthCtx]].get[AuthCtx]
          zio.http.ResultType.responseAsResult(Response.text(auth.userId))
        }
        val base  = route[Any](downstream)
        val app   = base @@ m
        val result = exec(app)
        assertTrue(result == zio.http.ResultType.responseAsResult(Response.text("alice")))
      },
      test("context consumption reads provided context") {
        val m: Middleware[Any, Any] =
          Middleware.custom { (req: Request, scope: Scope, auth: AuthCtx) =>
            if (auth.userId == "admin") Response.ok else Response.forbidden
          }.asInstanceOf[Middleware[Any, Any]]
        val base  = route[Any](Handler.succeed(Response(Status.Created)))
        val app   = base @@ m
        val ctx   = Context(AuthCtx("admin"))
        val result = exec(app, ctx)
        // m short-circuits with Response.ok for admin
        assertTrue(result == zio.http.ResultType.responseAsResult(Response.ok))
      },
      test("short-circuit blocks non-admin") {
        val m: Middleware[Any, Any] =
          Middleware.custom { (req: Request, scope: Scope, auth: AuthCtx) =>
            if (auth.userId == "admin") Response.ok else Response.forbidden
          }.asInstanceOf[Middleware[Any, Any]]
        val base  = route[Any](Handler.succeed(Response(Status.Created)))
        val app   = base @@ m
        val ctx   = Context(AuthCtx("eve"))
        val result = exec(app, ctx)
        assertTrue(result == zio.http.ResultType.responseAsResult(Response.forbidden))
      },
    ),

    // ── andThen ordering ──────────────────────────────────────────
    suite("andThen ordering")(
      test("m1.andThen(m2) calls m1 first") {
        val order = scala.collection.mutable.ArrayBuffer[Int]()
        val m1 = new Middleware[Any, Any] {
          def apply(r: Routes[Any]): Routes[Any] = { order += 1; r }
        }
        val m2 = new Middleware[Any, Any] {
          def apply(r: Routes[Any]): Routes[Any] = { order += 2; r }
        }
        m1.andThen(m2)(route[Any](Handler.succeed(Response.ok)))
        assertTrue(order.toList == List(1, 2))
      },
    ),

  )
}
