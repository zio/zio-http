package zhttp.http

import zhttp.http.middleware.Web
import zio.clock.Clock
import zio.duration.Duration
import zio.{UIO, ZIO}

/**
 * Middlewares are essentially transformations that one can apply on any Http to produce a new one. They can modify
 * requests and responses and also transform them into more concrete domain entities.
 *
 * You can think of middlewares as a functions —
 *
 * {{{
 * type Middleware[R, E, AIn, BIn, AOut, BOut] = Http[R, E, AIn, BIn] => Http[R, E, AOut, BOut]
 * }}}
 *
 * The `AIn` and `BIn` type params represent the type params of the input Http. The `AOut` and `BOut` type params
 * represent the type params of the output Http.
 */
sealed trait Middleware[-R, +E, +AIn, -BIn, -AOut, +BOut] { self =>

  /**
   * Creates a new middleware that passes the output Http of the current middleware as the input to the provided
   * middleware.
   */
  final def >>>[R1 <: R, E1 >: E, AIn1 <: AOut, BIn1 >: BOut, AOut1, BOut1](
    other: Middleware[R1, E1, AIn1, BIn1, AOut1, BOut1],
  ): Middleware[R1, E1, AIn, BIn, AOut1, BOut1] = self andThen other

  /**
   * Applies self but if it fails, applies other.
   */
  final def <>[R1 <: R, E1, AIn0 >: AIn, BIn0 <: BIn, AOut0 <: AOut, BOut0 >: BOut](
    other: Middleware[R1, E1, AIn0, BIn0, AOut0, BOut0],
  ): Middleware[R1, E1, AIn0, BIn0, AOut0, BOut0] = self orElse other

  /**
   * Combines two middleware that don't modify the input and output types.
   */
  final def ++[R1 <: R, E1 >: E, A0 >: AIn <: AOut, B0 >: BOut <: BIn](
    other: Middleware[R1, E1, A0, B0, A0, B0],
  ): Middleware[R1, E1, A0, B0, A0, B0] =
    self combine other

  /**
   * Composes one middleware with another.
   */
  final def andThen[R1 <: R, E1 >: E, AIn1 <: AOut, BIn1 >: BOut, AOut1, BOut1](
    other: Middleware[R1, E1, AIn1, BIn1, AOut1, BOut1],
  ): Middleware[R1, E1, AIn, BIn, AOut1, BOut1] = Middleware.AndThen(self, other)

  /**
   * Applies middleware on Http and returns new Http.
   */
  final def apply[R1 <: R, E1 >: E](http: Http[R1, E1, AIn, BIn]): Http[R1, E1, AOut, BOut] = execute(http)

  /**
   * Makes the middleware resolve with a constant Middleware
   */
  final def as[BOut0](
    bout: BOut0,
  ): Middleware[R, E, AIn, BIn, AOut, BOut0] =
    self.map(_ => bout)

  /**
   * Combines two middleware that operate on the same input and output types, into one.
   */
  final def combine[R1 <: R, E1 >: E, A0 >: AIn <: AOut, B0 >: BOut <: BIn](
    other: Middleware[R1, E1, A0, B0, A0, B0],
  ): Middleware[R1, E1, A0, B0, A0, B0] =
    self andThen other

  final def contramap[AOut0](f: AOut0 => AOut): Middleware[R, E, AIn, BIn, AOut0, BOut] =
    self.contramapZIO[AOut0](a => UIO(f(a)))

  final def contramapZIO[AOut0]: Middleware.PartialContraMapZIO[R, E, AIn, BIn, AOut, BOut, AOut0] =
    new Middleware.PartialContraMapZIO(self)

  /**
   * Delays the production of Http output for the specified duration
   */
  final def delay(duration: Duration): Middleware[R with Clock, E, AIn, BIn, AOut, BOut] =
    self.mapZIO(b => UIO(b).delay(duration))

  /**
   * Creates a new Middleware from another
   */
  final def flatMap[R1 <: R, E1 >: E, AIn0 >: AIn, BIn0 <: BIn, AOut0 <: AOut, BOut0](
    f: BOut => Middleware[R1, E1, AIn0, BIn0, AOut0, BOut0],
  ): Middleware[R1, E1, AIn0, BIn0, AOut0, BOut0] =
    Middleware.FlatMap(self, f)

  /**
   * Flattens an Middleware of an Middleware
   */
  final def flatten[R1 <: R, E1 >: E, AIn0 >: AIn, BIn0 <: BIn, AOut0 <: AOut, BOut0](implicit
    ev: BOut <:< Middleware[R1, E1, AIn0, BIn0, AOut0, BOut0],
  ): Middleware[R1, E1, AIn0, BIn0, AOut0, BOut0] =
    flatMap(identity(_))

  /**
   * Transforms the output type of the current middleware.
   */
  final def map[BOut0](f: BOut => BOut0): Middleware[R, E, AIn, BIn, AOut, BOut0] =
    self.flatMap(b => Middleware.succeed(f(b)))

