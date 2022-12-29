package zio.http

import zio._
import zio.http.middleware.{IT, ITAndThen, ITIfThenElse, ITOrElse, MonoMiddleware, Web} // scalafix:ok;

/**
 * Middlewares are essentially transformations that one can apply on any Http to
 * produce a new one. They can modify requests and responses and also transform
 * them into more concrete domain entities.
 *
 * You can think of middlewares as a functions —
 *
 * {{{
 *   type Middleware[R, E, AIn, BIn, AOut, BOut] = Http[R, E, AIn, BIn] => Http[R, E, AOut, BOut]
 * }}}
 *
 * The `AIn` and `BIn` type params represent the type params of the input Http.
 * The `AOut` and `BOut` type params represent the type params of the output
 * Http.
 */
trait Middleware[-R, +E, +AIn, -BIn, -AOut, +BOut, +AInT <: IT[AIn]] { self =>

  def inputTransformation: AInT

  /**
   * Applies middleware on Http and returns new Http.
   */
  def apply[R1 <: R, E1 >: E](http: Http.Total[R1, E1, AIn, BIn])(implicit trace: Trace): Http.Total[R1, E1, AOut, BOut]

  /**
   * Creates a new middleware that passes the output Http of the current
   * middleware as the input to the provided middleware.
   */
  final def >>>[R1 <: R, E1 >: E, AIn1 <: AOut, BIn1 >: BOut, AOut1, BOut1, AIn1T <: IT[
    AIn1,
  ], AIn0 >: AIn, AInT0 >: AInT <: IT[AIn0], AInRT <: IT[
    AIn0,
  ]](
    other: Middleware[R1, E1, AIn1, BIn1, AOut1, BOut1, AIn1T],
  )(implicit
    ev: ITAndThen.Aux[AInT0, AIn1T, AInRT],
  ): Middleware[R1, E1, AIn0, BIn, AOut1, BOut1, AInRT] =
    self.andThen[R1, E1, AIn1, BIn1, AOut1, BOut1, AIn1T, AIn0, AInT0, AInRT](other)

  /**
   * Applies self but if it fails, applies other.
   */
  final def <>[R1 <: R, E1 >: E, AIn0 >: AIn, BIn0 <: BIn, AOut0 <: AOut, BOut0 >: BOut, AIn0T <: IT[
    AIn0,
  ], AInT0 >: AInT <: IT[AIn0], AInRT <: IT[
    AIn0,
  ]](
    other: Middleware[R1, E1, AIn0, BIn0, AOut0, BOut0, AIn0T],
  )(implicit ev: ITOrElse.Aux[AInT0, AIn0T, AInRT]): Middleware[R1, E1, AIn0, BIn0, AOut0, BOut0, AInRT] =
    self.orElse[R1, E1, AIn0, BIn0, AOut0, BOut0, AIn0T, AInT0, AInRT](other)

  /**
   * Combines two middleware that don't modify the input and output types.
   */
  final def ++[R1 <: R, E1 >: E, AIn1 >: AIn <: AOut, BIn1 >: BOut <: BIn, AIn1T <: IT[
    AIn1,
  ], AIn0 >: AIn, AInT0 >: AInT <: IT[AIn0], AInRT <: IT[
    AIn0,
  ]](
    other: Middleware[R1, E1, AIn1, BIn1, AIn1, BIn1, AIn1T],
  )(implicit ev: ITAndThen.Aux[AInT0, AIn1T, AInRT]): Middleware[R1, E1, AIn0, BIn1, AIn1, BIn1, AInRT] =
    self.combine[R1, E1, AIn1, BIn1, AIn1T, AIn0, AInT0, AInRT](other)

