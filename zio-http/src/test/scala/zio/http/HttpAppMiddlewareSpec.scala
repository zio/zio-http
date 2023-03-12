package zio.http

import java.io.IOException

import zio._
import zio.test.Assertion._
import zio.test._

import zio.http.RequestHandlerMiddleware.WithOut
import zio.http.model.Method

object HttpAppMiddlewareSpec extends ZIOSpecDefault with ExitAssertion {

  def spec: Spec[Any, Any] =
    suite("HttpAppMiddleware")(
      test("combine") {
        for {
          ref <- Ref.make(0)
          mid1 = HttpAppMiddleware.runBefore(ref.update(_ + 1))
          mid2 = HttpAppMiddleware.runBefore(ref.update(_ + 2))
          app1 = Handler.ok @@ mid1 @@ mid2
          app2 = Handler.ok @@ (mid1 ++ mid2)
          _       <- app1.runZIO(Request.get(URL.root))
          result1 <- ref.get
          _       <- app2.runZIO(Request.get(URL.root))
          result2 <- ref.get
        } yield assertTrue(result1 == 3, result2 == 6)
      },
      test("runBefore") {
        val mid = HttpAppMiddleware.runBefore(Console.printLine("A"))
        val app = Handler.fromFunctionZIO((_: Request) => Console.printLine("B").as(Response.ok)) @@ mid
        assertZIO(app.runZIO(Request.get(URL.root)) *> TestConsole.output)(equalTo(Vector("A\n", "B\n")))
      },
      test("runAfter") {
        val mid = HttpAppMiddleware.runAfter(Console.printLine("B"))
        val app = Handler.fromFunctionZIO((_: Request) => Console.printLine("A").as(Response.ok)) @@ mid
        assertZIO(app.runZIO(Request.get(URL.root)) *> TestConsole.output)(equalTo(Vector("A\n", "B\n")))
      },
      test("runBefore and runAfter") {
        val mid =
          HttpAppMiddleware.runBefore(Console.printLine("A")) ++ HttpAppMiddleware.runAfter(Console.printLine("C"))
        val app = Handler.fromFunctionZIO((_: Request) => Console.printLine("B").as(Response.ok)) @@ mid
        assertZIO(app.runZIO(Request.get(URL.root)) *> TestConsole.output)(equalTo(Vector("A\n", "B\n", "C\n")))
      },
      test("when") {
        for {
          ref <- Ref.make(0)
          mid  = HttpAppMiddleware.runBefore(ref.update(_ + 1)).when(_.method == Method.GET)
          app1 = Handler.ok @@ mid
          _       <- app1.runZIO(Request.get(URL.root))
          result1 <- ref.get
          _       <- app1.runZIO(Request.default(Method.HEAD, URL.root))
          result2 <- ref.get
        } yield assertTrue(result1 == 1, result2 == 1)
      },
      test("whenZIO") {
        for {
          ref <- Ref.make(0)
          mid  = HttpAppMiddleware.runBefore(ref.update(_ + 1)).whenZIO(req => ZIO.succeed(req.method == Method.GET))
          app1 = Handler.ok @@ mid
          _       <- app1.runZIO(Request.get(URL.root))
          result1 <- ref.get
          _       <- app1.runZIO(Request.default(Method.HEAD, URL.root))
          result2 <- ref.get
        } yield assertTrue(result1 == 1, result2 == 1)
      },
    )
}
