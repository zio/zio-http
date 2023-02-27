package zio.http
import zio.{Trace, ZEnvironment, ZIO}
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

trait HandlerMiddleware[-RReq, -RProv, +Err, +AIn, -AOut, -BIn <: AIn, +BOut]
    extends HandlerAspect[RReq, Err, AIn, AOut, BIn, BOut]
    with Middleware[RReq, Err, AIn, AOut, BIn, BOut] { self =>

  final def >>>[RReq1 <: RReq, RProv1 <: RProv, Err1 >: Err, AIn1 <: BIn, AOut1 >: BOut, BIn1 <: AIn1, BOut1](
    that: HandlerMiddleware[RReq1, RProv1, Err1, AIn1, AOut1, BIn1, BOut1],
  ): HandlerMiddleware[RReq1, RProv1, Err1, AIn, AOut, BIn1, BOut1] =
    self.andThen(that)

  final def ++[RReq1 <: RReq, RProv1 <: RProv, Err1 >: Err, AIn1 <: BIn, AOut1 >: BOut, BIn1 <: AIn1, BOut1](
    that: HandlerMiddleware[RReq1, RProv1, Err1, AIn1, AOut1, BIn1, BOut1],
  ): HandlerMiddleware[RReq1, RProv1, Err1, AIn, AOut, BIn1, BOut1] =
    self.andThen(that)

  final def andThen[RReq1 <: RReq, RProv1 <: RProv, Err1 >: Err, AIn1 <: BIn, AOut1 >: BOut, BIn1 <: AIn1, BOut1](
    that: HandlerMiddleware[RReq1, RProv1, Err1, AIn1, AOut1, BIn1, BOut1],
  ): HandlerMiddleware[RReq1, RProv1, Err1, AIn, AOut, BIn1, BOut1] =
    new HandlerMiddleware[RReq1, RProv1, Err1, AIn, AOut, BIn1, BOut1] {

      override def context[RReq2 <: RReq1, RProv2 <: RProv1, Err2 >: Err1, AIn2 >: AIn](in: AIn2)(implicit
        trace: Trace,
      ): ZIO[RReq2, Err2, ZEnvironment[RProv2]] =
        self
          .context(in)
          .flatMap(ctx1 => that.context(in).map(ctx2 => (ctx1 ++ ctx2).asInstanceOf[ZEnvironment[RProv2]]))

      override def apply[RReq2 <: RReq1, Ctx, Err2 >: Err1](handler: Handler[RReq2, Ctx, Err2, AIn, AOut])(implicit
        trace: Trace,
      ): Handler[RReq2, Ctx, Err2, BIn1, BOut1] =
        that(self(handler))
    }

  def apply[R1 <: RReq, Ctx, Err1 >: Err](
    http: Http[R1, Ctx, Err1, AIn, AOut],
  )(implicit trace: Trace): Http[R1, Ctx, Err1, BIn, BOut] =
    http match {
      case Http.Empty                                  => Http.empty
      case Http.Static(handler)                        => Http.Static(self(handler))
      case route: Http.Route[R1, Ctx, Err1, AIn, AOut] =>
        Http.fromHttpZIOCtx[BIn, Ctx].apply[R1, Err1, BOut] { (in: BIn) =>
          route.run(in).map { http: Http[R1, Ctx, Err1, AIn, AOut] =>
            self.asInstanceOf[HandlerMiddleware[R1, Ctx, Err1, AIn, AOut, BIn, BOut]].apply(http)
          }
        }
    }

  def context[R1 <: RReq, RProv1 <: RProv, Err1 >: Err, AIn1 >: AIn](in: AIn1)(implicit
    trace: Trace,
  ): ZIO[R1, Err1, ZEnvironment[RProv1]]

  override def when[BIn1 <: BIn](
    condition: BIn1 => Boolean,
  )(implicit
    trace: Trace,
    ev: IsMono[AIn, AOut, BIn, BOut],
    ev2: RProv =:= Any,
  ): HandlerMiddleware[RReq, Any, Err, AIn, AOut, BIn1, BOut] =
    new HandlerMiddleware[RReq, Any, Err, AIn, AOut, BIn1, BOut] {

      override def context[R1 <: RReq, RProv1 <: Any, Err1 >: Err, AIn1 >: AIn](in: AIn1)(implicit
        trace: Trace,
      ): ZIO[R1, Err1, ZEnvironment[RProv1]] =
        ZIO.succeed(ZEnvironment.empty.asInstanceOf[ZEnvironment[RProv1]])

      override def apply[R1 <: RReq, Ctx <: RProv, Err1 >: Err](handler: Handler[R1, Ctx, Err1, AIn, AOut])(implicit
        trace: Trace,
      ): Handler[R1, Ctx, Err1, BIn1, BOut] =
        Handler.fromFunctionHandler[BIn1] { in =>
          if (condition(in)) self(handler)
          else handler.asInstanceOf[Handler[R1, Ctx, Err1, BIn, BOut]]
        }
    }

  override def whenZIO[RReq1 <: RReq, Err1 >: Err, BIn1 <: BIn](
    condition: BIn1 => ZIO[RReq1, Err1, Boolean],
  )(implicit
    trace: Trace,
    ev: IsMono[AIn, AOut, BIn, BOut],
    ev2: RProv =:= Any,
  ): HandlerMiddleware[RReq1, Any, Err1, AIn, AOut, BIn1, BOut] =
    new HandlerMiddleware[RReq1, Any, Err1, AIn, AOut, BIn1, BOut] {

      override def context[R1 <: RReq1, RProv1 <: Any, Err2 >: Err1, AIn1 >: AIn](in: AIn1)(implicit
        trace: Trace,
      ): ZIO[R1, Err2, ZEnvironment[RProv1]] =
        ZIO.succeed(ZEnvironment.empty.asInstanceOf[ZEnvironment[RProv1]])

      override def apply[R2 <: RReq1, Ctx, Err2 >: Err1](handler: Handler[R2, Ctx, Err2, AIn, AOut])(implicit
        trace: Trace,
      ): Handler[R2, Ctx, Err2, BIn1, BOut] =
        Handler
          .fromFunctionZIO[BIn1] { in =>
            condition(in).map {
              case true  => self(handler)
              case false => handler.asInstanceOf[Handler[R2, Ctx, Err2, BIn, BOut]]
            }
          }
          .flatten
    }
}