  /**
   * Composes one middleware with another.
   */
  final def andThen[R1 <: R, E1 >: E, AIn1 <: AOut, BIn1 >: BOut, AOut1, BOut1, AIn1T <: IT[
    AIn1,
  ], AIn0 >: AIn, AInT0 >: AInT <: IT[AIn0], AInRT <: IT[AIn0]](
    other: Middleware[R1, E1, AIn1, BIn1, AOut1, BOut1, AIn1T],
  )(implicit
    ev: ITAndThen.Aux[AInT0, AIn1T, AInRT],
  ): Middleware[R1, E1, AIn0, BIn, AOut1, BOut1, AInRT] =
    new Middleware[R1, E1, AIn0, BIn, AOut1, BOut1, AInRT] {

      override def inputTransformation: AInRT = ev.andThen(self.inputTransformation, other.inputTransformation)

      override def apply[R2 <: R1, E2 >: E1](http: Http.Total[R2, E2, AIn0, BIn])(implicit
        trace: Trace,
      ): Http.Total[R2, E2, AOut1, BOut1] =
        other(self(http))
    }

  /**
   * Makes the middleware resolve with a constant Middleware
   */
  final def as[BOut0](
    bout: BOut0,
  ): Middleware[R, E, AIn, BIn, AOut, BOut0, AInT] =
    self.map(_ => bout)

  /**
   * Combines two middleware that operate on the same input and output types,
   * into one.
   */
  final def combine[R1 <: R, E1 >: E, AIn1 >: AIn <: AOut, BIn1 >: BOut <: BIn, AIn1T <: IT[
    AIn1,
  ], AIn0 >: AIn, AInT0 >: AInT <: IT[AIn0], AInRT <: IT[
    AIn0,
  ]](
    other: Middleware[R1, E1, AIn1, BIn1, AIn1, BIn1, AIn1T],
  )(implicit ev: ITAndThen.Aux[AInT0, AIn1T, AInRT]): Middleware[R1, E1, AIn0, BIn1, AIn1, BIn1, AInRT] =
    self.andThen[R1, E1, AIn1, BIn1, AIn1, BIn1, AIn1T, AIn0, AInT0, AInRT](other)

  /**
   * Preprocesses the incoming value for the outgoing Http.
   */
  final def contramap[AOut0]: Middleware.PartialContraMap[R, E, AIn, BIn, AOut, BOut, AInT, AOut0] =
    new Middleware.PartialContraMap(self)

  /**
   * Preprocesses the incoming value using a ZIO, for the outgoing Http.
   */
  final def contramapZIO[AOut0]: Middleware.PartialContraMapZIO[R, E, AIn, BIn, AOut, BOut, AOut0, AInT] =
    new Middleware.PartialContraMapZIO(self)

  /**
   * Delays the production of Http output for the specified duration
   */
  final def delay(duration: Duration): Middleware[R, E, AIn, BIn, AOut, BOut, IT.Id[AIn]] =
    new Middleware[R, E, AIn, BIn, AOut, BOut, IT.Id[AIn]] {

      override def inputTransformation: IT.Id[AIn] = IT.Id()

      override def apply[R1 <: R, E1 >: E](http: Http.Total[R1, E1, AIn, BIn])(implicit
        trace: Trace,
      ): Http.Total[R1, E1, AOut, BOut] =
        self(http).delay(duration)
    }

  /**
   * Creates a new Middleware from another
   */
  final def flatMap[R1 <: R, E1 >: E, AIn0 >: AIn, BIn0 <: BIn, AOut0 <: AOut, BOut0, AIn0T <: IT[AIn0]](
    f: BOut => Middleware[R1, E1, AIn0, BIn0, AOut0, BOut0, AIn0T],
  ): Middleware[R1, E1, AIn0, BIn0, AOut0, BOut0, IT.Impossible[AIn]] =
    new Middleware[R1, E1, AIn0, BIn0, AOut0, BOut0, IT.Impossible[AIn]] {

      override def inputTransformation: IT.Impossible[AIn] = IT.Impossible()

      override def apply[R2 <: R1, E2 >: E1](http: Http.Total[R2, E2, AIn0, BIn0])(implicit
        trace: Trace,
      ): Http.Total[R2, E2, AOut0, BOut0] =
        self(http).flatMap(f(_)(http))
    }

