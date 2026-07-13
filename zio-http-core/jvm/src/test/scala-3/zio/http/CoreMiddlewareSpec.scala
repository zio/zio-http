package zio.http

import zio.test._
import zio.blocks.context.{Context, IsNominalType}
import zio.blocks.scope.Scope
import zio.http.ResultType._

object CoreMiddlewareSpec extends ZIOSpecDefault {

  private val req: Request     = Request.get(URL.root)
  private val postReq: Request = Request.post(URL.root, Body.empty)

  private def mkRoute[Ctx](h: Handler[Ctx, Any]): Routes[Ctx] =
    Routes(Route(zio.blocks.endpoint.RoutePattern.GET, h))

  private def runSingle[Ctx](routes: Routes[Ctx], request: Request = req): Response | Halt =
    routes.routes.toList.head.handler.handle(request, Context.empty.asInstanceOf[Context[Ctx]], (), Scope.global)

  def spec = suite("CoreMiddleware")(
    suite("CORS")(
      test("allows request with valid origin") {
        val mw      = Middleware.cors()
        val app     = mkRoute[Any](Handler.succeed(Response.text("ok"))) @@ mw
        val corsReq = req.addHeader(Header.Origin.Value("https", "example.com", None))
        val result  = runSingle(app, corsReq)
        assertTrue(result match {
          case r: Response => r.status == Status.Ok
          case _           => false
        })
      },
      test("blocks request with invalid origin") {
        val config  = Middleware.CorsConfig(allowedOrigins = Set("https://trusted.com"))
        val mw      = Middleware.cors(config)
        val app     = mkRoute[Any](Handler.succeed(Response.text("ok"))) @@ mw
        val corsReq = req.addHeader(Header.Origin.Value("https", "evil.com", None))
        assertTrue(runSingle(app, corsReq).isInstanceOf[Halt])
      },
      test("responds to preflight OPTIONS request") {
        val mw         = Middleware.cors()
        val app        = mkRoute[Any](Handler.succeed(Response.text("ok"))) @@ mw
        val optionsReq = Request(Method.OPTIONS, URL.root, Headers.empty, Body.empty, Version.`HTTP/1.1`)
          .addHeader(Header.Origin.Value("https", "example.com", None))
          .addHeader(Header.AccessControlRequestMethod(Method.GET))
        val result     = runSingle(app, optionsReq)
        assertTrue(result match {
          case r: Response => r.status == Status.NoContent || r.status == Status.Ok
          case _           => false
        })
      },
    ),
    suite("requestLogging")(
      test("invokes logger for each request") {
        val log = scala.collection.mutable.ArrayBuffer[String]()
        val mw  = Middleware.requestLogging(s => log += s)
        val app = mkRoute[Any](Handler.succeed(Response.text("ok"))) @@ mw
        runSingle(app)
        assertTrue(log.nonEmpty && log.head.contains("GET"))
      },
    ),
    suite("timeout")(
      test("passes through fast requests") {
        val mw  = Middleware.timeout(5000L)
        val app = mkRoute[Any](Handler.succeed(Response.text("ok"))) @@ mw
        assertTrue(runSingle(app) == responseAsResult(Response.text("ok")))
      },
    ),
    suite("flashScope")(
      test("compiles and runs") {
        implicit val isNominalTypeFlashMap: IsNominalType[Middleware.FlashMap] =
          IsNominalType.derived[Middleware.FlashMap]
        val mw                                                                 = Middleware.flashScope()
        val app = mkRoute[Any](Handler.succeed(Response.text("ok"))) @@ mw.asInstanceOf[Middleware[Any, Any]]
        assertTrue(runSingle(app) == responseAsResult(Response.text("ok")))
      },
    ),
    suite("serveDirectory")(
      test("falls through to downstream handler for non-existent file") {
        val mw     = Middleware.serveDirectory("/nonexistent")
        val app    = mkRoute[Any](Handler.succeed(Response.text("fallback"))) @@ mw
        val result = runSingle(app)
        assertTrue(result == responseAsResult(Response.text("fallback")))
      },
    ),
    suite("serveResources")(
      test("falls through to downstream handler for non-existent resource") {
        val mw     = Middleware.serveResources()
        val app    = mkRoute[Any](Handler.succeed(Response.text("fallback"))) @@ mw
        val result = runSingle(app)
        assertTrue(result == responseAsResult(Response.text("fallback")))
      },
    ),
  )
}
