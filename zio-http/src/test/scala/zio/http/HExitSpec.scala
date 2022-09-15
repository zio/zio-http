package zio.http

import zio.test.Assertion._
import zio.test.TestAspect._
import zio.test._
import zio.{ZIO, durationInt}

object HExitSpec extends ZIOSpecDefault with HExitAssertion {
  def spec: Spec[Environment, Any] = {
    import HExit._
    suite("HExit")(
      test("out") {
        empty === isEmpty &&
        succeed(1) === isSuccess(equalTo(1)) &&
        fail(1) === isFailure(equalTo(1)) &&
        fromZIO(ZIO.succeed(1)) === isEffect
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
        test("die") {
          val t  = new Throwable("boom")
          val t1 = new Throwable("blah")
          empty <+> die(t) === isDie(equalTo(t)) &&
          fail(1) <+> die(t) === isFailure(equalTo(1)) &&
          die(t) <+> fail(1) === isDie(equalTo(t)) &&
          die(t) <+> die(t1) === isDie(equalTo(t))
        },
        test("empty") {
          empty <+> empty === isEmpty
        },
        test("effect") {
          fromZIO(ZIO.succeed(1)) <+> empty === isEffect &&
          empty <+> fromZIO(ZIO.succeed(1)) === isEffect &&
          empty *> fromZIO(ZIO.succeed(1)) *> fromZIO(ZIO.succeed(1)) === isEmpty
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
    ) @@ timeout(5 second)
  }
}