  /**
   * Flattens an Middleware of a Middleware
   */
  final def flatten[R1 <: R, E1 >: E, AIn0 >: AIn, BIn0 <: BIn, AOut0 <: AOut, BOut0, AIn0T <: IT[AIn0]](implicit
    ev: BOut <:< Middleware[R1, E1, AIn0, BIn0, AOut0, BOut0, AIn0T],
  ): Middleware[R1, E1, AIn0, BIn0, AOut0, BOut0, IT.Impossible[AIn]] =
    flatMap(m => m)

  /**
   * Transforms the output type of the current middleware.
   */
  final def map[BOut0](f: BOut => BOut0): Middleware[R, E, AIn, BIn, AOut, BOut0, AInT] =
    new Middleware[R, E, AIn, BIn, AOut, BOut0, AInT] {

      override def inputTransformation: AInT = self.inputTransformation

      override def apply[R1 <: R, E1 >: E](http: Http.Total[R1, E1, AIn, BIn])(implicit
        trace: Trace,
      ): Http.Total[R1, E1, AOut, BOut0] =
        self(http).map(f)
    }

  /**
   * Transforms the output type of the current middleware using effect function.
   */
  final def mapZIO[R1 <: R, E1 >: E, BOut0](
    f: BOut => ZIO[R1, E1, BOut0],
  ): Middleware[R1, E1, AIn, BIn, AOut, BOut0, AInT] =
    new Middleware[R1, E1, AIn, BIn, AOut, BOut0, AInT] {

      override def inputTransformation: AInT = self.inputTransformation

      def apply[R2 <: R1, E2 >: E1](http: Http.Total[R2, E2, AIn, BIn])(implicit
        trace: Trace,
      ): Http.Total[R2, E2, AOut, BOut0] =
        self(http).mapZIO(f)
    }

  /**
   * Applies self but if it fails, applies other.
   */
  final def orElse[R1 <: R, E1 >: E, AIn0 >: AIn, BIn0 <: BIn, AOut0 <: AOut, BOut0 >: BOut, AIn0T <: IT[
    AIn0,
  ], AInT0 >: AInT <: IT[AIn0], AInRT <: IT[AIn0]](
    other: Middleware[R1, E1, AIn0, BIn0, AOut0, BOut0, AIn0T],
  )(implicit ev: ITOrElse.Aux[AInT0, AIn0T, AInRT]): Middleware[R1, E1, AIn0, BIn0, AOut0, BOut0, AInRT] =
    new Middleware[R1, E1, AIn0, BIn0, AOut0, BOut0, AInRT] {

      override def inputTransformation: AInRT = ev.orElse(self.inputTransformation, other.inputTransformation)

      override def apply[R2 <: R1, E2 >: E1](http: Http.Total[R2, E2, AIn0, BIn0])(implicit
        trace: Trace,
      ): Http.Total[R2, E2, AOut0, BOut0] =
        self(http) <> other(http)
    }

  /**
   * Race between current and other, cancels other when execution of one
   * completes
   */
  final def race[R1 <: R, E1 >: E, AIn1 >: AIn, BIn1 <: BIn, AOut1 <: AOut, BOut1 >: BOut, AInT0 >: AInT <: IT[
    AIn1,
  ], AIn1T <: IT[
    AIn1,
  ], AInRT <: IT[AIn1]](
    other: Middleware[R1, E1, AIn1, BIn1, AOut1, BOut1, AIn1T],
  )(implicit ev: ITOrElse.Aux[AInT0, AIn1T, AInRT]): Middleware[R1, E1, AIn1, BIn1, AOut1, BOut1, AInRT] =
    new Middleware[R1, E1, AIn1, BIn1, AOut1, BOut1, AInRT] {

      override def inputTransformation: AInRT = ev.orElse(self.inputTransformation, other.inputTransformation)

      override def apply[R2 <: R1, E2 >: E1](http: Http.Total[R2, E2, AIn1, BIn1])(implicit
        trace: Trace,
      ): Http.Total[R2, E2, AOut1, BOut1] =
        self(http) race other(http)
    }

