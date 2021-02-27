package zio-http.domain.http

import zio.ZIO

sealed trait HttpResult[-R, +E, +A] { self =>
  def map[B](ab: A => B): HttpResult[R, E, B] = self.flatMap(a => HttpResult.success(ab(a)))

  def >>=[R1 <: R, E1 >: E, B](ab: A => HttpResult[R1, E1, B]): HttpResult[R1, E1, B] =
    self.flatMap(ab)

  def flatMap[R1 <: R, E1 >: E, B](ab: A => HttpResult[R1, E1, B]): HttpResult[R1, E1, B] =
    self.foldM(
      e => HttpResult.failure(e),
      a => ab(a),
    )

  def catchAll[R1 <: R, E1, A1 >: A](h: E => HttpResult[R1, E1, A1]): HttpResult[R1, E1, A1] =
    self.foldM(
      e => h(e),
      a => HttpResult.success(a),
    )

  def foldM[R1 <: R, E1, B1](h: E => HttpResult[R1, E1, B1], ab: A => HttpResult[R1, E1, B1]): HttpResult[R1, E1, B1] =
    HttpResult.foldM(self, h, ab)

  def asEffect: ZIO[R, E, A] = HttpResult.asEffect(self)
}

object HttpResult {
  // CTOR
  case class Success[A](a: A)                   extends HttpResult[Any, Nothing, A]
  case class Failure[E](e: E)                   extends HttpResult[Any, E, Nothing]
  case class Continue[R, E, A](z: ZIO[R, E, A]) extends HttpResult[R, E, A]

  // Help
  def success[A](a: A): HttpResult[Any, Nothing, A]           = Success(a)
  def failure[E](e: E): HttpResult[Any, E, Nothing]           = Failure(e)
  def continue[R, E, A](z: ZIO[R, E, A]): HttpResult[R, E, A] = Continue(z)

  def foldM[R, E, E2, A, B](
    result: => HttpResult[R, E, A],
    h: E => HttpResult[R, E2, B],
    ab: A => HttpResult[R, E2, B],
  ): HttpResult[R, E2, B] = {
    result match {
      case HttpResult.Success(a)  => ab(a)
      case HttpResult.Failure(e)  => h(e)
      case HttpResult.Continue(z) => HttpResult.continue(z.fold(h, ab).flatMap(_.asEffect))
    }
  }

  def asEffect[R, E, A](result: => HttpResult[R, E, A]): ZIO[R, E, A] = result match {
    case HttpResult.Success(a)  => ZIO.succeed(a)
    case HttpResult.Failure(e)  => ZIO.fail(e)
    case HttpResult.Continue(z) => z
  }
}
