package zio.http

import zio.test._
import zio.blocks.context.Context
import zio.blocks.scope.Scope

object ContextHandlerSpec extends ZIOSpecDefault {

  final case class AuthCtx(userId: String)
  final case class ReqId(value: String)

  private val req: Request = Request.get(URL.root)

  def spec = suite("ContextHandler")(
    test("2-arg context handler compiles and runs") {
      val h      = contextHandler { (r: Request, auth: AuthCtx) =>
        Response.text(auth.userId)
      }
      val ctx    = Context(AuthCtx("alice"))
      val result = h.handle(req, ctx, (), Scope.global)
      assertTrue(result == zio.http.ResultType.responseAsResult(Response.text("alice")))
    },
    test("3-arg context handler reads multiple context types") {
      val h      = contextHandler { (r: Request, auth: AuthCtx, id: ReqId) =>
        Response.text(s"${auth.userId}/${id.value}")
      }
      val ctx    = Context(AuthCtx("bob"), ReqId("req-42"))
      val result = h.handle(req, ctx, (), Scope.global)
      assertTrue(result == zio.http.ResultType.responseAsResult(Response.text("bob/req-42")))
    },
    test("non-context shape reports compile error") {
      assertTrue(compiletime.testing.typeChecks("""
        contextHandler { (x: Int) => Response.ok }
      """) == false)
    },
  )
}
