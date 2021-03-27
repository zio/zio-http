package zhttp.http

import zio.{CanFail, ZIO}

import scala.annotation.tailrec

sealed trait HttpResult[-R, +E, +A] { self =>
  def map[B](ab: A => B): HttpResult[R, E, B] = self.flatMap(a => HttpResult.success(ab(a)))

  def >>=[R1 <: R, E1 >: E, B](ab: A => HttpResult[R1, E1, B]): HttpResult[R1, E1, B] =
    self.flatMap(ab)

  def *>[R1 <: R, E1 >: E, B](other: => HttpResult[R1, E1, B]): HttpResult[R1, E1, B] =
    self.flatMap(_ => other)

  def flatMap[R1 <: R, E1 >: E, B](ab: A => HttpResult[R1, E1, B]): HttpResult[R1, E1, B] =
    HttpResult.flatMap(self, ab)

  def catchAll[R1 <: R, E1, A1 >: A](h: E => HttpResult[R1, E1, A1])(implicit ev: CanFail[E]): HttpResult[R1, E1, A1] =
    self.foldM(h, HttpResult.success)

  def foldM[R1 <: R, E1, B1](h: E => HttpResult[R1, E1, B1], ab: A => HttpResult[R1, E1, B1]): HttpResult[R1, E1, B1] =
    HttpResult.foldM(self, h, ab)

  def asEffect: ZIO[R, E, A] = evaluate.asEffect

  def evaluate: HttpResult.Out[R, E, A] = HttpResult.evaluate(self)
}

object HttpResult {
  sealed trait Out[-R, +E, +A] extends HttpResult[R, E, A] { self =>
    override def asEffect: ZIO[R, E, A] = self match {
      case Success(a)  => ZIO.succeed(a)
      case Failure(e)  => ZIO.fail(e)
      case Continue(z) => z
    }
  }

  // CTOR
  final case class Success[A](a: A)                   extends Out[Any, Nothing, A]
  final case class Failure[E](e: E)                   extends Out[Any, E, Nothing]
  final case class Continue[R, E, A](z: ZIO[R, E, A]) extends Out[R, E, A]

  // OPR
  private final case class Suspend[R, E, A](r: () => HttpResult[R, E, A]) extends HttpResult[R, E, A]
  private final case class FoldM[R, E, EE, A, AA](
    r: HttpResult[R, E, A],
    ee: E => HttpResult[R, EE, AA],
    aa: A => HttpResult[R, EE, AA],
  )                                                                       extends HttpResult[R, EE, AA]

  // Help
  def success[A](a: A): HttpResult.Out[Any, Nothing, A]           = Success(a)
  def failure[E](e: E): HttpResult.Out[Any, E, Nothing]           = Failure(e)
  def continue[R, E, A](z: ZIO[R, E, A]): HttpResult.Out[R, E, A] = Continue(z)

  def unit: HttpResult[Any, Nothing, Unit] = HttpResult.success(())

  def flatMap[R, E, A, B](self: HttpResult[R, E, A], ab: A => HttpResult[R, E, B]): HttpResult[R, E, B] =
    self.foldM(HttpResult.failure, ab)

  def suspend[R, E, A](r: => HttpResult[R, E, A]): HttpResult[R, E, A] = HttpResult.Suspend(() => r)

  def foldM[R, E, EE, A, AA](
    r: HttpResult[R, E, A],
    ee: E => HttpResult[R, EE, AA],
    aa: A => HttpResult[R, EE, AA],
  ): HttpResult[R, EE, AA] =
    HttpResult.FoldM(r, ee, aa)

  // EVAL
  @tailrec
  def evaluate[R, E, A](result: HttpResult[R, E, A]): Out[R, E, A] = {
    result match {
      case m @ Continue(_)  => m
      case m @ Success(_)   => m
      case m @ Failure(_)   => m
      case Suspend(r)       => evaluate(r())
      case FoldM(r, ee, aa) =>
        evaluate(r match {
          case Success(a)          => aa(a)
          case Failure(e)          => ee(e)
          case Continue(z)         => HttpResult.continue(z.fold(ee, aa).flatMap(_.asEffect))
          case Suspend(r)          => r().foldM(ee, aa)
          case FoldM(r0, ee0, aa0) => r0.foldM(ee0(_).foldM(ee, aa), aa0(_).foldM(ee, aa))
        })
    }
  }
}
