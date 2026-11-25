package zio.http

import zio.test._
import zio.blocks.context.Context
import zio.blocks.scope.Scope
import zio.http.ResultType._

object HandlerV4Spec extends ZIOSpecDefault {

  private val req: Request = Request.get(URL.root)

  def spec = suite("HandlerV4")(
    suite("Handler.succeed")(
      test("returns ok response for any request") {
        val h      = Handler.succeed(Response.ok)
        val result = h.handle(req, Context.empty, (), Scope.global)
        assertTrue(result == responseAsResult(Response.ok))
      },
      test("returns 404 when constructed with NotFound") {
        val h      = Handler.succeed(Response(Status.NotFound))
        val result = h.handle(req, Context.empty, (), Scope.global)
        assertTrue(result == responseAsResult(Response(Status.NotFound)))
      },
      test("ignores request content") {
        val h       = Handler.succeed(Response.ok)
        val postReq = Request.post(URL.root, Body.empty)
        val result  = h.handle(postReq, Context.empty, (), Scope.global)
        assertTrue(result == responseAsResult(Response.ok))
      },
    ),
    suite("Handler.apply via ToHandler")(
      test("constructs from Response using responseIsHandler") {
        val h      = Handler(Response.ok)
        val result = h.handle(req, Context.empty, (), Scope.global)
        assertTrue(result == responseAsResult(Response.ok))
      },
      test("constructs from Halt using haltIsHandler") {
        val halt   = Halt(Response(Status.Forbidden))
        val h      = Handler(halt)
        val result = h.handle(req, Context.empty, (), Scope.global)
        assertTrue(result == haltAsResult(halt))
      },
      test("constructs from handler itself via handlerIsHandler") {
        val inner  = Handler.succeed(Response.ok)
        val h      = Handler(inner)
        val result = h.handle(req, Context.empty, (), Scope.global)
        assertTrue(result == responseAsResult(Response.ok))
      },
      test("constructs from request function via requestFunctionIsHandler") {
        val fn: Request => Response | Halt = _ => responseAsResult(Response.ok)
        val h                              = Handler(fn)
        val result                         = h.handle(req, Context.empty, (), Scope.global)
        assertTrue(result == responseAsResult(Response.ok))
      },
      test("constructs from thunk via thunkIsHandler") {
        val fn: () => Response | Halt = () => responseAsResult(Response.ok)
        val h                         = Handler(fn)
        val result                    = h.handle(req, Context.empty, (), Scope.global)
        assertTrue(result == responseAsResult(Response.ok))
      },
    ),
    suite("handler() package function")(
      test("handler(Response) compiles and works") {
        val h      = handler(Response.ok)
        val result = h.handle(req, Context.empty, (), Scope.global)
        assertTrue(result == responseAsResult(Response.ok))
      },
      test("handler { req => result } works") {
        val h      = handler { (_: Request) => responseAsResult(Response.ok) }
        val result = h.handle(req, Context.empty, (), Scope.global)
        assertTrue(result == responseAsResult(Response.ok))
      },
      test("handler { () => result } works") {
        val h      = handler { () => responseAsResult(Response.ok) }
        val result = h.handle(req, Context.empty, (), Scope.global)
        assertTrue(result == responseAsResult(Response.ok))
      },
      test("handler(Halt) compiles and works") {
        val halt   = Halt(Response(Status.Unauthorized))
        val h      = handler(halt)
        val result = h.handle(req, Context.empty, (), Scope.global)
        assertTrue(result == haltAsResult(halt))
      },
    ),
    suite("Halt")(
      test("Halt wraps response correctly") {
        val resp = Response(Status.Forbidden)
        val halt = Halt(resp)
        assertTrue(halt.response == resp)
      },
      test("Halt.response on forbidden") {
        val halt = Halt(Response.forbidden)
        assertTrue(halt.response.status == Status.Forbidden)
      },
      test("handler returning Halt produces halt result") {
        val forbidden = Response(Status.Forbidden)
        val h         = Handler(Halt(forbidden))
        val result    = h.handle(req, Context.empty, (), Scope.global)
        assertTrue(result == haltAsResult(Halt(forbidden)))
      },
      test("Halt result differs from response result") {
        val resp = Response.ok
        val halt = Halt(resp)
        val rRes = responseAsResult(resp)
        val hRes = haltAsResult(halt)
        assertTrue(rRes != hRes)
      },
    ),
    suite("Handler.handle invocation")(
      test("handle with Context.empty and Scope.global succeeds") {
        val h      = Handler.succeed(Response(Status.Created))
        val result = h.handle(req, Context.empty, (), Scope.global)
        assertTrue(result == responseAsResult(Response(Status.Created)))
      },
      test("request function handler receives the request") {
        val fn: Request => Response | Halt = r =>
          if (r.method == Method.GET) responseAsResult(Response.ok)
          else responseAsResult(Response.notFound)
        val h                              = Handler(fn)
        val getReq                         = Request.get(URL.root)
        val postReq                        = Request.post(URL.root, Body.empty)
        val getRes                         = h.handle(getReq, Context.empty, (), Scope.global)
        val postRes                        = h.handle(postReq, Context.empty, (), Scope.global)
        assertTrue(
          getRes == responseAsResult(Response.ok),
          postRes == responseAsResult(Response.notFound),
        )
      },
    ),
  )
}
