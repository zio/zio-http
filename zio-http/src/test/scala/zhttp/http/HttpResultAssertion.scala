package zhttp.http

import zio.test._

private[zhttp] trait HttpResultAssertion {
  def isEffect[R, E, A]: Assertion[HttpResult[R, E, A]] =
    Assertion.assertion("isEffect")() {
      case HttpResult.Effect(_) => true
      case _                    => false
    }

  def isEmpty[R, E, A]: Assertion[HttpResult[R, E, A]] =
    Assertion.assertion("isEmpty")() {
      case HttpResult.Empty => true
      case _                => false
    }

  def isSuccess[R, E, A](ass: Assertion[A]): Assertion[HttpResult[R, E, A]] =
    Assertion.assertion("isSuccess")() {
      case HttpResult.Success(a) => ass.test(a)
      case _                     => false
    }

  def isFailure[R, E, A](ass: Assertion[E]): Assertion[HttpResult[R, E, A]] =
    Assertion.assertion("isFailure")() {
      case HttpResult.Failure(e) => ass.test(e)
      case _                     => false
    }

  private[zhttp] implicit class HttpResultSyntax[R, E, A](result: HttpResult[R, E, A]) {
    def ===(assertion: Assertion[HttpResult[R, E, A]]): TestResult = assert(result.evaluate)(assertion)
  }
}
