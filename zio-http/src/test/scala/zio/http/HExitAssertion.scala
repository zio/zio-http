package zio.http

import zio.test._

private[zio] trait HExitAssertion {
  def isDie[R, E, A](ass: Assertion[Throwable]): Assertion[HExit[R, E, A]] =
    Assertion.assertion("isDie") {
      case HExit.Failure(cause) => cause.dieOption.fold(false)(ass.test)
      case _                    => false
    }

  def isEffect[R, E, A]: Assertion[HExit[R, E, A]] =
    Assertion.assertion("isEffect") {
      case HExit.Effect(_) => true
      case _               => false
    }

  def isEmpty[R, E, A]: Assertion[HExit[R, E, A]] =
    Assertion.assertion("isEmpty") {
      case HExit.Empty => true
      case _           => false
    }

  def isSuccess[R, E, A](ass: Assertion[A]): Assertion[HExit[R, E, A]] =
    Assertion.assertion("isSuccess") {
      case HExit.Success(a) => ass.test(a)
      case _                => false
    }

  def isFailure[R, E, A](ass: Assertion[E]): Assertion[HExit[R, E, A]] =
    Assertion.assertion("isFailure") {
      case HExit.Failure(cause) => cause.failureOption.fold(false)(ass.test)
      case _                    => false
    }

  private[zio] implicit class HExitSyntax[R, E, A](result: HExit[R, E, A]) {
    def ===(assertion: Assertion[HExit[R, E, A]]): TestResult = assert(result)(assertion)
  }
}
