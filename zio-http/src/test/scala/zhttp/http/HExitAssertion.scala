package zhttp.http

import zio.test._

private[zhttp] trait HExitAssertion {
  def isEffect[R, E, A]: Assertion[HExit[R, E, A]] =
    Assertion.assertion("isEffect")() {
      case HExit.Effect(_) => true
      case _               => false
    }

  def isEmpty[R, E, A]: Assertion[HExit[R, E, A]] =
    Assertion.assertion("isEmpty")() {
      case HExit.Empty => true
      case _           => false
    }

  def isSuccess[R, E, A](ass: Assertion[A]): Assertion[HExit[R, E, A]] =
    Assertion.assertion("isSuccess")() {
      case HExit.Success(a) => ass.test(a)
      case _                => false
    }

  def isFailure[R, E, A](ass: Assertion[E]): Assertion[HExit[R, E, A]] =
    Assertion.assertion("isFailure")() {
      case HExit.Failure(e) => ass.test(e)
      case _                => false
    }

  private[zhttp] implicit class HExitSyntax[R, E, A](result: HExit[R, E, A]) {
    def ===(assertion: Assertion[HExit[R, E, A]]): TestResult = assert(result)(assertion)
  }
}