  /**
   * Transforms the output type of the current middleware using effect function.
   */
  final def mapZIO[R1 <: R, E1 >: E, BOut0](f: BOut => ZIO[R1, E1, BOut0]): Middleware[R1, E1, AIn, BIn, AOut, BOut0] =
    self.flatMap(b => Middleware.fromHttp(Http.fromZIO(f(b))))

  /**
   * Applies self but if it fails, applies other.
   */
  final def orElse[R1 <: R, E1, AIn0 >: AIn, BIn0 <: BIn, AOut0 <: AOut, BOut0 >: BOut](
    other: Middleware[R1, E1, AIn0, BIn0, AOut0, BOut0],
  ): Middleware[R1, E1, AIn0, BIn0, AOut0, BOut0] =
    Middleware.OrElse(self, other)

  /**
   * Race between current and other, cancels other when execution of one completes
   */
  final def race[R1 <: R, E1 >: E, AIn1 >: AIn, BIn1 <: BIn, AOut1 <: AOut, BOut1 >: BOut](
    other: Middleware[R1, E1, AIn1, BIn1, AOut1, BOut1],
  ): Middleware[R1, E1, AIn1, BIn1, AOut1, BOut1] =
    Middleware.Race(self, other)

  final def runAfter[R1 <: R, E1 >: E](effect: ZIO[R1, E1, Any]): Middleware[R1, E1, AIn, BIn, AOut, BOut] =
    self.mapZIO(bOut => effect.as(bOut))

  final def runBefore[R1 <: R, E1 >: E](effect: ZIO[R1, E1, Any]): Middleware[R1, E1, AIn, BIn, AOut, BOut] =
    self.contramapZIO(b => effect.as(b))

  /**
   * Applies Middleware based only if the condition function evaluates to true
   */
  final def when[AOut0 <: AOut](cond: AOut0 => Boolean): Middleware[R, E, AIn, BIn, AOut0, BOut] =
    whenZIO(a => UIO(cond(a)))

  /**
   * Applies Middleware based only if the condition effectful function evaluates to true
   */
  final def whenZIO[R1 <: R, E1 >: E, AOut0 <: AOut](
    cond: AOut0 => ZIO[R1, E1, Boolean],
  ): Middleware[R1, E1, AIn, BIn, AOut0, BOut] =
    Middleware.ifThenElseZIO[AOut0](cond(_))(
      isTrue = _ => self,
      isFalse = _ => Middleware.identity,
    )

  /**
   * Applies Middleware and returns a transformed Http app
   */
  private[zhttp] final def execute[R1 <: R, E1 >: E](http: Http[R1, E1, AIn, BIn]): Http[R1, E1, AOut, BOut] =
    Middleware.execute(http, self)
}

object Middleware extends Web {

  /**
   * Creates a middleware using specified encoder and decoder
   */
  def codec[A, B]: PartialCodec[A, B] = new PartialCodec[A, B](())

  /**
   * Creates a middleware using specified effectful encoder and decoder
   */
  def codecZIO[A, B]: PartialCodecZIO[A, B] = new PartialCodecZIO[A, B](())

  /**
   * Creates a middleware which always fail with specified error
   */
  def fail[E](e: E): Middleware[Any, E, Nothing, Any, Any, Nothing] = Fail(e)

  /**
   * Creates a middleware with specified http App
   */
  def fromHttp[R, E, A, B](http: Http[R, E, A, B]): Middleware[R, E, Nothing, Any, A, B] = Constant(http)

  /**
   * An empty middleware that doesn't do anything
   */
  def identity: Middleware[Any, Nothing, Nothing, Any, Any, Nothing] = Middleware.Identity

  /**
   * Logical operator to decide which middleware to select based on the predicate.
   */
  def ifThenElse[A]: PartialIfThenElse[A] = new PartialIfThenElse(())

  /**
   * Logical operator to decide which middleware to select based on the predicate effect.
   */
  def ifThenElseZIO[A]: PartialIfThenElseZIO[A] = new PartialIfThenElseZIO(())

  /**
   * Creates a new middleware using transformation functions
   */
  def intercept[A, B]: PartialIntercept[A, B] = new PartialIntercept[A, B](())

  /**
   * Creates a new middleware using effectful transformation functions
   */
  def interceptZIO[A, B]: PartialInterceptZIO[A, B] = new PartialInterceptZIO[A, B](())

  /**
   * Creates a middleware using specified function
   */
  def make[A]: PartialMake[A] = new PartialMake[A](())

  /**
   * Creates a middleware using specified effect function
   */
  def makeZIO[A]: PartialMakeZIO[A] = new PartialMakeZIO[A](())

  /**
   * Creates a middleware which always succeed with specified value
   */
  def succeed[B](b: B): Middleware[Any, Nothing, Nothing, Any, Any, B] = fromHttp(Http.succeed(b))

