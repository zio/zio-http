package zio-http.domain.http

import zio.ZIO

/**
 * Domain that models a Request => Response relationship
 */

sealed trait Http[-R, +E, -A, +B] { self =>
  def apply[A1 <: A](req: => A): ZIO[R, E, B] = Http.execute(self, req)

  def ++[R1 <: R, E1 >: E, A1 <: A, B1 >: B](other: Http[R1, E1, A1, B1])(implicit
    ev: CanConcatenate[E1],
  ): Http[R1, E1, A1, B1] = Http.Concat(self, other, ev)

  def >>>[R1 <: R, E1 >: E, A1 <: A, B1 >: B, C1](other: Http[R1, E1, B1, C1]): Http[R1, E1, A1, C1] =
    Http.Chain(self, other)

  def >>=[R1 <: R, E1 >: E, A1 <: A, C1](f: B => Http[R1, E1, A1, C1]): Http[R1, E1, A1, C1] =
    self.flatMap(f)

  def *>[R1 <: R, E1 >: E, A1 <: A, C1](other: Http[R1, E1, A1, C1]): Http[R1, E1, A1, C1] =
    self.flatMap(_ => other)

  def silent[E1 >: E, B1 >: B](implicit s: CanBeSilenced[E1, B1]): Http[R, Nothing, A, B1] =
    self.foldM(e => Http.succeed(s.silent(e)), Http.succeed)

  def collect[R1 <: R, E1 >: E, A1 <: A, B1 >: B, C](
    pf: PartialFunction[B1, C],
  )(implicit error: CanSupportPartial[B1, E1]): Http[R1, E1, A1, C] =
    self >>> Http.collect(pf)

  def collectM[R1 <: R, E1 >: E, A1 <: A, B1 >: B, C](
    pf: PartialFunction[B1, ZIO[R1, E1, C]],
  )(implicit error: CanSupportPartial[B1, E1]): Http[R1, E1, A1, C] =
    self >>> Http.collectM(pf)

  def map[C](bc: B => C): Http[R, E, A, C] = self.flatMap(b => Http.succeed(bc(b)))

  def cmap[X](xa: X => A): Http[R, E, X, B] = Http.identity[X].map(xa) >>> self

  def mapM[R1 <: R, E1 >: E, C](bFc: B => ZIO[R1, E1, C]): Http[R1, E1, A, C] =
    self >>> Http.fromEffectFunction(bFc)

  def cmapM[R1 <: R, E1 >: E, X](xa: X => ZIO[R1, E1, A]): Http[R1, E1, X, B] =
    Http.fromEffectFunction[X](xa) >>> self

  def flatten[R1 <: R, E1 >: E, A1 <: A, B1](implicit ev: B <:< Http[R1, E1, A1, B1]): Http[R1, E1, A1, B1] = {
    self.flatMap(identity(_))
  }

  def widen[B1](implicit ev: B <:< B1): Http[R, E, A, B1] =
    self.asInstanceOf[Http[R, E, A, B1]]

  def as[C](c: C): Http[R, E, A, C] =
    self *> Http.succeed(c)

  def flatMap[R1 <: R, E1 >: E, A1 <: A, C1](f: B => Http[R1, E1, A1, C1]): Http[R1, E1, A1, C1] = {
    self.foldM(Http.fail, f)
  }

  def foldM[R1 <: R, A1 <: A, E1, B1](
    ee: E => Http[R1, E1, A1, B1],
    bb: B => Http[R1, E1, A1, B1],
  ): Http[R1, E1, A1, B1] = Http.FoldM(self, ee, bb)
}

object Http extends HttpConstructors with HttpExecutors {
  final case object Identity extends Http[Any, Nothing, Any, Nothing]

  final case class Succeed[B](b: B) extends Http[Any, Nothing, Any, B]

  final case class Fail[E](e: E) extends Http[Any, E, Any, Nothing]

  case class FromEffectFunction[R, E, A, B](f: A => ZIO[R, E, B]) extends Http[R, E, A, B]

  final case class Concat[R, E, A, B](self: Http[R, E, A, B], other: Http[R, E, A, B], ev: CanConcatenate[E])
      extends Http[R, E, A, B]

  final case class Chain[R, E, A, B, C](self: Http[R, E, A, B], other: Http[R, E, B, C]) extends Http[R, E, A, C]

  final case class FoldM[R, E, EE, A, B, BB](
    self: Http[R, E, A, B],
    ee: E => Http[R, EE, A, BB],
    bb: B => Http[R, EE, A, BB],
  ) extends Http[R, EE, A, BB]

  // Ctor Help
  final case class MakeCollectM[A](unit: Unit) extends AnyVal {
    def apply[R, E, B](
      pf: PartialFunction[A, ZIO[R, E, B]],
    )(implicit error: CanSupportPartial[A, E]): Http[R, E, A, B] =
      Http.fromEffectFunction[A](a => if (pf.isDefinedAt(a)) pf(a) else ZIO.fail(error(a)))
  }

  final case class MakeCollect[A](unit: Unit) extends AnyVal {
    def apply[R, E, B](
      pf: PartialFunction[A, B],
    )(implicit error: CanSupportPartial[A, E]): Http[R, E, A, B] = Http.identity[A] >>=
      (a => if (pf.isDefinedAt(a)) Http.succeed(pf(a)) else Http.fail(error(a)))
  }

  final case class MakeFromEffectFunction[A](unit: Unit) extends AnyVal {
    def apply[R, E, B](f: A => ZIO[R, E, B]): Http[R, E, A, B] = Http.FromEffectFunction(f)
  }

}