  final def runAfter[R1 <: R, E1 >: E](effect: ZIO[R1, E1, Any]): Middleware[R1, E1, AIn, BIn, AOut, BOut, AInT] =
    new Middleware[R1, E1, AIn, BIn, AOut, BOut, AInT] {

      override def inputTransformation: AInT = self.inputTransformation

      def apply[R2 <: R1, E2 >: E1](http: Http.Total[R2, E2, AIn, BIn])(implicit
        trace: Trace,
      ): Http.Total[R2, E2, AOut, BOut] =
        self(http).mapZIO(b => effect.as(b))
    }

  final def runBefore[R1 <: R, E1 >: E](effect: ZIO[R1, E1, Any]): Middleware[R1, E1, AIn, BIn, AOut, BOut, AInT] =
    new Middleware[R1, E1, AIn, BIn, AOut, BOut, AInT] {

      override def inputTransformation: AInT = self.inputTransformation

      def apply[R2 <: R1, E2 >: E1](
        http: Http.Total[R2, E2, AIn, BIn],
      )(implicit trace: Trace): Http.Total[R2, E2, AOut, BOut] =
        Http.fromFunctionZIO { a =>
          effect *> self(http).toZIO(a)
        }
    }

  /**
   * Applies Middleware based only if the condition function evaluates to true
   */
  final def when[AOut0 <: AOut, AIn0 >: AIn, AInT0 >: AInT <: IT[AIn0], AInRT <: IT[AIn0]](cond: AOut0 => Boolean)(
    implicit
    ev: IsMono[AIn0, BIn, AOut0, BOut],
    ev2: ITIfThenElse.Aux[AIn0, AOut0, AIn0, AIn0, AInT0, IT.Id[AIn0], AInRT],
  ): Middleware[R, E, AIn0, BIn, AOut0, BOut, AInRT] =
    Middleware
      .ifThenElse[AOut0]
      .apply[R, E, AIn0, BIn, BOut, AIn0, AIn0, AInT0, IT.Id[AIn0], AInRT](cond(_))(
        isTrue = _ => self,
        isFalse = _ => Middleware.identity[AIn0, BIn, AOut, BOut],
      )

  /**
   * Applies Middleware based only if the condition effectful function evaluates
   * to true
   */
  final def whenZIO[R1 <: R, E1 >: E, AOut0 <: AOut, AIn0 >: AIn, AInT0 >: AInT <: IT[AIn0], AInRT <: IT[AIn0]](
    cond: AOut0 => ZIO[R1, E1, Boolean],
  )(implicit
    ev: IsMono[AIn0, BIn, AOut0, BOut],
    ev2: ITOrElse.Aux[AInT0, IT.Id[AIn0], AInRT],
  ): Middleware[R1, E1, AIn0, BIn, AOut0, BOut, AInRT] = {
    Middleware
      .ifThenElseZIOStatic[AOut0]
      .apply[R1, E1, AIn0, BIn, BOut, AIn0, AInT0, IT.Id[AIn0], AInRT](cond(_))(
        isTrue = self,
        isFalse = Middleware.identity[AIn0, BIn, AOut, BOut],
      )
  }
}

object Middleware extends Web {

  /**
   * Creates a middleware using the specified encoder and decoder functions
   */
  def codec[A, B]: PartialCodec[A, B] = new PartialCodec[A, B](())

  /**
   * Creates a codec middleware using two Http.
   */
  def codecHttp[A, B]: PartialCodecHttp[A, B] = new PartialCodecHttp[A, B](())

  /**
   * Creates a middleware using specified effectful encoder and decoder
   */
  def codecZIO[A, B]: PartialCodecZIO[A, B] = new PartialCodecZIO[A, B](())

