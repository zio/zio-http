package zhttp.http

import zio.clock.Clock
import zio.duration.Duration
import zio.{UIO, ZIO}

/**
 * Middlewares are essentially transformations that one can apply on any Http to produce a new one. They can modify
 * requests and responses and also transform them into more concrete domain entities.
 */
sealed trait Middleware[-R, +E, +AIn, -BIn, -AOut, +BOut] { self =>
  def <>[R1 <: R, E1, AIn0 >: AIn, BIn0 <: BIn, AOut0 <: AOut, BOut0 >: BOut](
    other: Middleware[R1, E1, AIn0, BIn0, AOut0, BOut0],
  ): Middleware[R1, E1, AIn0, BIn0, AOut0, BOut0] = self orElse other

  def <<<[R1 <: R, E1 >: E, A0 <: AOut, B0 >: BOut, A1, B1](
    other: Middleware[R1, E1, A0, B0, A1, B1],
  ): Middleware[R1, E1, AIn, BIn, A1, B1] = self compose other

  def ++[R1 <: R, E1 >: E, A0 >: AIn <: AOut, B0 >: BOut <: BIn](
    other: Middleware[R1, E1, A0, B0, A0, B0],
  ): Middleware[R1, E1, A0, B0, A0, B0] =
    self combine other

  def apply[R1 <: R, E1 >: E](http: Http[R1, E1, AIn, BIn]): Http[R1, E1, AOut, BOut] = execute(http)

  def combine[R1 <: R, E1 >: E, A0 >: AIn <: AOut, B0 >: BOut <: BIn](
    other: Middleware[R1, E1, A0, B0, A0, B0],
  ): Middleware[R1, E1, A0, B0, A0, B0] =
    self compose other

  def compose[R1 <: R, E1 >: E, A0 <: AOut, B0 >: BOut, A1, B1](
    other: Middleware[R1, E1, A0, B0, A1, B1],
  ): Middleware[R1, E1, AIn, BIn, A1, B1] = Middleware.Compose(self, other)

  def delay(duration: Duration): Middleware[R with Clock, E, AIn, BIn, AOut, BOut] =
    self.mapZIO(b => UIO(b).delay(duration))

  def execute[R1 <: R, E1 >: E](http: Http[R1, E1, AIn, BIn]): Http[R1, E1, AOut, BOut] = Middleware.execute(http, self)

  def flatMap[R1 <: R, E1 >: E, AIn0 >: AIn, BIn0 <: BIn, AOut0 <: AOut, BOut0](
    f: BOut => Middleware[R1, E1, AIn0, BIn0, AOut0, BOut0],
  ): Middleware[R1, E1, AIn0, BIn0, AOut0, BOut0] =
    Middleware.FlatMap(self, f)

  def flatten[R1 <: R, E1 >: E, AIn0 >: AIn, BIn0 <: BIn, AOut0 <: AOut, BOut0](implicit
    ev: BOut <:< Middleware[R1, E1, AIn0, BIn0, AOut0, BOut0],
  ): Middleware[R1, E1, AIn0, BIn0, AOut0, BOut0] =
    flatMap(identity(_))

  def map[BOut0](f: BOut => BOut0): Middleware[R, E, AIn, BIn, AOut, BOut0] =
    self.flatMap(b => Middleware.succeed(f(b)))

  def mapZIO[R1 <: R, E1 >: E, BOut0](f: BOut => ZIO[R1, E1, BOut0]): Middleware[R1, E1, AIn, BIn, AOut, BOut0] =
    self.flatMap(b => Middleware.fromHttp(Http.fromEffect(f(b))))

  def orElse[R1 <: R, E1, AIn0 >: AIn, BIn0 <: BIn, AOut0 <: AOut, BOut0 >: BOut](
    other: Middleware[R1, E1, AIn0, BIn0, AOut0, BOut0],
  ): Middleware[R1, E1, AIn0, BIn0, AOut0, BOut0] =
    Middleware.OrElse(self, other)

  def race[R1 <: R, E1 >: E, AIn1 >: AIn, BIn1 <: BIn, AOut1 <: AOut, BOut1 >: BOut](
    other: Middleware[R1, E1, AIn1, BIn1, AOut1, BOut1],
  ): Middleware[R1, E1, AIn1, BIn1, AOut1, BOut1] =
    Middleware.Race(self, other)
}

object Middleware {

  def codec[A, B]: PartialCodec[A, B] = new PartialCodec[A, B](())

  def codecZIO[A, B]: PartialCodecZIO[A, B] = new PartialCodecZIO[A, B](())

  def fail[E](e: E): Middleware[Any, E, Nothing, Any, Any, Nothing] = Fail(e)

  def fromHttp[R, E, A, B](http: Http[R, E, A, B]): Middleware[R, E, Nothing, Any, A, B] = Constant(http)

  def identity[A, B]: Middleware[Any, Nothing, A, B, A, B] = Identity

  def ifThenElse[A]: PartialIfThenElse[A] = new PartialIfThenElse(())

  def ifThenElseZIO[A]: PartialIfThenElseZIO[A] = new PartialIfThenElseZIO(())

  def intercept[A, B]: PartialIntercept[A, B] = new PartialIntercept[A, B](())

  def interceptZIO[A, B]: PartialInterceptZIO[A, B] = new PartialInterceptZIO[A, B](())

  def succeed[B](b: B): Middleware[Any, Nothing, Nothing, Any, Any, B] = fromHttp(Http.succeed(b))