  private[zhttp] def execute[R, E, AIn, BIn, AOut, BOut](
    http: Http[R, E, AIn, BIn],
    self: Middleware[R, E, AIn, BIn, AOut, BOut],
  ): Http[R, E, AOut, BOut] =
    self match {
      case Identity                      => http.asInstanceOf[Http[R, E, AOut, BOut]]
      case Constant(http)                => http
      case OrElse(self, other)           => self.execute(http).orElse(other.execute(http))
      case Fail(error)                   => Http.fail(error)
      case AndThen(self, other)          => other.execute(self.execute(http))
      case FlatMap(self, f)              => self.execute(http).flatMap(f(_).execute(http))
      case ContraMapZIO(self, f)         => self.execute(http).contramapZIO(a => f(a))
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

  final class PartialMake[AOut](val unit: Unit) extends AnyVal {
    def apply[R, E, AIn, BIn, BOut](
      f: AOut => Middleware[R, E, AIn, BIn, AOut, BOut],
    ): Middleware[R, E, AIn, BIn, AOut, BOut] =
      Middleware.fromHttp(Http.fromFunction[AOut](aout => f(aout))).flatten
  }

  final class PartialMakeZIO[AOut](val unit: Unit) extends AnyVal {
    def apply[R, E, AIn, BIn, BOut](
      f: AOut => ZIO[R, E, Middleware[R, E, AIn, BIn, AOut, BOut]],
    ): Middleware[R, E, AIn, BIn, AOut, BOut] =
      Middleware.fromHttp(Http.fromFunctionZIO[AOut](aout => f(aout))).flatten
  }

  final class PartialIntercept[A, B](val unit: Unit) extends AnyVal {
    def apply[S, BOut](incoming: A => S)(outgoing: (B, S) => BOut): Middleware[Any, Nothing, A, B, A, BOut] =
      interceptZIO[A, B](a => UIO(incoming(a)))((b, s) => UIO(outgoing(b, s)))
  }

  final class PartialInterceptZIO[A, B](val unit: Unit) extends AnyVal {
    def apply[R, E, S, BOut](incoming: A => ZIO[R, Option[E], S])(
      outgoing: (B, S) => ZIO[R, Option[E], BOut],
    ): Middleware[R, E, A, B, A, BOut] = Intercept(incoming, outgoing)
  }

  final class PartialCodec[AOut, BIn](val unit: Unit) extends AnyVal {
    def apply[E, AIn, BOut](
      decoder: AOut => Either[E, AIn],
      encoder: BIn => Either[E, BOut],
    ): Middleware[Any, E, AIn, BIn, AOut, BOut] =
      Middleware.identity.mapZIO((b: BIn) => ZIO.fromEither(encoder(b))).contramapZIO(a => ZIO.fromEither(decoder(a)))
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
      Middleware.identity.mapZIO(encoder).contramapZIO(decoder)
  }

  final class PartialContraMapZIO[-R, +E, +AIn, -BIn, -AOut, +BOut, AOut0](
    val self: Middleware[R, E, AIn, BIn, AOut, BOut],
  ) extends AnyVal {
    def apply[R1 <: R, E1 >: E](f: AOut0 => ZIO[R1, E1, AOut]): Middleware[R1, E1, AIn, BIn, AOut0, BOut] =
      ContraMapZIO[R1, E1, AIn, BIn, AOut, BOut, AOut0](self, f)
  }

  private final case class Fail[E](error: E) extends Middleware[Any, E, Nothing, Any, Any, Nothing]

  private final case class OrElse[R, E0, E1, AIn, BIn, AOut, BOut](
    self: Middleware[R, E0, AIn, BIn, AOut, BOut],
    other: Middleware[R, E1, AIn, BIn, AOut, BOut],
  ) extends Middleware[R, E1, AIn, BIn, AOut, BOut]

  private final case class Constant[R, E, AOut, BOut](http: Http[R, E, AOut, BOut])
      extends Middleware[R, E, Nothing, Any, AOut, BOut]

  private final case class Intercept[R, E, A, B, S, BOut](
    incoming: A => ZIO[R, Option[E], S],
    outgoing: (B, S) => ZIO[R, Option[E], BOut],
  ) extends Middleware[R, E, A, B, A, BOut]

  private final case class AndThen[R, E, A0, B0, A1, B1, A2, B2](
    self: Middleware[R, E, A0, B0, A1, B1],
    other: Middleware[R, E, A1, B1, A2, B2],
  ) extends Middleware[R, E, A0, B0, A2, B2]

  private final case class FlatMap[R, E, AIn, BIn, AOut, BOut, BOut0](
    self: Middleware[R, E, AIn, BIn, AOut, BOut0],
    f: BOut0 => Middleware[R, E, AIn, BIn, AOut, BOut],
  ) extends Middleware[R, E, AIn, BIn, AOut, BOut]

  private final case class ContraMapZIO[R, E, AIn, BIn, AOut, BOut, AOut0](
    self: Middleware[R, E, AIn, BIn, AOut, BOut],
    f: AOut0 => ZIO[R, E, AOut],
  ) extends Middleware[R, E, AIn, BIn, AOut0, BOut]

  private final case class Race[R, E, AIn, BIn, AOut, BOut](
    self: Middleware[R, E, AIn, BIn, AOut, BOut],
    other: Middleware[R, E, AIn, BIn, AOut, BOut],
  ) extends Middleware[R, E, AIn, BIn, AOut, BOut]

  private case object Identity extends Middleware[Any, Nothing, Nothing, Any, Any, Nothing]
}