  /**
   * Creates a middleware which always fail with specified error
   */
  def fail[E](e: E): Middleware[Any, E, Nothing, Any, Any, Nothing, IT.Id[Nothing]] =
    new Middleware[Any, E, Nothing, Any, Any, Nothing, IT.Id[Nothing]] {

      override def inputTransformation: IT.Id[Nothing] = IT.Id()

      override def apply[R1 <: Any, E1 >: E](http: Http.Total[R1, E1, Nothing, Any])(implicit
        trace: Trace,
      ): Http.Total[R1, E1, Any, Nothing] =
        Http.fail(e)
    }

  /**
   * Creates a middleware with specified http App
   */
  def fromHttp[R, E, A, B](http: Http.Total[R, E, A, B]): Middleware[R, E, Nothing, Any, A, B, IT.Id[Nothing]] =
    new Middleware[R, E, Nothing, Any, A, B, IT.Id[Nothing]] {

      override def inputTransformation: IT.Id[Nothing] = IT.Id()

      override def apply[R1 <: R, E1 >: E](other: Http.Total[R1, E1, Nothing, Any])(implicit
        trace: Trace,
      ): Http.Total[R1, E1, A, B] = http
    }

  /**
   * An empty middleware that doesn't do perform any operations on the provided
   * Http and returns it as it is.
   */
  def identity[A, B]: MonoMiddleware[Any, Nothing, A, B, IT.Id[A]] =
    Identity[A, B, A, B]()

  /**
   * An empty middleware that doesn't do perform any operations on the provided
   * Http and returns it as it is.
   */
  def identity[AIn, BIn, AOut, BOut](implicit
    ev: IsMono[AIn, BIn, AOut, BOut],
  ): Middleware[Any, Nothing, AIn, BIn, AOut, BOut, IT.Id[AIn]] =
    Identity[AIn, BIn, AOut, BOut]()

  /**
   * Logical operator to decide which middleware to select based on the
   * predicate.
   */
  def ifThenElse[A]: PartialIfThenElse[A] = new PartialIfThenElse(())

  /**
   * Logical operator to decide which middleware to select based on the
   * predicate effect.
   */
  def ifThenElseZIO[A]: PartialIfThenElseZIO[A] = new PartialIfThenElseZIO(())

  /**
   * Logical operator to decide which middleware to select based on the
   * predicate effect.
   */
  def ifThenElseZIOStatic[A]: PartialIfThenElseZIOStatic[A] = new PartialIfThenElseZIOStatic(())

  /**
   * Creates a new middleware using transformation functions
   */
  def intercept[A, B]: PartialIntercept[A, B] = new PartialIntercept[A, B](())

  /**
   * Creates a new middleware using effectful transformation functions
   */
  def interceptZIO[A, B]: PartialInterceptZIO[A, B] = new PartialInterceptZIO[A, B](())

  /**
   * Creates a middleware which always succeed with specified value
   */
  def succeed[B](b: B): Middleware[Any, Nothing, Nothing, Any, Any, B, IT.Id[Nothing]] = fromHttp(Http.succeed(b))

  /**
   * Creates a new middleware using two transformation functions, one that's
   * applied to the incoming type of the Http and one that applied to the
   * outgoing type of the Http.
   */
  def transform[AOut, BIn]: PartialMono[AOut, BIn] = new PartialMono[AOut, BIn]({})

  /**
   * Creates a new middleware using two transformation functions, one that's
   * applied to the incoming type of the Http and one that applied to the
   * outgoing type of the Http.
   */
  def transformZIO[AOut, BIn]: PartialMonoZIO[AOut, BIn] = new PartialMonoZIO[AOut, BIn]({})

  final class PartialMono[AOut, BIn](val unit: Unit) extends AnyVal {
    def apply[AIn, BOut](
      in: AOut => AIn,
      out: BIn => BOut,
    ): Middleware[Any, Nothing, AIn, BIn, AOut, BOut, IT.Contramap[AIn, AOut]] =
      new Middleware[Any, Nothing, AIn, BIn, AOut, BOut, IT.Contramap[AIn, AOut]] {

        override def inputTransformation: IT.Contramap[AIn, AOut] = IT.Contramap(in)

        override def apply[R1 <: Any, E1 >: Nothing](http: Http.Total[R1, E1, AIn, BIn])(implicit
          trace: Trace,
        ): Http.Total[R1, E1, AOut, BOut] =
          http.contramap(in).map(out)
      }
  }

