package zio.http

import zio.test._
import zio.blocks.scope.Scope
import zio.blocks.context.Context
import zio.http.ResultType._

object MiddlewareMacroSpec extends ZIOSpecDefault {

  final case class AuthCtx(userId: String)
  final case class ReqId(value: String)
  final case class C1(v: String); final case class C2(v: String); final case class C3(v: String)
  final case class C4(v: String); final case class C5(v: String); final case class C6(v: String)
  final case class C7(v: String); final case class C8(v: String); final case class C9(v: String)
  final case class C10(v: String); final case class C11(v: String); final case class C12(v: String)
  final case class C13(v: String); final case class C14(v: String); final case class C15(v: String)
  final case class C16(v: String); final case class C17(v: String); final case class C18(v: String)
  final case class C19(v: String); final case class C20(v: String); final case class C21(v: String)
  final case class C22(v: String); final case class C23(v: String)

  private val req: Request                                  = Request.get(URL.root)
  private def route[Ctx](h: Handler[Ctx, Any]): Routes[Ctx] =
    Routes(Route(zio.blocks.endpoint.RoutePattern.GET, h))

  private def exec[Ctx](routes: Routes[Ctx], ctx: Context[Ctx] = Context.empty)(implicit
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
        val base                    = route[Any](Handler.succeed(Response(Status.Created)))
        val app                     = base @@ m
        val result                  = exec(app)
        assertTrue(result == zio.http.ResultType.responseAsResult(Response.ok))
      },
      test("injected context arrives in downstream handler") {
        val m: Middleware[Any, Any] =
          Middleware.custom { (req: Request, scope: Scope) =>
            (Response.ok, AuthCtx("alice"))
          }.asInstanceOf[Middleware[Any, Any]]
        val downstream              = Handler.extracted[Any, Any] { (_, ctx, _, _) =>
          val auth = ctx.asInstanceOf[Context[AuthCtx]].get[AuthCtx]
          zio.http.ResultType.responseAsResult(Response.text(auth.userId))
        }
        val base                    = route[Any](downstream)
        val app                     = base @@ m
        val result                  = exec(app)
        assertTrue(result == zio.http.ResultType.responseAsResult(Response.text("alice")))
      },
      test("context consumption reads provided context") {
        val m: Middleware[Any, Any] =
          Middleware.custom { (req: Request, scope: Scope, auth: AuthCtx) =>
            if (auth.userId == "admin") Response.ok else Response.forbidden
          }.asInstanceOf[Middleware[Any, Any]]
        val base                    = route[Any](Handler.succeed(Response(Status.Created)))
        val app                     = base @@ m
        val ctx                     = Context(AuthCtx("admin"))
        val result                  = exec(app, ctx)
        // m short-circuits with Response.ok for admin
        assertTrue(result == zio.http.ResultType.responseAsResult(Response.ok))
      },
      test("short-circuit blocks non-admin") {
        val m: Middleware[Any, Any] =
          Middleware.custom { (req: Request, scope: Scope, auth: AuthCtx) =>
            if (auth.userId == "admin") Response.ok else Response.forbidden
          }.asInstanceOf[Middleware[Any, Any]]
        val base                    = route[Any](Handler.succeed(Response(Status.Created)))
        val app                     = base @@ m
        val ctx                     = Context(AuthCtx("eve"))
        val result                  = exec(app, ctx)
        assertTrue(result == zio.http.ResultType.responseAsResult(Response.forbidden))
      },
      test("24-arg middleware (>22) executes correctly") {
        // 2 fixed (Request, Scope) + 22 context = 24 total args (FunctionXXL)
        val m: Middleware[Any, Any] =
          Middleware.custom {
            (
              req: Request,
              scope: Scope,
              c1: C1,
              c2: C2,
              c3: C3,
              c4: C4,
              c5: C5,
              c6: C6,
              c7: C7,
              c8: C8,
              c9: C9,
              c10: C10,
              c11: C11,
              c12: C12,
              c13: C13,
              c14: C14,
              c15: C15,
              c16: C16,
              c17: C17,
              c18: C18,
              c19: C19,
              c20: C20,
              c21: C21,
              c22: C22,
            ) =>
              Response.text(s"${c1.v}/${c11.v}/${c22.v}")
          }.asInstanceOf[Middleware[Any, Any]]
        val base                    = route[Any](Handler.succeed(Response(Status.Created)))
        val app                     = base @@ m
        val ctx0                    = Context.empty
        val ctx: Context[Any]       = ctx0
          .add(C1("a"))
          .add(C2("b"))
          .add(C3("c"))
          .add(C4("d"))
          .add(C5("e"))
          .add(C6("f"))
          .add(C7("g"))
          .add(C8("h"))
          .add(C9("i"))
          .add(C10("j"))
          .add(C11("k"))
          .add(C12("l"))
          .add(C13("m"))
          .add(C14("n"))
          .add(C15("o"))
          .add(C16("p"))
          .add(C17("q"))
          .add(C18("r"))
          .add(C19("s"))
          .add(C20("t"))
          .add(C21("u"))
          .add(C22("v"))
          .asInstanceOf[Context[Any]]
        val result                  = app.routes.toList.head.handler.handle(req, ctx, (), Scope.global)
        assertTrue(result == zio.http.ResultType.responseAsResult(Response.text("a/k/v")))
      },
    ),

    // ── andThen ordering ──────────────────────────────────────────
    suite("andThen ordering")(
      test("m1.andThen(m2) calls m1 first") {
        val order = scala.collection.mutable.ArrayBuffer[Int]()
        val m1    = new Middleware[Any, Any] {
          def apply(r: Routes[Any]): Routes[Any] = { order += 1; r }
        }
        val m2    = new Middleware[Any, Any] {
          def apply(r: Routes[Any]): Routes[Any] = { order += 2; r }
        }
        m1.andThen(m2)(route[Any](Handler.succeed(Response.ok)))
        assertTrue(order.toList == List(1, 2))
      },
    ),
  )
}
