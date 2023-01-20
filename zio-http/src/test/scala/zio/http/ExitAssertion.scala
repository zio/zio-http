package zio.http

import zio.test._
import zio.{Exit, ZIO}

private[zio] trait ExitAssertion {
  def isDie[R, E, A](ass: Assertion[Throwable]): Assertion[ZIO[R, E, A]] =
    Assertion.assertion("isDie") {
      case Exit.Failure(cause) => cause.dieOption.fold(false)(ass.test)
      case _                   => false
    }

  def isEffect[R, E, A]: Assertion[ZIO[R, E, A]] =
    Assertion.assertion("isEffect") {
      case _: Exit[_, _] => false
      case _             => true
    }

  def isSuccess[R, E, A](ass: Assertion[A]): Assertion[ZIO[R, E, A]] =
    Assertion.assertion("isSuccess") {
      case Exit.Success(a) => ass.test(a)
      case _               => false
    }

  def isFailure[R, E, A](ass: Assertion[E]): Assertion[ZIO[R, E, A]] =
    Assertion.assertion("isFailure") {
      case Exit.Failure(cause) => cause.failureOption.fold(false)(ass.test)
      case _                   => false
    }

  private[zio] implicit class ZIOSyntax[R, E, A](result: ZIO[R, E, A]) {
    def ===(assertion: Assertion[ZIO[R, E, A]]): TestResult = assert(result)(assertion)
  }
}
