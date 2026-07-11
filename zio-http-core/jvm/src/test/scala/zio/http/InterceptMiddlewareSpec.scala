package zio.http

import zio.test._
import zio.blocks.context.{Context, IsNominalType}
import zio.blocks.scope.Scope
import zio.http.ResultType._

object InterceptMiddlewareSpec extends ZIOSpecDefault {

  private val req: Request = Request.get(URL.root)
  private val postReq: Request = Request.post(URL.root, Body.empty)

  private def mkRoute[Ctx](h: Handler[Ctx, Any]): Routes[Ctx] =
    Routes(Route(zio.blocks.endpoint.RoutePattern.GET, h))

  private def runSingle[Ctx](routes: Routes[Ctx], request: Request = req): Response | Halt =
    routes.routes.toList.head.handler.handle(request, Context.empty.asInstanceOf[Context[Ctx]], (), Scope.global)

  def spec = suite("InterceptConditionalMiddleware")(

    suite("interceptHandler")(
      test("short-circuits with Some") {
        val mw = Middleware.interceptHandler(_ => Some(Halt(Response.forbidden)))
        val app = mkRoute[Any](Handler.succeed(Response.text("ok"))) @@ mw
        assertTrue(runSingle(app).isInstanceOf[Halt])
      },
      test("passes through with None") {
        var called = false
        val mw = Middleware.interceptHandler { _ => called = true; None }
        val app = mkRoute[Any](Handler.succeed(Response.text("ok"))) @@ mw
        assertTrue(runSingle(app) == responseAsResult(Response.text("ok")) && called)
      },
    ),

    suite("interceptPatch")(
      test("passes through with patcher") {
        val mw = Middleware.interceptPatch(
          interceptor = _ => None,
          patcher = r => r,
        )
        val app = mkRoute[Any](Handler.succeed(Response.text("ok"))) @@ mw
        assertTrue(runSingle(app) == responseAsResult(Response.text("ok")))
      },
    ),

    suite("when")(
      test("applies middleware when predicate is true") {
        val mw = Middleware.when(
          predicate = _.method == Method.GET,
          middleware = Middleware.interceptHandler(_ => Some(Halt(Response.forbidden))),
        )
        val app = mkRoute[Any](Handler.succeed(Response.text("ok"))) @@ mw
        assertTrue(runSingle(app).isInstanceOf[Halt])
      },
      test("skips middleware when predicate is false") {
        val mw = Middleware.when(
          predicate = _.method == Method.POST,
          middleware = Middleware.interceptHandler(_ => Some(Halt(Response.forbidden))),
        )
        val app = mkRoute[Any](Handler.succeed(Response.text("ok"))) @@ mw
        assertTrue(runSingle(app) == responseAsResult(Response.text("ok")))
      },
    ),

    suite("ifRequestThenElse")(
      test("applies onTrue for matching predicate") {
        val mw = Middleware.ifRequestThenElse(
          predicate = _.method == Method.GET,
          onTrue = Middleware.interceptHandler(_ => Some(Halt(Response.forbidden))),
          onFalse = Middleware.identity[Any],
        )
        val app = mkRoute[Any](Handler.succeed(Response.text("ok"))) @@ mw
        assertTrue(runSingle(app).isInstanceOf[Halt])
      },
      test("applies onFalse for non-matching predicate") {
        val mw = Middleware.ifRequestThenElse(
          predicate = _.method == Method.POST,
          onTrue = Middleware.interceptHandler(_ => Some(Halt(Response.forbidden))),
          onFalse = Middleware.identity[Any],
        )
        val app = mkRoute[Any](Handler.succeed(Response.text("ok"))) @@ mw
        assertTrue(runSingle(app) == responseAsResult(Response.text("ok")))
      },
    ),

    suite("ifRequestThen")(
      test("applies middleware for matching predicate") {
        val mw = Middleware.ifRequestThen(
          predicate = _.method == Method.GET,
          onTrue = Middleware.interceptHandler(_ => Some(Halt(Response.forbidden))),
        )
        val app = mkRoute[Any](Handler.succeed(Response.text("ok"))) @@ mw
        assertTrue(runSingle(app).isInstanceOf[Halt])
      },
      test("passes through for non-matching predicate") {
        val mw = Middleware.ifRequestThen(
          predicate = _.method == Method.POST,
          onTrue = Middleware.interceptHandler(_ => Some(Halt(Response.forbidden))),
        )
        val app = mkRoute[Any](Handler.succeed(Response.text("ok"))) @@ mw
        assertTrue(runSingle(app) == responseAsResult(Response.text("ok")))
      },
    ),

    suite("debug")(
      test("calls logger with request method") {
        val log = scala.collection.mutable.ArrayBuffer[String]()
        val mw = Middleware.debug(s => log += s)
        val app = mkRoute[Any](Handler.succeed(Response.text("ok"))) @@ mw
        runSingle(app)
        assertTrue(log.nonEmpty && log.exists(_.contains("GET")))
      },
    ),

    suite("timing")(
      test("calls reporter with elapsed time") {
        val log = scala.collection.mutable.ArrayBuffer[(zio.http.Method, String, Long)]()
        val mw = Middleware.timing((m, p, t) => log += ((m, p, t)))
        val app = mkRoute[Any](Handler.succeed(Response.text("ok"))) @@ mw
        runSingle(app)
        assertTrue(log.nonEmpty)
      },
    ),

    suite("methods")(
      test("dispatches per method") {
        val blockMw = Middleware.interceptHandler(_ => Some(Halt(Response.forbidden)))
        val mw = Middleware.methods(Method.POST -> blockMw)
        val app = mkRoute[Any](Handler.succeed(Response.text("ok"))) @@ mw
        val postResult = runSingle(app, Request.post(URL.root, Body.empty))
        val getResult = runSingle(app, Request.get(URL.root))
        assertTrue(postResult.isInstanceOf[Halt] && getResult == responseAsResult(Response.text("ok")))
      },
    ),

  )
}
