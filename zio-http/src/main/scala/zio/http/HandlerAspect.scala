package zio.http

import zio.{Trace, ZIO}

trait HandlerAspect[-R, +Err, +AIn, -AOut, -BIn, +BOut] { self =>

  final def >>>[R1 <: R, Err1 >: Err, AIn1 <: BIn, AOut1 >: BOut, BIn1, BOut1](
    that: HandlerAspect[R1, Err1, AIn1, AOut1, BIn1, BOut1],
  ): HandlerAspect[R1, Err1, AIn, AOut, BIn1, BOut1] =
    self.andThen(that)

  final def ++[R1 <: R, Err1 >: Err, AIn1 <: BIn, AOut1 >: BOut, BIn1, BOut1](
    that: HandlerAspect[R1, Err1, AIn1, AOut1, BIn1, BOut1],
  ): HandlerAspect[R1, Err1, AIn, AOut, BIn1, BOut1] =
    self.andThen(that)

  final def andThen[R1 <: R, Err1 >: Err, AIn1 <: BIn, AOut1 >: BOut, BIn1, BOut1](
    that: HandlerAspect[R1, Err1, AIn1, AOut1, BIn1, BOut1],
  ): HandlerAspect[R1, Err1, AIn, AOut, BIn1, BOut1] =
    new HandlerAspect[R1, Err1, AIn, AOut, BIn1, BOut1] {
      override def apply[R2 <: R1, Err2 >: Err1](handler: Handler[R2, Err2, AIn, AOut])(implicit
        trace: Trace,
      ): Handler[R2, Err2, BIn1, BOut1] =
        that(self(handler))
    }

  def apply[R1 <: R, Err1 >: Err](handler: Handler[R1, Err1, AIn, AOut])(implicit
    trace: Trace,
  ): Handler[R1, Err1, BIn, BOut]

  def when[BIn1 <: BIn](
    condition: BIn1 => Boolean,
  )(implicit trace: Trace, ev: IsMono[AIn, AOut, BIn, BOut]): HandlerAspect[R, Err, AIn, AOut, BIn1, BOut] =
    new HandlerAspect[R, Err, AIn, AOut, BIn1, BOut] {
      override def apply[R1 <: R, Err1 >: Err](handler: Handler[R1, Err1, AIn, AOut])(implicit
        trace: Trace,
      ): Handler[R1, Err1, BIn1, BOut] =
        Handler.fromFunctionHandler[BIn1] { in =>
          if (condition(in)) self(handler)
          else handler.asInstanceOf[Handler[R1, Err1, BIn, BOut]]
        }
    }

  def whenZIO[R1 <: R, Err1 >: Err, BIn1 <: BIn](
    condition: BIn1 => ZIO[R1, Err1, Boolean],
  )(implicit trace: Trace, ev: IsMono[AIn, AOut, BIn, BOut]): HandlerAspect[R1, Err1, AIn, AOut, BIn1, BOut] =
    new HandlerAspect[R1, Err1, AIn, AOut, BIn1, BOut] {
      override def apply[R2 <: R1, Err2 >: Err1](handler: Handler[R2, Err2, AIn, AOut])(implicit
        trace: Trace,
      ): Handler[R2, Err2, BIn1, BOut] =
        Handler
          .fromFunctionZIO[BIn1] { in =>
            condition(in).map {
              case true  => self(handler)
              case false => handler.asInstanceOf[Handler[R2, Err2, BIn, BOut]]
            }
          }
          .flatten
    }
}

object HandlerAspect {
  def codec[BIn, AOut]: Codec[BIn, AOut] = new Codec[BIn, AOut](())

  def codecHttp[BIn, AOut]: CodecHttp[BIn, AOut] = new CodecHttp[BIn, AOut](())

  def codecZIO[BIn, AOut]: CodecZIO[BIn, AOut] = new CodecZIO[BIn, AOut](())

  def identity[AIn, AOut]: HandlerAspect[Any, Nothing, AIn, AOut, AIn, AOut] =
    new HandlerAspect[Any, Nothing, AIn, AOut, AIn, AOut] {
      override def apply[R1 <: Any, Err1 >: Nothing](handler: Handler[R1, Err1, AIn, AOut])(implicit
        trace: Trace,
      ): Handler[R1, Err1, AIn, AOut] =
        handler
    }

  def transform[BIn, AOut]: Transform[BIn, AOut] = new Transform[BIn, AOut](())

  final class Codec[BIn, AOut](val self: Unit) extends AnyVal {
    def apply[Err, AIn, BOut](
      decoder: BIn => Either[Err, AIn],
      encoder: AOut => Either[Err, BOut],
    )(implicit trace: Trace): HandlerAspect[Any, Err, AIn, AOut, BIn, BOut] =
      new HandlerAspect[Any, Err, AIn, AOut, BIn, BOut] {
        override def apply[R1 <: Any, Err1 >: Err](
          handler: Handler[R1, Err1, AIn, AOut],
        )(implicit trace: Trace): Handler[R1, Err1, BIn, BOut] =
          handler
            .contramapZIO((in: BIn) => ZIO.fromEither(decoder(in)))
            .mapZIO(out => ZIO.fromEither(encoder(out)))
      }
  }

  final class CodecHttp[BIn, AOut](val self: Unit) extends AnyVal {
    def apply[R, Err, AIn, BOut](
      decoder: Handler[R, Err, BIn, AIn],
      encoder: Handler[R, Err, AOut, BOut],
    )(implicit trace: Trace): HandlerAspect[R, Err, AIn, AOut, BIn, BOut] =
      new HandlerAspect[R, Err, AIn, AOut, BIn, BOut] {
        override def apply[R1 <: R, Err1 >: Err](
          handler: Handler[R1, Err1, AIn, AOut],
        )(implicit trace: Trace): Handler[R1, Err1, BIn, BOut] =
          decoder >>> handler >>> encoder
      }
  }

  final class CodecZIO[BIn, AOut](val self: Unit) extends AnyVal {
    def apply[R, Err, AIn, BOut](
      decoder: BIn => ZIO[R, Err, AIn],
      encoder: AOut => ZIO[R, Err, BOut],
    )(implicit trace: Trace): HandlerAspect[R, Err, AIn, AOut, BIn, BOut] =
      new HandlerAspect[R, Err, AIn, AOut, BIn, BOut] {
        override def apply[R1 <: R, Err1 >: Err](
          handler: Handler[R1, Err1, AIn, AOut],
        )(implicit trace: Trace): Handler[R1, Err1, BIn, BOut] =
          handler
            .contramapZIO((in: BIn) => decoder(in))
            .mapZIO(out => encoder(out))
      }
  }

  final class Transform[BIn, AOut](val self: Unit) extends AnyVal {
    def apply[AIn, BOut](in: BIn => AIn, out: AOut => BOut): HandlerAspect[Any, Nothing, AIn, AOut, BIn, BOut] =
      new HandlerAspect[Any, Nothing, AIn, AOut, BIn, BOut] {
        override def apply[R1 <: Any, Err1 >: Nothing](handler: Handler[R1, Err1, AIn, AOut])(implicit
          trace: Trace,
        ): Handler[R1, Err1, BIn, BOut] =
          handler.contramap(in).map(out)
      }
  }
}
