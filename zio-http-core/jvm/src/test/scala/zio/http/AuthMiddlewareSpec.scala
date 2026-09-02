package zio.http

import zio.test._
import zio.blocks.context.{Context, IsNominalType}
import zio.blocks.scope.Scope
import zio.http.ResultType._

object AuthMiddlewareSpec extends ZIOSpecDefault {

  final case class User(id: String, role: String)
  private val req: Request = Request.get(URL.root)

  private def mkRoute[Ctx](h: Handler[Ctx, Any]): Routes[Ctx] =
    Routes(Route(zio.blocks.endpoint.RoutePattern.GET, h))

  private def runSingle[Ctx](routes: Routes[Ctx], request: Request = req): Response | Halt =
    routes.routes.toList.head.handler.handle(request, Context.empty.asInstanceOf[Context[Ctx]], (), Scope.global)

  implicit val userIsNominal: IsNominalType[User] = IsNominalType.derived[User]

  def spec = suite("AuthMiddleware")(
    suite("basicAuth")(
      test("allows valid credentials") {
        val mw      = Middleware.basicAuth[String](validate =
          b => if (b.username == "admin" && b.password == "pass") Right("admin") else Left(Response.unauthorized),
        )
        val app     = mkRoute[Any](Handler.succeed(Response.text("ok"))) @@ mw
        val authReq = req.addHeader(Header.Authorization.Basic("admin", "pass"))
        assertTrue(runSingle(app, authReq) == responseAsResult(Response.text("ok")))
      },
      test("rejects invalid credentials") {
        val mw      = Middleware.basicAuth[String](validate =
          b => if (b.username == "admin" && b.password == "pass") Right("admin") else Left(Response.unauthorized),
        )
        val app     = mkRoute[Any](Handler.succeed(Response.text("ok"))) @@ mw
        val authReq = req.addHeader(Header.Authorization.Basic("admin", "wrong"))
        assertTrue(
          foldResult(runSingle(app, authReq))(
            r => r.status == Status.Unauthorized,
            h => h.response.status == Status.Unauthorized,
          ),
        )
      },
      test("rejects missing header") {
        val mw  = Middleware.basicAuth[String](validate = _ => Left(Response.unauthorized))
        val app = mkRoute[Any](Handler.succeed(Response.text("ok"))) @@ mw
        assertTrue(
          foldResult(runSingle(app))(
            r => r.status == Status.Unauthorized,
            h => h.response.status == Status.Unauthorized,
          ),
        )
      },
    ),
    suite("bearerAuth")(
      test("allows valid token") {
        val mw      = Middleware.bearerAuth[String](validate =
          t => if (t.token == "secret-token") Right("ok") else Left(Response.unauthorized),
        )
        val app     = mkRoute[Any](Handler.succeed(Response.text("ok"))) @@ mw
        val authReq = req.addHeader(Header.Authorization.Bearer("secret-token"))
        assertTrue(runSingle(app, authReq) == responseAsResult(Response.text("ok")))
      },
      test("rejects invalid token") {
        val mw      = Middleware.bearerAuth[String](validate =
          t => if (t.token == "secret-token") Right("ok") else Left(Response.unauthorized),
        )
        val app     = mkRoute[Any](Handler.succeed(Response.text("ok"))) @@ mw
        val authReq = req.addHeader(Header.Authorization.Bearer("bad-token"))
        assertTrue(
          foldResult(runSingle(app, authReq))(
            r => r.status == Status.Unauthorized,
            h => h.response.status == Status.Unauthorized,
          ),
        )
      },
    ),
    suite("customAuth")(
      test("injects User context on success") {
        val mw      = Middleware.customAuth[User](req => valueAsOutcome(User("alice", "admin")))
        val wrapped = mkRoute[Any](Handler.succeed(Response.text("ok"))) @@
          mw.asInstanceOf[Middleware[Any, Any]]
        assertTrue(runSingle(wrapped) == responseAsResult(Response.text("ok")))
      },
      test("rejects with Halt outcome on failure") {
        val mw      = Middleware.customAuth[User](_ => haltAsOutcome(Halt(Response.unauthorized)))
        val wrapped = mkRoute[Any](Handler.succeed(Response.text("ok"))) @@
          mw.asInstanceOf[Middleware[Any, Any]]
        assertTrue(runSingle(wrapped) == haltAsResult(Halt(Response.unauthorized)))
      },
    ),
    suite("customAuthProviding")(
      test("injects context for every request") {
        val mw      = Middleware.customAuth[User](req => valueAsOutcome(User("bob", "user")))
        val wrapped = mkRoute[Any](Handler.succeed(Response.text("ok"))) @@
          mw.asInstanceOf[Middleware[Any, Any]]
        assertTrue(runSingle(wrapped) == responseAsResult(Response.text("ok")))
      },
    ),
  )
}
