package zio.http

import zio.test._
import zio.blocks.context.{Context, IsNominalType}
import zio.blocks.scope.Scope
import zio.http.ResultType._

object FinalMiddlewareSpec extends ZIOSpecDefault {

  private val req: Request = Request.get(URL.root)

  private def mkRoute[Ctx](h: Handler[Ctx, Any]): Routes[Ctx] =
    Routes(Route(zio.blocks.endpoint.RoutePattern.GET, h))

  private def runSingle[Ctx](routes: Routes[Ctx], request: Request = req): Response | Halt =
    routes.routes.toList.head.handler.handle(request, Context.empty.asInstanceOf[Context[Ctx]], (), Scope.global)

  def spec = suite("FinalMiddleware")(
    suite("addHeader")(
      test("adds header to response") {
        val mw     = Middleware.addHeader("X-Custom", "test")
        val app    = mkRoute[Any](Handler.succeed(Response.text("ok"))) @@ mw
        val result = runSingle(app)
        assertTrue(result match {
          case r: Response => r.headers.has("X-Custom")
          case _           => false
        })
      },
    ),
    suite("updateHeaders")(
      test("modifies response headers") {
        val mw     = Middleware.updateHeaders(_.add("X-Modified", "yes"))
        val app    = mkRoute[Any](Handler.succeed(Response.text("ok"))) @@ mw
        val result = runSingle(app)
        assertTrue(result match {
          case r: Response => r.headers.has("X-Modified")
          case _           => false
        })
      },
    ),
    suite("removeHeader")(
      test("removes response header") {
        val mw     = Middleware.removeHeader("content-type")
        val app    = mkRoute[Any](Handler.succeed(Response.text("ok"))) @@ mw
        val result = runSingle(app)
        assertTrue(result match {
          case r: Response => !r.headers.has("content-type")
          case _           => false
        })
      },
    ),
    suite("appendPath")(
      test("appends segment to request path") {
        val mw      = Middleware.appendPath("extra")
        val handler = Handler.extracted[Any, Any] { (req2, _, _, _) =>
          Response.text(req2.url.path.toString)
        }
        val app     = mkRoute[Any](handler) @@ mw
        assertTrue(runSingle(app, Request.get(URL.root)) == responseAsResult(Response.text("/extra")))
      },
    ),
    suite("prependPath")(
      test("prepends segment to request path") {
        val mw      = Middleware.prependPath("prefix")
        val handler = Handler.extracted[Any, Any] { (req2, _, _, _) =>
          Response.text(req2.url.path.toString)
        }
        val app     = mkRoute[Any](handler) @@ mw
        val req     = Request.get(URL.root / "api")
        assertTrue(runSingle(app, req) == responseAsResult(Response.text("/prefix/api")))
      },
    ),
    suite("dropTrailingSlash")(
      test("drops trailing slash from path") {
        val mw      = Middleware.dropTrailingSlash
        val handler = Handler.extracted[Any, Any] { (req2, _, _, _) =>
          Response.text(req2.url.path.toString)
        }
        val app     = mkRoute[Any](handler) @@ mw
        val req     = Request.get(URL.root.copy(path = Path.empty / ""))
        assertTrue(runSingle(app, req) == responseAsResult(Response.text("")))
      },
    ),
    suite("runBefore")(
      test("passes through when effect returns None") {
        val mw  = Middleware.runBefore(_ => None)
        val app = mkRoute[Any](Handler.succeed(Response.text("ok"))) @@ mw
        assertTrue(runSingle(app) == responseAsResult(Response.text("ok")))
      },
    ),
    suite("runAfter")(
      test("modifies response via after-effect") {
        val mw  = Middleware.runAfter((_, _) => Response.text("modified"))
        val app = mkRoute[Any](Handler.succeed(Response.text("ok"))) @@ mw
        assertTrue(runSingle(app) == responseAsResult(Response.text("modified")))
      },
    ),
    suite("redirect")(
      test("returns redirect response") {
        val mw  = Middleware.redirect(Status.fromInt(302), "/new-location")
        val app = mkRoute[Any](Handler.succeed(Response.text("ok"))) @@ mw
        assertTrue(runSingle(app) match {
          case h: Halt => h.response.status == Status.Found && h.response.headers.has("Location")
          case _       => false
        })
      },
    ),
    suite("status")(
      test("intercepts matching status") {
        val mw  = Middleware.status(s => s == Status.Ok, _ => Response.text("intercepted"))
        val app = mkRoute[Any](Handler.succeed(Response.text("ok"))) @@ mw
        assertTrue(runSingle(app) match {
          case r: Response => r.body == Response.text("intercepted").body
          case _           => false
        })
      },
    ),
    suite("requestDump / responseDump")(
      test("requestDump runs without error") {
        val mw  = Middleware.requestDump(_ => ())
        val app = mkRoute[Any](Handler.succeed(Response.text("ok"))) @@ mw
        assertTrue(runSingle(app) == responseAsResult(Response.text("ok")))
      },
      test("responseDump runs without error") {
        val mw  = Middleware.responseDump(_ => ())
        val app = mkRoute[Any](Handler.succeed(Response.text("ok"))) @@ mw
        assertTrue(runSingle(app) == responseAsResult(Response.text("ok")))
      },
    ),
  )
}
