package zhttp.http

import zio._
import zio.test.Assertion._
import zio.test.{DefaultRunnableSpec, TestClock, TestConsole, assert, assertM}

object MiddlewareSpec extends DefaultRunnableSpec with HExitAssertion {
  def spec = suite("Middleware") {
    val increment = Middleware.codec[Int, Int](decoder = a => Right(a + 1), encoder = b => Right(b + 1))
    test("empty") {
      val http = Http.empty
      val app  = Middleware.identity(http)
      assertM(app(()).either)(isLeft(isNone))
    } +
      test("constant") {
        val mid = Middleware.fromHttp(Http.succeed("OK"))
        val app = Http.succeed(1) @@ mid
        assertM(app(()))(equalTo("OK"))
      } +
      test("as") {
        val mid = Middleware.fromHttp(Http.succeed("Not OK")).as("OK")
        val app = Http.succeed(1) @@ mid
        assertM(app(()))(equalTo("OK"))
      } +
      test("interceptZIO") {
        for {
          ref <- Ref.make(0)
          mid = Middleware.interceptZIO[Int, Int](i => UIO(i * 10))((i, j) => ref.set(i + j))
          app = Http.identity[Int] @@ mid
          _ <- app(1)
          i <- ref.get
        } yield assert(i)(equalTo(11))
      } +
      test("orElse") {
        val mid = Middleware.fail("left") <> Middleware.fail("right")
        val app = Http.empty @@ mid
        assertM(app(()).flip)(isSome(equalTo("right")))
      } +
      test("combine") {
        val mid1 = increment
        val mid2 = increment
        val mid  = mid1 andThen mid2
        val app  = Http.identity[Int] @@ mid
        assertM(app(0))(equalTo(4))
      } +
      test("flatMap") {
        val mid = increment.flatMap(i => Middleware.succeed(i + 1))
        val app = Http.identity[Int] @@ mid
        assertM(app(0))(equalTo(3))
      } +
      test("mapZIO") {
        val mid = increment.mapZIO(i => UIO(i + 1))
        val app = Http.identity[Int] @@ mid
        assertM(app(0))(equalTo(3))
      } +
      test("runBefore") {
        val mid = Middleware.identity.runBefore(Console.printLine("A"))
        val app = Http.fromZIO(Console.printLine("B")) @@ mid
        assertM(app(()) *> TestConsole.output)(equalTo(Vector("A\n", "B\n")))
      } +
      test("runAfter") {
        val mid = Middleware.identity.runAfter(Console.printLine("B"))
        val app = Http.fromZIO(Console.printLine("A")) @@ mid
        assertM(app(()) *> TestConsole.output)(equalTo(Vector("A\n", "B\n")))
      } +
      test("runBefore and runAfter") {
        val mid = Middleware.identity.runBefore(Console.printLine("A")).runAfter(Console.printLine("C"))
        val app = Http.fromZIO(Console.printLine("B")) @@ mid
        assertM(app(()) *> TestConsole.output)(equalTo(Vector("A\n", "B\n", "C\n")))
      } +
      test("race") {
        val mid = Middleware.succeed('A').delay(2 second) race Middleware.succeed("B").delay(1 second)
        val app = Http.succeed(1) @@ mid
        assertM(app(()) <& TestClock.adjust(3 second))(equalTo("B"))
      } +
      suite("ifThenElse") {
        val mid = Middleware.ifThenElse[Int](_ > 5)(
          isTrue = i => Middleware.succeed(i + 1),
          isFalse = i => Middleware.succeed(i - 1),
        )
        test("isTrue") {
          val app = Http.identity[Int] @@ mid
          assertM(app(10))(equalTo(11))
        } +
          test("isFalse") {
            val app = Http.identity[Int] @@ mid
            assertM(app(1))(equalTo(0))
          }
      } +
      suite("ifThenElseZIO") {
        val mid = Middleware.ifThenElseZIO[Int](i => UIO(i > 5))(
          isTrue = i => Middleware.succeed(i + 1),
          isFalse = i => Middleware.succeed(i - 1),
        )
        test("isTrue") {
          val app = Http.identity[Int] @@ mid
          assertM(app(10))(equalTo(11))
        } +
          test("isFalse") {
            val app = Http.identity[Int] @@ mid
            assertM(app(1))(equalTo(0))
          }
      } +
      suite("contramap") {
        val mid = Middleware.intercept[String, String](a => a + "Bar")((b, s) => b + s)
        test("contramap") {
          val app = Http.identity[String] @@ mid.contramap[Int] { i => s"${i}Foo" }
          assertM(app(0))(equalTo("0Foo0FooBar"))
        } +
          test("contramapZIO") {
            val app = Http.identity[String] @@ mid.contramapZIO[Int] { i => UIO(s"${i}Foo") }
            assertM(app(0))(equalTo("0Foo0FooBar"))
          }
      } +
      suite("when") {
        val mid = Middleware.succeed(0)
        test("condition is true") {
          val app = Http.identity[Int] @@ mid.when[Int](_ => true)
          assertM(app(10))(equalTo(0))
        } +
          test("condition is false") {
            val app = Http.identity[Int] @@ mid.when[Int](_ => false)
            assertM(app(1))(equalTo(1))
          }
      } +
      suite("whenZIO") {
        val mid = Middleware.succeed(0)
        test("condition is true") {
          val app = Http.identity[Int] @@ mid.whenZIO[Any, Nothing, Int](_ => UIO(true))
          assertM(app(10))(equalTo(0))
        } +
          test("condition is false") {
            val app = Http.identity[Int] @@ mid.whenZIO[Any, Nothing, Int](_ => UIO(false))
            assertM(app(1))(equalTo(1))
          }
      } +
      suite("codec") {
        test("codec success") {
          val mid = Middleware.codec[String, Int](a => Right(a.toInt), b => Right(b.toString))
          val app = Http.identity[Int] @@ mid
          assertM(app("1"))(equalTo("1"))
        } +
          test("decoder failure") {
            val mid = Middleware.codec[String, Int](a => Left(a), b => Right(b.toString))
            val app = Http.identity[Int] @@ mid
            assertM(app("a").exit)(fails(anything))
          } +
          test("encoder failure") {
            val mid = Middleware.codec[String, Int](a => Right(a.toInt), b => Left(b.toString))
            val app = Http.identity[Int] @@ mid
            assertM(app("1").exit)(fails(anything))
          }
      }
  }
}
