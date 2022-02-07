package zhttp.http

import zio.duration._
import zio.test.Assertion._
import zio.test.environment.{TestClock, TestConsole}
import zio.test.{DefaultRunnableSpec, assert, assertM}
import zio.{Ref, UIO, console}

object MiddlewareSpec extends DefaultRunnableSpec with HExitAssertion {
  def spec = suite("Middleware") {
    val increment = Middleware.codec[Int, Int](decoder = a => Right(a + 1), encoder = b => Right(b + 1))
    testM("empty") {
      val http = Http.empty
      val app  = Middleware.identity(http)
      assertM(app(()).either)(isLeft(isNone))
    } +
      testM("constant") {
        val mid = Middleware.fromHttp(Http.succeed("OK"))
        val app = Http.succeed(1) @@ mid
        assertM(app(()))(equalTo("OK"))
      } +
      testM("as") {
        val mid = Middleware.fromHttp(Http.succeed("Not OK")).as("OK")
        val app = Http.succeed(1) @@ mid
        assertM(app(()))(equalTo("OK"))
      } +
      testM("interceptZIO") {
        for {
          ref <- Ref.make(0)
          mid = Middleware.interceptZIO[Int, Int](i => UIO(i * 10))((i, j) => ref.set(i + j))
          app = Http.identity[Int] @@ mid
          _ <- app(1)
          i <- ref.get
        } yield assert(i)(equalTo(11))
      } +
      testM("orElse") {
        val mid = Middleware.fail("left") <> Middleware.fail("right")
        val app = Http.empty @@ mid
        assertM(app(()).flip)(isSome(equalTo("right")))
      } +
      testM("combine") {
        val mid1 = increment
        val mid2 = increment
        val mid  = mid1 andThen mid2
        val app  = Http.identity[Int] @@ mid
        assertM(app(0))(equalTo(4))
      } +
      testM("flatMap") {
        val mid = increment.flatMap(i => Middleware.succeed(i + 1))
        val app = Http.identity[Int] @@ mid
        assertM(app(0))(equalTo(3))
      } +
      testM("mapZIO") {
        val mid = increment.mapZIO(i => UIO(i + 1))
        val app = Http.identity[Int] @@ mid
        assertM(app(0))(equalTo(3))
      } +
      testM("mapError") {
        val originalFailure = "original failure"
        val mappedFailure   = " plus mapped failure"
        val http            = Middleware.fail(originalFailure)

        val mid = http.mapError { _ + mappedFailure }

        val app = Http.identity[Int] @@ mid
        assertM(app(0).run)(fails(equalTo(Some(originalFailure + mappedFailure))))
      } +
      testM("runBefore") {
        val mid = Middleware.identity.runBefore(console.putStrLn("A"))
        val app = Http.fromZIO(console.putStrLn("B")) @@ mid
        assertM(app(()) *> TestConsole.output)(equalTo(Vector("A\n", "B\n")))
      } +
      testM("runAfter") {
        val mid = Middleware.identity.runAfter(console.putStrLn("B"))
        val app = Http.fromZIO(console.putStrLn("A")) @@ mid
        assertM(app(()) *> TestConsole.output)(equalTo(Vector("A\n", "B\n")))
      } +
      testM("runBefore and runAfter") {
        val mid = Middleware.identity.runBefore(console.putStrLn("A")).runAfter(console.putStrLn("C"))
        val app = Http.fromZIO(console.putStrLn("B")) @@ mid
        assertM(app(()) *> TestConsole.output)(equalTo(Vector("A\n", "B\n", "C\n")))
      } +
      testM("race") {
        val mid = Middleware.succeed('A').delay(2 second) race Middleware.succeed("B").delay(1 second)
        val app = Http.succeed(1) @@ mid
        assertM(app(()) <& TestClock.adjust(3 second))(equalTo("B"))
      } +
      suite("ifThenElse") {
        val mid = Middleware.ifThenElse[Int](_ > 5)(
          isTrue = i => Middleware.succeed(i + 1),
          isFalse = i => Middleware.succeed(i - 1),
        )
        testM("isTrue") {
          val app = Http.identity[Int] @@ mid
          assertM(app(10))(equalTo(11))
        } +
          testM("isFalse") {
            val app = Http.identity[Int] @@ mid
            assertM(app(1))(equalTo(0))
          }
      } +
      suite("ifThenElseZIO") {
        val mid = Middleware.ifThenElseZIO[Int](i => UIO(i > 5))(
          isTrue = i => Middleware.succeed(i + 1),
          isFalse = i => Middleware.succeed(i - 1),
        )
        testM("isTrue") {
          val app = Http.identity[Int] @@ mid
          assertM(app(10))(equalTo(11))
        } +
          testM("isFalse") {
            val app = Http.identity[Int] @@ mid
            assertM(app(1))(equalTo(0))
          }
      } +
      suite("contramap") {
        val mid = Middleware.intercept[String, String](a => a + "Bar")((b, s) => b + s)
        testM("contramap") {
          val app = Http.identity[String] @@ mid.contramap[Int] { i => s"${i}Foo" }
          assertM(app(0))(equalTo("0Foo0FooBar"))
        } +
          testM("contramapZIO") {
            val app = Http.identity[String] @@ mid.contramapZIO[Int] { i => UIO(s"${i}Foo") }
            assertM(app(0))(equalTo("0Foo0FooBar"))
          }
      } +
      suite("when") {
        val mid = Middleware.succeed(0)
        testM("condition is true") {
          val app = Http.identity[Int] @@ mid.when[Int](_ => true)
          assertM(app(10))(equalTo(0))
        } +
          testM("condition is false") {
            val app = Http.identity[Int] @@ mid.when[Int](_ => false)
            assertM(app(1))(equalTo(1))
          }
      } +
      suite("whenZIO") {
        val mid = Middleware.succeed(0)
        testM("condition is true") {
          val app = Http.identity[Int] @@ mid.whenZIO[Any, Nothing, Int](_ => UIO(true))
          assertM(app(10))(equalTo(0))
        } +
          testM("condition is false") {
            val app = Http.identity[Int] @@ mid.whenZIO[Any, Nothing, Int](_ => UIO(false))
            assertM(app(1))(equalTo(1))
          }
      } +
      suite("codec") {
        testM("codec success") {
          val mid = Middleware.codec[String, Int](a => Right(a.toInt), b => Right(b.toString))
          val app = Http.identity[Int] @@ mid
          assertM(app("1"))(equalTo("1"))
        } +
          testM("decoder failure") {
            val mid = Middleware.codec[String, Int](a => Left(a), b => Right(b.toString))
            val app = Http.identity[Int] @@ mid
            assertM(app("a").run)(fails(anything))
          } +
          testM("encoder failure") {
            val mid = Middleware.codec[String, Int](a => Right(a.toInt), b => Left(b.toString))
            val app = Http.identity[Int] @@ mid
            assertM(app("1").run)(fails(anything))
          }
      }
  }
}
