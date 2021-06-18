package zhttp.http

import zio._
import zio.duration._
import zio.test.Assertion._
import zio.test.TestAspect._
import zio.test._

object HttpResultSpec extends DefaultRunnableSpec with HttpResultAssertion {
  def spec: ZSpec[Environment, Failure] = {
    import HttpResult._
    suite("HttpResult")(
      test("out") {
        empty === isEmpty &&
        succeed(1) === isSuccess(equalTo(1)) &&
        fail(1) === isFailure(equalTo(1)) &&
        effect(UIO(1)) === isEffect
      },
      test("flatMapError") {
        succeed(0) *> fail(1) <> fail(2) === isFailure(equalTo(2)) &&
        succeed(0) *> fail(1) *> fail(2) === isFailure(equalTo(1))
      },
      suite("defaultWith")(
        test("succeed") {
          empty <+> succeed(1) === isSuccess(equalTo(1)) &&
          succeed(1) <+> empty === isSuccess(equalTo(1)) &&
          succeed(1) <+> succeed(2) === isSuccess(equalTo(1)) &&
          succeed(1) <+> empty === isSuccess(equalTo(1)) &&
          empty <+> empty === isEmpty
        },
        test("fail") {
          empty <+> fail(1) === isFailure(equalTo(1)) &&
          fail(1) <+> empty === isFailure(equalTo(1)) &&
          fail(1) <+> fail(2) === isFailure(equalTo(1)) &&
          fail(1) <+> empty === isFailure(equalTo(1))
        },
        test("empty") {
          empty <+> empty === isEmpty
        },
        test("effect") {
          effect(UIO(1)) <+> empty === isEffect &&
          empty <+> effect(UIO(1)) === isEffect &&
          empty *> effect(UIO(1)) *> effect(UIO(1)) === isEmpty
        },
        test("nested succeed") {
          empty <+> succeed(1) <+> succeed(2) === isSuccess(equalTo(1)) &&
          succeed(1) <+> empty <+> succeed(2) === isSuccess(equalTo(1)) &&
          empty <+> empty <+> succeed(1) === isSuccess(equalTo(1))
        },
        test("flatMap") {
          succeed(0) *> empty <+> succeed(1) === isSuccess(equalTo(1)) &&
          empty *> empty <+> succeed(1) === isSuccess(equalTo(1)) &&
          empty *> empty *> empty <+> succeed(1) === isSuccess(equalTo(1))
        },
        test("reversed") {
          empty <+> (empty <+> (empty <+> succeed(1))) === isSuccess(equalTo(1))
        },
      ),
      suite("provide")(
        testM("provide") {
          val app = HttpResult.effect(ZIO.environment[Int]).provide(1).evaluate.asEffect
          assertM(app)(equalTo(1))
        },
        testM("foldM") {
          val app = (effect(ZIO.environment[Int]) *> succeed(1)).provide(1).evaluate.asEffect
          assertM(app)(equalTo(1))
        },
      ),
      suite("provideSome")(
        testM("provideSome") {
          trait HasInt {
            val int: Int
          }

          val needsEnv = effect(for {
            int <- ZIO.environment[HasInt]
          } yield int.int)

          val app = needsEnv.provideSome[Any](_ => {
            new HasInt {
              val int = 2
            }
          })

          for {
            res <- app.evaluate.asEffect
          } yield assert(res)(equalTo(2))
        },
        testM("foldM 1") {
          trait HasInt {
            val int: Int
          }

          val needsEnv = effect(for {
            int <- ZIO.environment[HasInt]
          } yield int.int)

          val app = (needsEnv *> succeed(1)).provideSome[Any](_ => {
            new HasInt { val int = 1 }
          })

          for {
            res <- app.evaluate.asEffect
          } yield assert(res)(equalTo(1))
        },
        testM("foldM 2") {
          trait HasInt {
            val int: Int
          }

          val needsEnv = effect(for {
            int <- ZIO.environment[HasInt]
          } yield int.int)

          val app = (succeed(1) *> needsEnv).provideSome[Any](_ => {
            new HasInt { val int = 1 }
          })

          for {
            res <- app.evaluate.asEffect
          } yield assert(res)(equalTo(1))
        },
        testM("it provides parts of the environment") {
          trait HasInt    {
            val int: Int
          }
          trait HasString {
            val string: String
          }

          val needsEnv = effect(for {
            _   <- ZIO.environment[HasString]
            int <- ZIO.environment[HasInt]
          } yield int.int)

          val app = (needsEnv *> needsEnv).provideSome[HasInt](env =>
            new HasInt with HasString {
              val int    = env.int
              val string = "String"
            },
          )

          for {
            res <- app.provide(new HasInt { val int = 2 }).evaluate.asEffect
          } yield assert(res)(equalTo(2))
        },
      ),
      suite("provideLayer")(
        testM("provideLayer") {
          val app = effect(for {
            _   <- ZIO.service[String]
            int <- ZIO.service[Int]
          } yield int)

          val res =
            app.provideLayer(ZLayer.succeed("String") ++ ZLayer.succeed(2)).evaluate.asEffect
          assertM(res)(equalTo(2))
        },
        testM("foldM") {
          val app = effect(for {
            _   <- ZIO.service[String]
            int <- ZIO.service[Int]
          } yield int) *> succeed(2)

          val res =
            app.provideLayer(ZLayer.succeed("String") ++ ZLayer.succeed(2)).evaluate.asEffect
          assertM(res)(equalTo(2))
        },
      ),
    ) @@ timeout(5 second)
  }
}
