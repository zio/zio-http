package zhttp.http

import zio.{CanFail, ZIO}

import scala.annotation.{tailrec, unused}

sealed trait HttpResult[-R, +E, +A] { self =>
  def map[B](ab: A => B): HttpResult[R, E, B] = self.flatMap(a => HttpResult.success(ab(a)))

  def >>=[R1 <: R, E1 >: E, B](ab: A => HttpResult[R1, E1, B]): HttpResult[R1, E1, B] =
    self.flatMap(ab)

  def *>[R1 <: R, E1 >: E, B](other: => HttpResult[R1, E1, B]): HttpResult[R1, E1, B] =
    self.flatMap(_ => other)

  def flatMap[R1 <: R, E1 >: E, B](ab: A => HttpResult[R1, E1, B]): HttpResult[R1, E1, B] =
    HttpResult.flatMap(self, ab)

  def defaultWith[R1 <: R, E1 >: E, A1 >: A](other: HttpResult[R1, E1, A1]): HttpResult[R1, E1, A1] =
    HttpResult.DefaultWith(self, other)

  def catchAll[R1 <: R, E1, A1 >: A](h: E => HttpResult[R1, E1, A1])(implicit
    @unused ev: CanFail[E],
  ): HttpResult[R1, E1, A1] =
    self.foldM(h, HttpResult.success)

  def foldM[R1 <: R, E1, B1](h: E => HttpResult[R1, E1, B1], ab: A => HttpResult[R1, E1, B1]): HttpResult[R1, E1, B1] =
    HttpResult.foldM(self, h, ab)

  def asEffect[E1 >: E](implicit ev: HttpEmpty[E1]): ZIO[R, E1, A]            = evaluate[E1].asEffect
  def evaluate[E1 >: E](implicit ev: HttpEmpty[E1]): HttpResult.Out[R, E1, A] = HttpResult.evaluate[R, E1, A](self)
  def evaluateOrElse[E1 >: E](e: E1): HttpResult.Out[R, E1, A]                = HttpResult.evaluate[R, E1, A](self)(HttpEmpty(e))
}

object HttpResult {
  sealed trait Out[-R, +E, +A] extends HttpResult[R, E, A] { self =>
    override def asEffect[E1 >: E](implicit ev: HttpEmpty[E1]): ZIO[R, E1, A] = self match {
      case Empty       => ZIO.fail(ev.get)
      case Success(a)  => ZIO.succeed(a)
      case Failure(e)  => ZIO.fail(e)
      case Continue(z) => z
    }
  }

  // CTOR
  case object Empty                                   extends Out[Any, Nothing, Nothing]
  final case class Success[A](a: A)                   extends Out[Any, Nothing, A]
  final case class Failure[E](e: E)                   extends Out[Any, E, Nothing]
  final case class Continue[R, E, A](z: ZIO[R, E, A]) extends Out[R, E, A]

  // OPR
  private final case class Suspend[R, E, A](r: () => HttpResult[R, E, A]) extends HttpResult[R, E, A]
  private final case class DefaultWith[R, E, A](self: HttpResult[R, E, A], other: HttpResult[R, E, A])
      extends HttpResult[R, E, A]
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

  def empty: HttpResult.Out[Any, Nothing, Nothing] = Empty

  // EVAL
  @tailrec
  def evaluate[R, E: HttpEmpty, A](result: HttpResult[R, E, A]): Out[R, E, A] = {
    result match {
      case m @ Continue(_)          => m
      case m @ Success(_)           => m
      case m @ Failure(_)           => m
      case Empty                    => Empty
      case Suspend(r)               => evaluate(r())
      case DefaultWith(self, other) =>
        evaluate(self match {
          case Empty                      => other
          case m: Out[_, _, _]            => m
          case Suspend(r)                 => r().defaultWith(other)
          case DefaultWith(self0, other0) => self0.defaultWith(other.defaultWith(other0))
          case FoldM(r, ee, aa)           =>
            r.foldM(
              ee(_).defaultWith(other),
              aa(_).defaultWith(other),
            )
        })
      case FoldM(r, ee, aa)         =>
        evaluate(r match {
          case Empty               => Empty
          case Success(a)          => aa(a)
          case Failure(e)          => ee(e)
          case Continue(z)         => HttpResult.continue(z.fold(ee, aa).flatMap(_.asEffect))
          case Suspend(r)          => r().foldM(ee, aa)
          case FoldM(r0, ee0, aa0) => r0.foldM(ee0(_).foldM(ee, aa), aa0(_).foldM(ee, aa))
          case DefaultWith(h1, h2) =>
            h1.foldM(
              ee(_).defaultWith(h2.foldM(ee, aa)),
              aa(_).defaultWith(h2.foldM(ee, aa)),
            )
        })
    }
  }
}
