package zio.http

import java.io.IOException

import zio._
import zio.test.Assertion._
import zio.test._

import zio.http.RequestHandlerMiddleware.WithOut

object HttpAppMiddlewareSpec$ extends ZIOSpecDefault with ExitAssertion {

  def spec: Spec[Any, Any] =
    suite("Middleware")(
      // TODO
//      test("combine") {
//        val mid1 = increment
//        val mid2 = increment
//        val app  = Handler.identity[Int] @@ mid1 @@ mid2
//        assertZIO(app.runZIO(0))(equalTo(4))
//      },
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
      // TODO: these are not working now because HanderAspect.Mono#toMiddleware is not a Middleware.Mono and when is only defined on Mono
//      suite("when") {
//        val mid: Middleware.Mono[Nothing, Any, Nothing, Any, RuntimeFlags, RuntimeFlags, RuntimeFlags, RuntimeFlags] =
//          Middleware
//            .transform[Int, Int](
//              in = _ + 1,
//              out = _ + 1,
//            )
//            .toMiddleware
//
//        test("condition is true") {
//          val app = Handler.identity[Int] @@ mid.when((_: Any) => true)
//          assertZIO(app.runZIO(10))(equalTo(12))
//        } +
//          test("condition is false") {
//            val app = Handler.identity[Int] @@ mid.when((_: Any) => false)
//            assertZIO(app.runZIO(1))(equalTo(1))
//          }
//      },
//      suite("whenZIO") {
//        val mid = Middleware
//          .transform[Int, Int](
//            in = _ + 1,
//            out = _ + 1,
//          )
//          .toMiddleware
//
//        test("condition is true") {
//          val app = Handler.identity[Int] @@ mid.whenZIO((_: Any) => ZIO.succeed(true))
//          assertZIO(app.runZIO(10))(equalTo(12))
//        } +
//          test("condition is false") {
//            val app = Handler.identity[Int] @@ mid.whenZIO((_: Any) => ZIO.succeed(false))
//            assertZIO(app.runZIO(1))(equalTo(1))
//          }
//      },
//      suite("codec")(
//        test("codec success") {
//          val mid = Middleware.codec[String, Int](a => Right(a.toInt), b => Right(b.toString))
//          val app = Handler.identity[Int] @@ mid
//          assertZIO(app.runZIO("1"))(equalTo("1"))
//        },
//        test("decoder failure") {
//          val mid = Middleware.codec[String, Int](a => Left(a), b => Right(b.toString))
//          val app = Handler.identity[Int] @@ mid
//          assertZIO(app.runZIO("a").exit)(fails(anything))
//        },
//        test("encoder failure") {
//          val mid = Middleware.codec[String, Int](a => Right(a.toInt), b => Left(b.toString))
//          val app = Handler.identity[Int] @@ mid
//          assertZIO(app.runZIO("1").exit)(fails(anything))
//        },
//      ),
    )
}