  final class PartialMonoZIO[AOut, BIn](val unit: Unit) extends AnyVal {
    def apply[R, E, AIn, BOut](
      in: AOut => ZIO[R, E, AIn],
      out: BIn => ZIO[R, E, BOut],
    ): Middleware[R, E, AIn, BIn, AOut, BOut, IT.Impossible[AIn]] =
      new Middleware[R, E, AIn, BIn, AOut, BOut, IT.Impossible[AIn]] {

        override def inputTransformation: IT.Impossible[AIn] = IT.Impossible()

        override def apply[R1 <: R, E1 >: E](http: Http.Total[R1, E1, AIn, BIn])(implicit
          trace: Trace,
        ): Http.Total[R1, E1, AOut, BOut] =
          http.contramapZIO(in).mapZIO(out)
      }
  }

  final class PartialIntercept[A, B](val unit: Unit) extends AnyVal {
    def apply[S, BOut](incoming: A => S)(
      outgoing: (B, S) => BOut,
    ): Middleware[Any, Nothing, A, B, A, BOut, IT.Id[A]] =
      interceptZIO[A, B](a => ZIO.succeedNow(incoming(a)))((b, s) => ZIO.succeedNow(outgoing(b, s)))
  }

  final class PartialInterceptZIO[A, B](val unit: Unit) extends AnyVal {
    def apply[R, E, S, BOut](
      incoming: A => ZIO[R, E, S],
    ): PartialInterceptOutgoingZIO[R, E, A, S, B] =
      new PartialInterceptOutgoingZIO(incoming)
  }

  final class PartialInterceptOutgoingZIO[-R, +E, A, +S, B](val incoming: A => ZIO[R, E, S]) extends AnyVal {
    def apply[R1 <: R, E1 >: E, BOut](
      outgoing: (B, S) => ZIO[R1, E1, BOut],
    ): Middleware[R1, E1, A, B, A, BOut, IT.Id[A]] =
      new Middleware[R1, E1, A, B, A, BOut, IT.Id[A]] {

        override def inputTransformation: IT.Id[A] = IT.Id()

        override def apply[R2 <: R1, E2 >: E1](
          http: Http.Total[R2, E2, A, B],
        )(implicit trace: Trace): Http.Total[R2, E2, A, BOut] =
          Http.fromFunctionZIO { a =>
            for {
              s <- incoming(a)
              b <- http.toZIO(a)
              c <- outgoing(b, s)
            } yield c
          }
      }
  }

  final class PartialCodec[AOut, BIn](val unit: Unit) extends AnyVal {
    def apply[E, AIn, BOut](
      decoder: AOut => Either[E, AIn],
      encoder: BIn => Either[E, BOut],
    ): Middleware[Any, E, AIn, BIn, AOut, BOut, IT.Impossible[AIn]] =
      new Middleware[Any, E, AIn, BIn, AOut, BOut, IT.Impossible[AIn]] {

        override def inputTransformation: IT.Impossible[AIn] = IT.Impossible()

        override def apply[R1 <: Any, E1 >: E](http: Http.Total[R1, E1, AIn, BIn])(implicit
          trace: Trace,
        ): Http.Total[R1, E1, AOut, BOut] =
          http.mapZIO(b => ZIO.fromEither(encoder(b))).contramapZIO(a => ZIO.fromEither(decoder(a)))
      }
  }

