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
        assertTrue(runSingle(app, corsReq) match {
          case r: Response => r.status == Status.Forbidden
          case _           => false
        })
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
      test("returns ServiceUnavailable on timeout") {
        val mw          = Middleware.timeout(10L)
        val slowHandler = Handler.extracted[Any, Any] { (_, _, _, _) =>
          java.util.concurrent.TimeUnit.MILLISECONDS.sleep(100)
          Response.text("too late")
        }
        val app         = mkRoute[Any](slowHandler) @@ mw
        assertTrue(runSingle(app) match {
          case r: Response                                        => r.status == Status.ServiceUnavailable
          case Halt(Response(Status.ServiceUnavailable, _, _, _)) => true
          case _                                                  => false
        })
      },
    ),
    suite("flashScope")(
      test("compiles and runs") {
        implicit val isNominalTypeFlashMap: IsNominalType[Middleware.FlashMap] =
          IsNominalType.derived[Middleware.FlashMap]
        val mw                                                                 = Middleware.flashScope()
        val app = mkRoute[Any](Handler.succeed(Response.text("ok"))) @@ mw.asInstanceOf[Middleware[Any, Any]]
        assertTrue(runSingle(app) match {
          case r: Response => r.status == Status.Ok
          case _           => false
        })
      },
    ),
    suite("serveDirectory")(
      test("falls through to downstream handler for non-existent file") {
        val mw     = Middleware.serveDirectory("/nonexistent")
        val app    = mkRoute[Any](Handler.succeed(Response.text("fallback"))) @@ mw
        val result = runSingle(app)
        assertTrue(result == responseAsResult(Response.text("fallback")))
      },
      test("serves a file from the directory") {
        val tmpFile = java.io.File.createTempFile("sd-test", ".html")
        try {
          java.nio.file.Files.writeString(tmpFile.toPath, "hello from serveDirectory")
          val dir = tmpFile.getParentFile.getCanonicalPath
          val mw  = Middleware.serveDirectory(dir)
          val req = Request.get(URL.root.copy(path = Path(tmpFile.getName)))
          val app = mkRoute[Any](Handler.succeed(Response.text("fallback"))) @@ mw
          val ok  = runSingle(app, req) match {
            case r: Response if r.status == Status.Ok => true
            case _                                    => false
          }
          assertTrue(ok)
        } finally {
          tmpFile.delete()
        }
      },
    ),
    suite("serveResources")(
      test("falls through to downstream handler for non-existent resource") {
        val mw     = Middleware.serveResources()
        val app    = mkRoute[Any](Handler.succeed(Response.text("fallback"))) @@ mw
        val result = runSingle(app)
        assertTrue(result == responseAsResult(Response.text("fallback")))
      },
      test("serves an embedded resource") {
        val mw   = Middleware.serveResources(basePath = "")
        val path = "zio/http/Middleware.class"
        val req  = Request.get(URL.root.copy(path = Path(path)))
        val app  = mkRoute[Any](Handler.succeed(Response.text("fallback"))) @@ mw
        val ok   = runSingle(app, req) match {
          case r: Response if r.status == Status.Ok => true
          case _                                    => false
        }
        assertTrue(ok)
      },
    ),
    suite("signCookies")(
      test("passes through requests without cookies") {
        val mw  = Middleware.signCookies("test-secret")
        val app = mkRoute[Any](Handler.succeed(Response.text("ok"))) @@ mw
        assertTrue(runSingle(app) == responseAsResult(Response.text("ok")))
      },
      test("passes through request with unsigned cookie") {
        val req = Request.get(URL.root).addHeader("Cookie", "session=abc123")
        val mw  = Middleware.signCookies("test-secret")
        val app = mkRoute[Any](Handler.succeed(Response.text("ok"))) @@ mw
        assertTrue(runSingle(app, req) == responseAsResult(Response.text("ok")))
      },
      test("passes through request with tampered signed cookie") {
        val req = Request.get(URL.root).addHeader("Cookie", "session=realvalue.tampered")
        val mw  = Middleware.signCookies("test-secret")
        val app = mkRoute[Any](Handler.succeed(Response.text("ok"))) @@ mw
        assertTrue(runSingle(app, req) == responseAsResult(Response.text("ok")))
      },
      test("accepts request with valid signed cookie") {
        val mac     = javax.crypto.Mac.getInstance("HmacSHA256")
        mac.init(new javax.crypto.spec.SecretKeySpec("test-secret".getBytes("UTF-8"), "HmacSHA256"))
        val sig     = java.util.Base64.getUrlEncoder.withoutPadding.encodeToString(
          mac.doFinal("session=abc123".getBytes("UTF-8")),
        )
        val signed  = s"abc123.$sig"
        val req     = Request.get(URL.root).addHeader("Cookie", s"session=$signed")
        val mw      = Middleware.signCookies("test-secret")
        val handler = Handler.extracted[Any, Any] { (req2, _, _, _) =>
          val cookieVal = req2.cookies.find(_.name == "session").map(_.value).getOrElse("")
          Response.text(cookieVal)
        }
        val app     = mkRoute[Any](handler) @@ mw
        val result  = runSingle(app, req)
        assertTrue(result == responseAsResult(Response.text("abc123")))
      },
      test("signs outgoing cookies and keeps response intact") {
        val mw     = Middleware.signCookies("test-secret")
        val app    = mkRoute[Any](Handler.succeed(Response.text("ok"))) @@ mw
        val result = runSingle(app)
        assertTrue(result match {
          case r: Response => r.status == Status.Ok
          case _           => false
        })
      },
      test("removes all cookies when none verify") {
        val req     = Request.get(URL.root).addHeader("Cookie", "session=bad1; token=bad2")
        val mw      = Middleware.signCookies("test-secret")
        val handler = Handler.extracted[Any, Any] { (req2, _, _, _) =>
          Response.text(req2.cookies.size.toString)
        }
        val app     = mkRoute[Any](handler) @@ mw
        assertTrue(runSingle(app, req) == responseAsResult(Response.text("0")))
      },
      test("removes invalid cookies while keeping valid ones") {
        // Create a valid signed cookie
        val mac         = javax.crypto.Mac.getInstance("HmacSHA256")
        mac.init(new javax.crypto.spec.SecretKeySpec("test-secret".getBytes("UTF-8"), "HmacSHA256"))
        val sig         = java.util.Base64.getUrlEncoder.withoutPadding.encodeToString(
          mac.doFinal("session=validValue".getBytes("UTF-8")),
        )
        val validCookie = s"session=validValue.$sig"
        // Mix valid + invalid cookies
        val req         = Request.get(URL.root).addHeader("Cookie", s"$validCookie; tampered=bad")
        val mw          = Middleware.signCookies("test-secret")
        val handler     = Handler.extracted[Any, Any] { (req2, _, _, _) =>
          Response.text(req2.cookies.size.toString)
        }
        val app         = mkRoute[Any](handler) @@ mw
        assertTrue(runSingle(app, req) == responseAsResult(Response.text("1")))
      },
    ),
  )
}
