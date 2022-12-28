package zio.http

import zio._
import zio.test.Assertion._
import zio.test._

import java.io.IOException

object MiddlewareSpec extends ZIOSpecDefault with HExitAssertion {
  def spec: Spec[Any, Any] =
    suite("Middleware") {
      val increment = Middleware.codec[Int, Int](decoder = a => Right(a + 1), encoder = b => Right(b + 1))
      test("identity") {
        val http = Http.succeed(1)
        val app  = Middleware.identity[Unit, Int](http)
        assertZIO(app.toZIO(()))(equalTo(1))
      } +
        test("constant") {
          val mid = Middleware.fromHttp(Http.succeed("OK"))
          val app = Http.succeed(1) @@ mid
          assertZIO(app.toZIO(()))(equalTo("OK"))
        } +
        test("as") {
          val mid = Middleware.fromHttp(Http.succeed("Not OK")).as("OK")
          val app = Http.succeed(1) @@ mid
          assertZIO(app.toZIO(()))(equalTo("OK"))
        } +
        test("interceptZIO") {
          for {
            ref <- Ref.make(0)
            mid = Middleware.interceptZIO[Int, Int](i => ZIO.succeed(i * 10))((i, j) => ref.set(i + j))
            app = Http.identity[Int] @@ mid
            _ <- app.toZIO(1)
            i <- ref.get
          } yield assertTrue(i == 11)
        } +
        test("orElse") {
          val mid = Middleware.fail("left") <> Middleware.fail("right")
          val app = Http.fail(1) @@ mid
          assertZIO(app.toZIO(()).flip)(equalTo("right"))
        } +
        test("combine") {
          val mid1 = increment
          val mid2 = increment
          val mid  = mid1 andThen mid2
          val app  = Http.identity[Int] @@ mid
          assertZIO(app.toZIO(0))(equalTo(4))
        } +
        test("flatMap") {
          val mid = increment.flatMap(i => Middleware.succeed(i + 1))
          val app = Http.identity[Int] @@ mid
          assertZIO(app.toZIO(0))(equalTo(3))
        } +
        test("mapZIO") {
          val mid = increment.mapZIO(i => ZIO.succeed(i + 1))
          val app = Http.identity[Int] @@ mid
          assertZIO(app.toZIO(0))(equalTo(3))
        } +
        test("runBefore") {
          val mid = Middleware.identity[Any, Unit].runBefore(Console.printLine("A"))
          val app = Http.fromZIO(Console.printLine("B")) @@ mid
          assertZIO(app.toZIO(()) *> TestConsole.output)(equalTo(Vector("A\n", "B\n")))
        } +
        test("runAfter") {
          val mid = Middleware.identity[Any, Unit].runAfter(Console.printLine("B"))
          val app = Http.fromZIO(Console.printLine("A")) @@ mid
          assertZIO(app.toZIO(()) *> TestConsole.output)(equalTo(Vector("A\n", "B\n")))
        } +
        test("runBefore and runAfter") {
          val mid = Middleware.identity[Any, Unit].runBefore(Console.printLine("A")).runAfter(Console.printLine("C"))
          val app = Http.fromZIO(Console.printLine("B")) @@ mid
          assertZIO(app.toZIO(()) *> TestConsole.output)(equalTo(Vector("A\n", "B\n", "C\n")))
        } +
        test("race") {
          val mid = Middleware.succeed('A').delay(2 second) race Middleware.succeed("B").delay(1 second)
          val app = Http.succeed(1) @@ mid
          assertZIO(app.toZIO(()) <& TestClock.adjust(3 second))(equalTo("B"))
        } +
        suite("ifThenElse") {
          val mid = Middleware.ifThenElse[Int](_ > 5)(
            isTrue = i => Middleware.succeed(i + 1),
            isFalse = i => Middleware.succeed(i - 1),
          )
          test("isTrue") {
            val app = Http.identity[Int] @@ mid
            assertZIO(app.toZIO(10))(equalTo(11))
          } +
            test("isFalse") {
              val app = Http.identity[Int] @@ mid
              assertZIO(app.toZIO(1))(equalTo(0))
            }
        } +
        suite("ifThenElseZIO") {
          val mid = Middleware.ifThenElseZIO[Int](i => ZIO.succeed(i > 5))(
            isTrue = i => Middleware.succeed(i + 1),
            isFalse = i => Middleware.succeed(i - 1),
          )
          test("isTrue") {
            val app = Http.identity[Int] @@ mid
            assertZIO(app.toZIO(10))(equalTo(11))
          } +
            test("isFalse") {
              val app = Http.identity[Int] @@ mid
              assertZIO(app.toZIO(1))(equalTo(0))
            }
        } +
        suite("contramap") {
          val mid = Middleware.intercept[String, String](a => a + "Bar")((b, s) => b + s)
          test("contramap") {
            val app = Http.identity[String] @@ mid.contramap[Int] { i => s"${i}Foo" }
            assertZIO(app.toZIO(0))(equalTo("0Foo0FooBar"))
          } +
            test("contramapZIO") {
              val app = Http.identity[String] @@ mid.contramapZIO[Int] { i => ZIO.succeed(s"${i}Foo") }
              assertZIO(app.toZIO(0))(equalTo("0Foo0FooBar"))
            }
        } +
        suite("when") {
          val mid = Middleware.transform[Int, Int](
            in = _ + 1,
            out = _ + 1,
          )
          test("condition is true") {
            val app = Http.identity[Int] @@ mid.when((_: Any) => true)
            assertZIO(app.toZIO(10))(equalTo(12))
          } +
            test("condition is false") {
              val app = Http.identity[Int] @@ mid.when((_: Any) => false)
              assertZIO(app.toZIO(1))(equalTo(1))
            }
        } +
        suite("whenZIO") {
          val mid = Middleware.transform[Int, Int](
            in = _ + 1,
            out = _ + 1,
          )
          test("condition is true") {
            val app = Http.identity[Int] @@ mid.whenZIO((_: Any) => ZIO.succeed(true))
            assertZIO(app.toZIO(10))(equalTo(12))
          } +
            test("condition is false") {
              val app = Http.identity[Int] @@ mid.whenZIO((_: Any) => ZIO.succeed(false))
              assertZIO(app.toZIO(1))(equalTo(1))
            }
        } +
        suite("codec") {
          test("codec success") {
            val mid = Middleware.codec[String, Int](a => Right(a.toInt), b => Right(b.toString))
            val app = Http.identity[Int] @@ mid
            assertZIO(app.toZIO("1"))(equalTo("1"))
          } +
            test("decoder failure") {
              val mid = Middleware.codec[String, Int](a => Left(a), b => Right(b.toString))
              val app = Http.identity[Int] @@ mid
              assertZIO(app.toZIO("a").exit)(fails(anything))
            } +
            test("encoder failure") {
              val mid = Middleware.codec[String, Int](a => Right(a.toInt), b => Left(b.toString))
              val app = Http.identity[Int] @@ mid
              assertZIO(app.toZIO("1").exit)(fails(anything))
            }
        } +
        suite("codecHttp")(
          test("codec success") {
            val a   = Http.fromFunction[Int] { v => v.toString }
            val b   = Http.fromFunction[String] { v => v.toInt }
            val mid = Middleware.codecHttp[String, Int](b, a)
            val app = Http.identity[Int] @@ mid
            assertZIO(app.toZIO("2"))(equalTo("2"))
          },
          test("encoder failure") {
            val mid = Middleware.codecHttp[String, Int](Http.succeed(1), Http.fail("fail"))
            val app = Http.identity[Int] @@ mid
            assertZIO(app.toZIO("2").exit)(fails(anything))
          },
          test("decoder failure") {
            val mid = Middleware.codecHttp[String, Int](Http.fail("fail"), Http.succeed(2))
            val app = Http.identity[Int] @@ mid
            assertZIO(app.toZIO("2").exit)(fails(anything))
          },
        )
    }
}