  final class PartialIfThenElse[AOut](val unit: Unit) extends AnyVal {
    def apply[R, E, AIn, BIn, BOut, AIn1 <: AIn, AIn2 <: AIn, AInT1 <: IT[AIn1], AInT2 <: IT[AIn2], AInRT <: IT[AIn]](
      cond: AOut => Boolean,
    )(
      isTrue: AOut => Middleware[R, E, AIn1, BIn, AOut, BOut, AInT1],
      isFalse: AOut => Middleware[R, E, AIn2, BIn, AOut, BOut, AInT2],
    )(implicit
      ev: ITIfThenElse.Aux[AIn, AOut, AIn1, AIn2, AInT1, AInT2, AInRT],
    ): Middleware[R, E, AIn, BIn, AOut, BOut, AInRT] =
      new Middleware[R, E, AIn, BIn, AOut, BOut, AInRT] {

        override def inputTransformation: AInRT =
          ev.ifThenElse(cond, isTrue(_).inputTransformation, isFalse(_).inputTransformation)

        override def apply[R1 <: R, E1 >: E](http: Http.Total[R1, E1, AIn, BIn])(implicit
          trace: Trace,
        ): Http.Total[R1, E1, AOut, BOut] =
          Http.fromFunctionHExit { a =>
            if (cond(a)) isTrue(a)(http).executeTotal(a)
            else isFalse(a)(http).executeTotal(a)
          }
      }
  }

  final class PartialIfThenElseZIO[AOut](val unit: Unit) extends AnyVal {
    def apply[R, E, AIn, BIn, BOut, AInT1 <: IT[AIn], AInT2 <: IT[AIn]](
      cond: AOut => ZIO[R, E, Boolean],
    )(
      isTrue: AOut => Middleware[R, E, AIn, BIn, AOut, BOut, AInT1],
      isFalse: AOut => Middleware[R, E, AIn, BIn, AOut, BOut, AInT2],
    ): Middleware[R, E, AIn, BIn, AOut, BOut, IT.Impossible[AIn]] =
      new Middleware[R, E, AIn, BIn, AOut, BOut, IT.Impossible[AIn]] {

        override def inputTransformation: IT.Impossible[AIn] = IT.Impossible()

        override def apply[R1 <: R, E1 >: E](http: Http.Total[R1, E1, AIn, BIn])(implicit
          trace: Trace,
        ): Http.Total[R1, E1, AOut, BOut] =
          Http.fromFunctionZIO[AOut](a => cond(a).map(b => if (b) isTrue(a)(http) else isFalse(a)(http))).flatten
      }
  }

  final class PartialIfThenElseZIOStatic[AOut](val unit: Unit) extends AnyVal {
    def apply[R, E, AIn, BIn, BOut, AIn2 <: AIn, AInT1 <: IT[AIn], AInT2 <: IT[AIn2], AInTR <: IT[AIn]](
      cond: AOut => ZIO[R, E, Boolean],
    )(
      isTrue: Middleware[R, E, AIn, BIn, AOut, BOut, AInT1],
      isFalse: Middleware[R, E, AIn2, BIn, AOut, BOut, AInT2],
    )(implicit ev: ITOrElse.Aux[AInT1, AInT2, AInTR]): Middleware[R, E, AIn, BIn, AOut, BOut, AInTR] =
      new Middleware[R, E, AIn, BIn, AOut, BOut, AInTR] {

        override def inputTransformation: AInTR = ev.orElse(isTrue.inputTransformation, isFalse.inputTransformation)

        override def apply[R1 <: R, E1 >: E](http: Http.Total[R1, E1, AIn, BIn])(implicit
          trace: Trace,
        ): Http.Total[R1, E1, AOut, BOut] =
          Http.fromFunctionZIO[AOut](a => cond(a).map(b => if (b) isTrue(http) else isFalse(http))).flatten
      }
  }

