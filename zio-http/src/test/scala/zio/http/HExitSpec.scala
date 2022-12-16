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
        succeed(1) === isSuccess(equalTo(1)) &&
        fail(1) === isFailure(equalTo(1)) &&
        fromZIO(ZIO.succeed(1)) === isEffect
      },
      test("flatMapError") {
        succeed(0) *> fail(1) <> fail(2) === isFailure(equalTo(2)) &&
        succeed(0) *> fail(1) *> fail(2) === isFailure(equalTo(1))
      },
    ) @@ timeout(5 second)
  }
}
