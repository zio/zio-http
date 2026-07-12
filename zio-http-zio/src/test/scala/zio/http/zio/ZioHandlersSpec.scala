package zio.http.zio

import zio._
import zio.test._
import zio.blocks.context.Context
import zio.blocks.scope.Scope
import _root_.zio.http.{Body, Halt, Handler, Method, Request, Response, Status, URL, handler}
import _root_.zio.http.ResultType._
import _root_.zio.http.zio.ZioHandlers._

object ZioHandlersSpec extends ZIOSpecDefault {

  private val req: Request = Request.get(URL.root)

  def spec = suite("ZioHandlers")(
    suite("zioRequestHandler — Request => ZIO[Any, Response, Response]")(
      test("success path returns Response") {
        val zioFn: Request => ZIO[Any, Response, Response] = _ => ZIO.succeed(Response.ok)
        val h      = Handler(zioFn)
        val result = h.handle(req, Context.empty, (), Scope.global)
        assertTrue(result == responseAsResult(Response.ok))
      },
      test("error path (ZIO.fail) produces Halt") {
        val forbidden                                      = Response(Status.Forbidden)
        val zioFn: Request => ZIO[Any, Response, Response] = _ => ZIO.fail(forbidden)
        val h      = Handler(zioFn)
        val result = h.handle(req, Context.empty, (), Scope.global)
        assertTrue(result == haltAsResult(Halt(forbidden)))
      },
      test("handler receives the request") {
        val zioFn: Request => ZIO[Any, Response, Response] = r =>
          if (r.method == Method.GET) ZIO.succeed(Response.ok)
          else ZIO.succeed(Response.notFound)
        val h       = Handler(zioFn)
        val getRes  = h.handle(Request.get(URL.root), Context.empty, (), Scope.global)
        val postRes = h.handle(Request.post(URL.root, Body.empty), Context.empty, (), Scope.global)
        assertTrue(
          getRes == responseAsResult(Response.ok),
          postRes == responseAsResult(Response.notFound),
        )
      },
      test("different responses based on ZIO effect") {
        val created                                        = Response(Status.Created)
        val zioFn: Request => ZIO[Any, Response, Response] = _ => ZIO.succeed(created)
        val h      = Handler(zioFn)
        val result = h.handle(req, Context.empty, (), Scope.global)
        assertTrue(result == responseAsResult(created))
      },
      test("ZIO.fail with unauthorized produces correct Halt") {
        val unauthorized                                   = Response(Status.Unauthorized)
        val zioFn: Request => ZIO[Any, Response, Response] = _ => ZIO.fail(unauthorized)
        val h      = Handler(zioFn)
        val result = h.handle(req, Context.empty, (), Scope.global)
        assertTrue(result == haltAsResult(Halt(unauthorized)))
      },
    ),
    suite("zioInfallibleHandler — ZIO[Any, Nothing, Response]")(
      test("infallible ZIO.succeed returns Response") {
        val effect: ZIO[Any, Nothing, Response] = ZIO.succeed(Response.ok)
        val h      = Handler(effect)
        val result = h.handle(req, Context.empty, (), Scope.global)
        assertTrue(result == responseAsResult(Response.ok))
      },
      test("infallible ZIO returns non-ok response") {
        val effect: ZIO[Any, Nothing, Response] = ZIO.succeed(Response(Status.NotFound))
        val h      = Handler(effect)
        val result = h.handle(req, Context.empty, (), Scope.global)
        assertTrue(result == responseAsResult(Response(Status.NotFound)))
      },
      test("infallible ZIO.never would block but ZIO.succeed works") {
        val effect: ZIO[Any, Nothing, Response] = ZIO.succeed(Response(Status.Created))
        val h      = Handler(effect)
        val result = h.handle(req, Context.empty, (), Scope.global)
        assertTrue(result == responseAsResult(Response(Status.Created)))
      },
    ),
    suite("handler() package function with ZIO implicits")(
      test("handler(zioEffect) via zioInfallibleHandler") {
        val effect: ZIO[Any, Nothing, Response] = ZIO.succeed(Response.ok)
        val h      = handler(effect)
        val result = h.handle(req, Context.empty, (), Scope.global)
        assertTrue(result == responseAsResult(Response.ok))
      },
      test("handler(zioFn) via zioRequestHandler") {
        val fn: Request => ZIO[Any, Response, Response] = _ => ZIO.succeed(Response.ok)
        val h      = handler(fn)
        val result = h.handle(req, Context.empty, (), Scope.global)
        assertTrue(result == responseAsResult(Response.ok))
      },
    ),
  )
}