  final class PartialCodecZIO[AOut, BIn](val unit: Unit) extends AnyVal {
    def apply[R, E, AIn, BOut](
      decoder: AOut => ZIO[R, E, AIn],
      encoder: BIn => ZIO[R, E, BOut],
    ): Middleware[R, E, AIn, BIn, AOut, BOut, IT.Impossible[AIn]] =
      new Middleware[R, E, AIn, BIn, AOut, BOut, IT.Impossible[AIn]] {

        override def inputTransformation: IT.Impossible[AIn] = IT.Impossible()

        override def apply[R1 <: R, E1 >: E](http: Http.Total[R1, E1, AIn, BIn])(implicit
          trace: Trace,
        ): Http.Total[R1, E1, AOut, BOut] =
          http.mapZIO(encoder).contramapZIO(decoder)
      }
  }

  final class PartialCodecHttp[AOut, BIn](val unit: Unit) extends AnyVal {
    def apply[R, E, AIn, BOut](
      decoder: Http.Total[R, E, AOut, AIn],
      encoder: Http.Total[R, E, BIn, BOut],
    ): Middleware[R, E, AIn, BIn, AOut, BOut, IT.Impossible[AIn]] =
      new Middleware[R, E, AIn, BIn, AOut, BOut, IT.Impossible[AIn]] {

        override def inputTransformation: IT.Impossible[AIn] = IT.Impossible()

        override def apply[R1 <: R, E1 >: E](http: Http.Total[R1, E1, AIn, BIn])(implicit
          trace: Trace,
        ): Http.Total[R1, E1, AOut, BOut] =
          decoder >>> http >>> encoder
      }
  }

  final class PartialContraMap[-R, +E, +AIn, -BIn, -AOut, +BOut, +AInT <: IT[AIn], AOut0](
    val self: Middleware[R, E, AIn, BIn, AOut, BOut, AInT],
  ) extends AnyVal {
    def apply[AIn0 >: AIn, AOut1 <: AOut, AInT0 >: AInT <: IT[AIn0], AInTR <: IT[AIn0]](f: AOut0 => AOut1)(implicit
      ev: ITAndThen.Aux[AInT0, IT.Contramap[AOut1, AOut0], AInTR],
    ): Middleware[R, E, AIn0, BIn, AOut0, BOut, AInTR] =
      new Middleware[R, E, AIn0, BIn, AOut0, BOut, AInTR] {

        override def inputTransformation: AInTR =
          ev.andThen(self.inputTransformation, IT.Contramap(f))

        override def apply[R1 <: R, E1 >: E](http: Http.Total[R1, E1, AIn0, BIn])(implicit
          trace: Trace,
        ): Http.Total[R1, E1, AOut0, BOut] =
          self(http).contramap(f)
      }
  }

  final class PartialContraMapZIO[-R, +E, +AIn, -BIn, -AOut, +BOut, AOut0, +AInT <: IT[AIn]](
    val self: Middleware[R, E, AIn, BIn, AOut, BOut, AInT],
  ) extends AnyVal {

    def apply[R1 <: R, E1 >: E](
      f: AOut0 => ZIO[R1, E1, AOut],
    ): Middleware[R1, E1, AIn, BIn, AOut0, BOut, IT.Impossible[AIn]] =
      new Middleware[R1, E1, AIn, BIn, AOut0, BOut, IT.Impossible[AIn]] {

        override def inputTransformation: IT.Impossible[AIn] = IT.Impossible()

        override def apply[R2 <: R1, E2 >: E1](http: Http.Total[R2, E2, AIn, BIn])(implicit
          trace: Trace,
        ): Http.Total[R2, E2, AOut0, BOut] =
          self(http).contramapZIO(a => f(a))
      }
  }

  private final case class Identity[AIn, BIn, AOut, BOut]()
      extends Middleware[Any, Nothing, AIn, BIn, AOut, BOut, IT.Id[AIn]] {

    override def inputTransformation: IT.Id[AIn] = IT.Id()

    override def apply[R1 <: Any, E1 >: Nothing](http: Http.Total[R1, E1, AIn, BIn])(implicit
      trace: Trace,
    ): Http.Total[R1, E1, AOut, BOut] =
      http.asInstanceOf[Http.Total[R1, E1, AOut, BOut]]
  }
}