  private[zhttp] def execute[R, E, AIn, BIn, AOut, BOut](
    http: Http[R, E, AIn, BIn],
    self: Middleware[R, E, AIn, BIn, AOut, BOut],
  ): Http[R, E, AOut, BOut] =
    self match {
      case Codec(decoder, encoder)       => http.contramapZIO(decoder(_)).mapZIO(encoder(_))
      case Identity                      => http.asInstanceOf[Http[R, E, AOut, BOut]]
      case Constant(http)                => http.asInstanceOf[Http[R, E, AOut, BOut]]
      case OrElse(self, other)           => self.execute(http).orElse(other.execute(http))
      case Fail(error)                   => Http.fail(error)
      case Compose(self, other)          => other.execute(self.execute(http))
      case FlatMap(self, f)              => self.execute(http).flatMap(f(_).execute(http))
      case Race(self, other)             => self.execute(http) race other.execute(http)
      case Intercept(incoming, outgoing) =>
        Http.fromOptionFunction[AOut] { a =>
          for {
            s <- incoming(a)
            b <- http(a.asInstanceOf[AIn])
            c <- outgoing(b, s)
          } yield c.asInstanceOf[BOut]
        }
    }

  final case class Fail[E](error: E) extends Middleware[Any, E, Nothing, Any, Any, Nothing]

  final case class OrElse[R, E0, E1, AIn, BIn, AOut, BOut](
    self: Middleware[R, E0, AIn, BIn, AOut, BOut],
    other: Middleware[R, E1, AIn, BIn, AOut, BOut],
  ) extends Middleware[R, E1, AIn, BIn, AOut, BOut]

  final class PartialInterceptZIO[A, B](val unit: Unit) extends AnyVal {
    def apply[R, E, S, BOut](incoming: A => ZIO[R, Option[E], S])(
      outgoing: (B, S) => ZIO[R, Option[E], BOut],
    ): Middleware[R, E, A, B, A, BOut] = Intercept(incoming, outgoing)
  }

  final class PartialIntercept[A, B](val unit: Unit) extends AnyVal {
    def apply[S, BOut](incoming: A => S)(outgoing: (B, S) => BOut): Middleware[Any, Nothing, A, B, A, BOut] =
      interceptZIO[A, B](a => UIO(incoming(a)))((b, s) => UIO(outgoing(b, s)))
  }

  final case class Codec[R, E, AIn, BIn, AOut, BOut](decoder: AOut => ZIO[R, E, AIn], encoder: BIn => ZIO[R, E, BOut])
      extends Middleware[R, E, AIn, BIn, AOut, BOut]

  final class PartialCodec[AOut, BIn](val unit: Unit) extends AnyVal {
    def apply[AIn, BOut](decoder: AOut => AIn, encoder: BIn => BOut): Middleware[Any, Nothing, AIn, BIn, AOut, BOut] =
      Codec(a => UIO(decoder(a)), b => UIO(encoder(b)))
  }

  final class PartialIfThenElse[AOut](val unit: Unit) extends AnyVal {
    def apply[R, E, AIn, BIn, BOut](cond: AOut => Boolean)(
      isTrue: AOut => Middleware[R, E, AIn, BIn, AOut, BOut],
      isFalse: AOut => Middleware[R, E, AIn, BIn, AOut, BOut],
    ): Middleware[R, E, AIn, BIn, AOut, BOut] =
      Middleware
        .fromHttp(Http.fromFunction[AOut] { a => if (cond(a)) isTrue(a) else isFalse(a) })
        .flatten
  }

  final class PartialIfThenElseZIO[AOut](val unit: Unit) extends AnyVal {
    def apply[R, E, AIn, BIn, BOut](cond: AOut => ZIO[R, E, Boolean])(
      isTrue: AOut => Middleware[R, E, AIn, BIn, AOut, BOut],
      isFalse: AOut => Middleware[R, E, AIn, BIn, AOut, BOut],
    ): Middleware[R, E, AIn, BIn, AOut, BOut] =
      Middleware
        .fromHttp(Http.fromFunctionZIO[AOut] { a => cond(a).map(b => if (b) isTrue(a) else isFalse(a)) })
        .flatten
  }

  final class PartialCodecZIO[AOut, BIn](val unit: Unit) extends AnyVal {
    def apply[R, E, AIn, BOut](
      decoder: AOut => ZIO[R, E, AIn],
      encoder: BIn => ZIO[R, E, BOut],
    ): Middleware[R, E, AIn, BIn, AOut, BOut] =
      Codec(decoder(_), encoder(_))
  }

  final case class Constant[R, E, AOut, BOut](http: Http[R, E, AOut, BOut])
      extends Middleware[R, E, Nothing, Any, AOut, BOut]

  final case class Intercept[R, E, A, B, S, BOut](
    incoming: A => ZIO[R, Option[E], S],
    outgoing: (B, S) => ZIO[R, Option[E], BOut],
  ) extends Middleware[R, E, A, B, A, BOut]

  final case class Compose[R, E, A0, B0, A1, B1, A2, B2](
    self: Middleware[R, E, A0, B0, A1, B1],
    other: Middleware[R, E, A1, B1, A2, B2],
  ) extends Middleware[R, E, A0, B0, A2, B2]

  final case class FlatMap[R, E, AIn, BIn, AOut, BOut0, BOut1](
    self: Middleware[R, E, AIn, BIn, AOut, BOut0],
    f: BOut0 => Middleware[R, E, AIn, BIn, AOut, BOut1],
  ) extends Middleware[R, E, AIn, BIn, AOut, BOut1]

  final case class Race[R, E, AIn, BIn, AOut, BOut](
    self: Middleware[R, E, AIn, BIn, AOut, BOut],
    other: Middleware[R, E, AIn, BIn, AOut, BOut],
  ) extends Middleware[R, E, AIn, BIn, AOut, BOut]

  final case object Identity extends Middleware[Any, Nothing, Nothing, Any, Any, Nothing]
}
