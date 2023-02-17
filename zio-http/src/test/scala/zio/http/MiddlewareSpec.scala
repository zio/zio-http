package zio.http

import java.io.IOException

import zio._
import zio.test.Assertion._
import zio.test._

object MiddlewareSpec extends ZIOSpecDefault with ExitAssertion {
  private val increment = Middleware.codec[Int, Int](decoder = a => Right(a + 1), encoder = b => Right(b + 1))

  def spec: Spec[Any, Any] =
    suite("Middleware")(
      test("combine") {
        val mid1 = increment
        val mid2 = increment
        val app  = Handler.identity[Int] @@ mid1 @@ mid2
        assertZIO(app.runZIO(0))(equalTo(4))
      },
      test("runBefore") {
        val mid = Middleware.runBefore(Console.printLine("A"))
        val app = Handler.fromFunctionZIO((_: Request) => Console.printLine("B").as(Response.ok)) @@ mid
        assertZIO(app.runZIO(Request.get(URL.root)) *> TestConsole.output)(equalTo(Vector("A\n", "B\n")))
      },
      test("runAfter") {
        val mid = Middleware.runAfter(Console.printLine("B"))
        val app = Handler.fromFunctionZIO((_: Request) => Console.printLine("A").as(Response.ok)) @@ mid
        assertZIO(app.runZIO(Request.get(URL.root)) *> TestConsole.output)(equalTo(Vector("A\n", "B\n")))
      },
      test("runBefore and runAfter") {
        val mid: RequestHandlerMiddleware[Any, IOException] =
          Middleware.runBefore(Console.printLine("A")) ++ Middleware.runAfter(Console.printLine("C"))
        val app = Handler.fromFunctionZIO((_: Request) => Console.printLine("B").as(Response.ok)) @@ mid
        assertZIO(app.runZIO(Request.get(URL.root)) *> TestConsole.output)(equalTo(Vector("A\n", "B\n", "C\n")))
      },
      suite("when") {
        val mid = Middleware
          .transform[Int, Int](
            in = _ + 1,
            out = _ + 1,
          )
          .toMiddleware

        test("condition is true") {
          val app = Handler.identity[Int] @@ mid.when((_: Any) => true)
          assertZIO(app.runZIO(10))(equalTo(12))
        } +
          test("condition is false") {
            val app = Handler.identity[Int] @@ mid.when((_: Any) => false)
            assertZIO(app.runZIO(1))(equalTo(1))
          }
      },
      suite("whenZIO") {
        val mid = Middleware
          .transform[Int, Int](
            in = _ + 1,
            out = _ + 1,
          )
          .toMiddleware

        test("condition is true") {
          val app = Handler.identity[Int] @@ mid.whenZIO((_: Any) => ZIO.succeed(true))
          assertZIO(app.runZIO(10))(equalTo(12))
        } +
          test("condition is false") {
            val app = Handler.identity[Int] @@ mid.whenZIO((_: Any) => ZIO.succeed(false))
            assertZIO(app.runZIO(1))(equalTo(1))
          }
      },
      suite("codec")(
        test("codec success") {
          val mid = Middleware.codec[String, Int](a => Right(a.toInt), b => Right(b.toString))
          val app = Handler.identity[Int] @@ mid
          assertZIO(app.runZIO("1"))(equalTo("1"))
        },
        test("decoder failure") {
          val mid = Middleware.codec[String, Int](a => Left(a), b => Right(b.toString))
          val app = Handler.identity[Int] @@ mid
          assertZIO(app.runZIO("a").exit)(fails(anything))
        },
        test("encoder failure") {
          val mid = Middleware.codec[String, Int](a => Right(a.toInt), b => Left(b.toString))
          val app = Handler.identity[Int] @@ mid
          assertZIO(app.runZIO("1").exit)(fails(anything))
        },
      ),
      suite("codecHttp")(
        test("codec success") {
          val a   = Handler.fromFunction[Int] { v => v.toString }
          val b   = Handler.fromFunction[String] { v => v.toInt }
          val mid = Middleware.codecHttp[String, Int](b, a)
          val app = Handler.identity[Int] @@ mid
          assertZIO(app.runZIO("2"))(equalTo("2"))
        },
        test("encoder failure") {
          val mid = Middleware.codecHttp[String, Int](Handler.succeed(1), Handler.fail("fail"))
          val app = Handler.identity[Int] @@ mid
          assertZIO(app.runZIO("2").exit)(fails(anything))
        },
        test("decoder failure") {
          val mid = Middleware.codecHttp[String, Int](Handler.fail("fail"), Handler.succeed(2))
          val app = Handler.identity[Int] @@ mid
          assertZIO(app.runZIO("2").exit)(fails(anything))
        },
      ),
    )
}
