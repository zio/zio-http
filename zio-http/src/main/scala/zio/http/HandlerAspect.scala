package zio.http

import zio.{Trace, ZIO}

import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

trait HandlerAspect[-R, +Err, +AIn, -AOut, -BIn, +BOut] { self =>
  def apply[R1 <: R, Err1 >: Err](handler: Handler[R1, Err1, AIn, AOut])(implicit
    trace: Trace,
  ): Handler[R1, Err1, BIn, BOut]

  def toMiddleware[AIn1 >: AIn, BIn1 <: BIn](implicit
    ev: AIn1 <:< BIn1,
  ): HandlerMiddleware[R, Err, AIn1, AOut, AIn1, BOut] =
    new HandlerMiddleware[R, Err, AIn1, AOut, AIn1, BOut] {
      override def apply[R1 <: R, Err1 >: Err](handler: Handler[R1, Err1, AIn1, AOut])(implicit
        trace: Trace,
      ): Handler[R1, Err1, AIn1, BOut] =
        self(handler).contramap(ev.apply)
    }
}

object HandlerAspect {
  def codec[BIn, AOut]: Codec[BIn, AOut] = new Codec[BIn, AOut](())

  def codecHttp[BIn, AOut]: CodecHttp[BIn, AOut] = new CodecHttp[BIn, AOut](())

  def codecZIO[BIn, AOut]: CodecZIO[BIn, AOut] = new CodecZIO[BIn, AOut](())

  def identity[AIn, AOut]: HandlerMiddleware[Any, Nothing, AIn, AOut, AIn, AOut] =
    new HandlerMiddleware[Any, Nothing, AIn, AOut, AIn, AOut] {
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
    ): HandlerAspect[Any, Err, AIn, AOut, BIn, BOut] =
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
    ): HandlerAspect[R, Err, AIn, AOut, BIn, BOut] =
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
    ): HandlerAspect[R, Err, AIn, AOut, BIn, BOut] =
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
