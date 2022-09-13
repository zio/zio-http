package zio.http

import zio._
import zio.test.Assertion._
import zio.test.{TestClock, TestConsole, ZIOSpecDefault, assert, assertZIO}

object MiddlewareSpec extends ZIOSpecDefault with HExitAssertion {
  def spec = suite("Middleware") {
    val increment = Middleware.codec[Int, Int](decoder = a => Right(a + 1), encoder = b => Right(b + 1))
    test("identity") {
      val http = Http.empty
      val app  = Middleware.identity(http)
      assertZIO(app(()).either)(isLeft(isNone))
    } +
      test("identity - 2") {
        val http = Http.succeed(1)
        val app  = Middleware.identity[Unit, Int](http)
        assertZIO(app(()))(equalTo(1))
      } +
      test("empty") {
        val mid = Middleware.empty
        val app = Http.succeed(1) @@ mid
        assertZIO(app(()).either)(isLeft(isNone))
      } +
      test("constant") {
        val mid = Middleware.fromHttp(Http.succeed("OK"))
        val app = Http.succeed(1) @@ mid
        assertZIO(app(()))(equalTo("OK"))
      } +
      test("as") {
        val mid = Middleware.fromHttp(Http.succeed("Not OK")).as("OK")
        val app = Http.succeed(1) @@ mid
        assertZIO(app(()))(equalTo("OK"))
      } +
      test("interceptZIO") {
        for {
          ref <- Ref.make(0)
          mid = Middleware.interceptZIO[Int, Int](i => ZIO.succeed(i * 10))((i, j) => ref.set(i + j))
          app = Http.identity[Int] @@ mid
          _ <- app(1)
          i <- ref.get
        } yield assert(i)(equalTo(11))
      } +
      test("orElse") {
        val mid = Middleware.fail("left") <> Middleware.fail("right")
        val app = Http.empty @@ mid
        assertZIO(app(()).flip)(isSome(equalTo("right")))
      } +
      test("combine") {
        val mid1 = increment
        val mid2 = increment
        val mid  = mid1 andThen mid2
        val app  = Http.identity[Int] @@ mid
        assertZIO(app(0))(equalTo(4))
      } +
      test("flatMap") {
        val mid = increment.flatMap(i => Middleware.succeed(i + 1))
        val app = Http.identity[Int] @@ mid
        assertZIO(app(0))(equalTo(3))
      } +
      test("mapZIO") {
        val mid = increment.mapZIO(i => ZIO.succeed(i + 1))
        val app = Http.identity[Int] @@ mid
        assertZIO(app(0))(equalTo(3))
      } +
      test("runBefore") {
        val mid = Middleware.identity[Any, Unit].runBefore(Console.printLine("A"))
        val app = Http.fromZIO(Console.printLine("B")) @@ mid
        assertZIO(app(()) *> TestConsole.output)(equalTo(Vector("A\n", "B\n")))
      } +
      test("runAfter") {
        val mid = Middleware.identity[Any, Unit].runAfter(Console.printLine("B"))
        val app = Http.fromZIO(Console.printLine("A")) @@ mid
        assertZIO(app(()) *> TestConsole.output)(equalTo(Vector("A\n", "B\n")))
      } +
      test("runBefore and runAfter") {
        val mid = Middleware.identity[Any, Unit].runBefore(Console.printLine("A")).runAfter(Console.printLine("C"))
        val app = Http.fromZIO(Console.printLine("B")) @@ mid
        assertZIO(app(()) *> TestConsole.output)(equalTo(Vector("A\n", "B\n", "C\n")))
      } +
      test("race") {
        val mid = Middleware.succeed('A').delay(2 second) race Middleware.succeed("B").delay(1 second)
        val app = Http.succeed(1) @@ mid
        assertZIO(app(()) <& TestClock.adjust(3 second))(equalTo("B"))
      } +
      suite("ifThenElse") {
        val mid = Middleware.ifThenElse[Int](_ > 5)(
          isTrue = i => Middleware.succeed(i + 1),
          isFalse = i => Middleware.succeed(i - 1),
        )
        test("isTrue") {
          val app = Http.identity[Int] @@ mid
          assertZIO(app(10))(equalTo(11))
        } +
          test("isFalse") {
            val app = Http.identity[Int] @@ mid
            assertZIO(app(1))(equalTo(0))
          }
      } +
      suite("ifThenElseZIO") {
        val mid = Middleware.ifThenElseZIO[Int](i => ZIO.succeed(i > 5))(
          isTrue = i => Middleware.succeed(i + 1),
          isFalse = i => Middleware.succeed(i - 1),
        )
        test("isTrue") {
          val app = Http.identity[Int] @@ mid
          assertZIO(app(10))(equalTo(11))
        } +
          test("isFalse") {
            val app = Http.identity[Int] @@ mid
            assertZIO(app(1))(equalTo(0))
          }
      } +
      suite("contramap") {
        val mid = Middleware.intercept[String, String](a => a + "Bar")((b, s) => b + s)
        test("contramap") {
          val app = Http.identity[String] @@ mid.contramap[Int] { i => s"${i}Foo" }
          assertZIO(app(0))(equalTo("0Foo0FooBar"))
        } +
          test("contramapZIO") {
            val app = Http.identity[String] @@ mid.contramapZIO[Int] { i => ZIO.succeed(s"${i}Foo") }
            assertZIO(app(0))(equalTo("0Foo0FooBar"))
          }
      } +
      suite("when") {
        val mid = Middleware.transform[Int, Int](
          in = _ + 1,
          out = _ + 1,
        )
        test("condition is true") {
          val app = Http.identity[Int] @@ mid.when((_: Any) => true)
          assertZIO(app(10))(equalTo(12))
        } +
          test("condition is false") {
            val app = Http.identity[Int] @@ mid.when((_: Any) => false)
            assertZIO(app(1))(equalTo(1))
          }
      } +
      suite("whenZIO") {
        val mid = Middleware.transform[Int, Int](
          in = _ + 1,
          out = _ + 1,
        )
        test("condition is true") {
          val app = Http.identity[Int] @@ mid.whenZIO((_: Any) => ZIO.succeed(true))
          assertZIO(app(10))(equalTo(12))
        } +
          test("condition is false") {
            val app = Http.identity[Int] @@ mid.whenZIO((_: Any) => ZIO.succeed(false))
            assertZIO(app(1))(equalTo(1))
          }
      } +
      suite("codec") {
        test("codec success") {
          val mid = Middleware.codec[String, Int](a => Right(a.toInt), b => Right(b.toString))
          val app = Http.identity[Int] @@ mid
          assertZIO(app("1"))(equalTo("1"))
        } +
          test("decoder failure") {
            val mid = Middleware.codec[String, Int](a => Left(a), b => Right(b.toString))
            val app = Http.identity[Int] @@ mid
            assertZIO(app("a").exit)(fails(anything))
          } +
          test("encoder failure") {
            val mid = Middleware.codec[String, Int](a => Right(a.toInt), b => Left(b.toString))
            val app = Http.identity[Int] @@ mid
            assertZIO(app("1").exit)(fails(anything))
          }
      } +
      test("allow") {
        val mid = Middleware.allow[Int, Int](_ > 4)
        val app = Http.succeed(1) @@ mid
        for {
          test1 <- assertZIO(app(1).either)(isLeft(isNone))
          test2 <- assertZIO(app(6))(equalTo(1))
        } yield test1 && test2
      } +
      test("allowZIO") {
        val mid = Middleware.allowZIO[Int, Int](x => ZIO.succeed(x > 4))
        val app = Http.succeed(1) @@ mid
        for {
          test1 <- assertZIO(app(1).either)(isLeft(isNone))
          test2 <- assertZIO(app(6))(equalTo(1))
        } yield test1 && test2
      } +
      suite("codecHttp")(
        test("codec success") {
          val a   = Http.collect[Int] { case v => v.toString }
          val b   = Http.collect[String] { case v => v.toInt }
          val mid = Middleware.codecHttp[String, Int](b, a)
          val app = Http.identity[Int] @@ mid
          assertZIO(app("2"))(equalTo("2"))
        },
        test("encoder failure") {
          val mid = Middleware.codecHttp[String, Int](Http.succeed(1), Http.fail("fail"))
          val app = Http.identity[Int] @@ mid
          assertZIO(app("2").exit)(fails(anything))
        },
        test("decoder failure") {
          val mid = Middleware.codecHttp[String, Int](Http.fail("fail"), Http.succeed(2))
          val app = Http.identity[Int] @@ mid
          assertZIO(app("2").exit)(fails(anything))
        },
      )
  }
}
