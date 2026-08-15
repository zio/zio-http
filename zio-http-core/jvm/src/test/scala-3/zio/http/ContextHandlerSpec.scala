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
    test("24-arg context handler (>22) executes correctly") {
      val h      = contextHandler {
        (
          r: Request,
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
          c23: C23,
        ) =>
          Response.text(s"${c1.v}/${c23.v}")
      }
      val ctx0   = Context.empty
      val ctx    = ctx0
        .add(C1("1"))
        .add(C2("2"))
        .add(C3("3"))
        .add(C4("4"))
        .add(C5("5"))
        .add(C6("6"))
        .add(C7("7"))
        .add(C8("8"))
        .add(C9("9"))
        .add(C10("10"))
        .add(C11("11"))
        .add(C12("12"))
        .add(C13("13"))
        .add(C14("14"))
        .add(C15("15"))
        .add(C16("16"))
        .add(C17("17"))
        .add(C18("18"))
        .add(C19("19"))
        .add(C20("20"))
        .add(C21("21"))
        .add(C22("22"))
        .add(C23("23"))
      val result = h.handle(req, ctx, (), Scope.global)
      assertTrue(result == zio.http.ResultType.responseAsResult(Response.text("1/23")))
    },
  )

  final case class C1(v: String); final case class C2(v: String); final case class C3(v: String)
  final case class C4(v: String); final case class C5(v: String); final case class C6(v: String)
  final case class C7(v: String); final case class C8(v: String); final case class C9(v: String)
  final case class C10(v: String); final case class C11(v: String); final case class C12(v: String)
  final case class C13(v: String); final case class C14(v: String); final case class C15(v: String)
  final case class C16(v: String); final case class C17(v: String); final case class C18(v: String)
  final case class C19(v: String); final case class C20(v: String); final case class C21(v: String)
  final case class C22(v: String); final case class C23(v: String)
}
