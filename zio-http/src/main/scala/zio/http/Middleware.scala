package zio.http

import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.{Trace, ZIO}

import zio.http.middleware.{HttpRoutesMiddlewares, RequestHandlerMiddlewares}

trait Middleware[-R, +Err, +AIn, -AOut, -BIn, +BOut] { self =>

  def apply[R1 <: R, Err1 >: Err](http: Http[R1, Err1, AIn, AOut])(implicit
    trace: Trace,
  ): Http[R1, Err1, BIn, BOut]

  /**
   * Applies Middleware based only if the condition function evaluates to true
   */
  def when[BIn1 <: BIn](
    condition: BIn1 => Boolean,
  )(implicit trace: Trace, ev: IsMono[AIn, AOut, BIn, BOut]): Middleware[R, Err, AIn, AOut, BIn1, BOut] =
    new Middleware[R, Err, AIn, AOut, BIn1, BOut] {
      override def apply[R1 <: R, Err1 >: Err](http: Http[R1, Err1, AIn, AOut])(implicit
        trace: Trace,
      ): Http[R1, Err1, BIn1, BOut] =
        http.when(condition).asInstanceOf[Http[R1, Err1, BIn1, BOut]]
    }

  /**
   * Applies Middleware based only if the condition effectful function evaluates
   * to true
   */
  def whenZIO[R1 <: R, Err1 >: Err, BIn1 <: BIn](
    condition: BIn1 => ZIO[R1, Err1, Boolean],
  )(implicit trace: Trace, ev: IsMono[AIn, AOut, BIn, BOut]): Middleware[R1, Err1, AIn, AOut, BIn1, BOut] =
    new Middleware[R1, Err1, AIn, AOut, BIn1, BOut] {
      override def apply[R2 <: R1, Err2 >: Err1](http: Http[R2, Err2, AIn, AOut])(implicit
        trace: Trace,
      ): Http[R2, Err2, BIn1, BOut] =
        http.whenZIO(condition).asInstanceOf[Http[R2, Err2, BIn1, BOut]]

    }
}

object Middleware extends RequestHandlerMiddlewares with HttpRoutesMiddlewares {

  /**
   * Creates a middleware which can allow or disallow access to an http based on
   * the predicate
   */
  def allow[AIn, AOut]: Allow[AIn, AOut] = new Allow[AIn, AOut](())

  /**
   * Creates a middleware which can allow or disallow access to an http based on
   * the predicate effect
   */
  def allowZIO[AIn, AOut]: AllowZIO[AIn, AOut] = new AllowZIO[AIn, AOut](())

  /**
   * Creates a middleware using the specified encoder and decoder functions
   */
  def codec[BIn, AOut]: HandlerAspect.Codec[BIn, AOut] = HandlerAspect.codec[BIn, AOut]

  /**
   * Creates a codec middleware using two Http.
   */
  def codecHttp[BIn, AOut]: HandlerAspect.CodecHttp[BIn, AOut] = HandlerAspect.codecHttp[BIn, AOut]

  /**
   * Creates a middleware using specified effectful encoder and decoder
   */
  def codecZIO[BIn, AOut]: HandlerAspect.CodecZIO[BIn, AOut] = HandlerAspect.codecZIO[BIn, AOut]

  /**
   * An empty middleware that doesn't do perform any operations on the provided
   * Http and returns it as it is.
   */
  def identity[AIn, AOut]: Middleware[Any, Nothing, AIn, AOut, AIn, AOut] =
    new Middleware[Any, Nothing, AIn, AOut, AIn, AOut] {
      override def apply[R1 <: Any, Err1 >: Nothing](http: Http[R1, Err1, AIn, AOut])(implicit
        trace: Trace,
      ): Http[R1, Err1, AIn, AOut] =
        http
    }

  /**
   * Creates a new middleware using two transformation functions, one that's
   * applied to the incoming type of the Http and one that applied to the
   * outgoing type of the Http.
   */
  def transform[BIn, AOut]: HandlerAspect.Transform[BIn, AOut] = HandlerAspect.transform[BIn, AOut]

  final class Allow[AIn, AOut](val unit: Unit) extends AnyVal {
    def apply(condition: AIn => Boolean): Middleware[Any, Nothing, AIn, AOut, AIn, AOut] =
      new Middleware[Any, Nothing, AIn, AOut, AIn, AOut] {
        override def apply[R1 <: Any, Err1 >: Nothing](http: Http[R1, Err1, AIn, AOut])(implicit
          trace: Trace,
        ): Http[R1, Err1, AIn, AOut] =
          http.when(condition)
      }
  }

  final class AllowZIO[AIn, AOut](val unit: Unit) extends AnyVal {
    def apply[R, Err](condition: AIn => ZIO[R, Err, Boolean]): Middleware[R, Err, AIn, AOut, AIn, AOut] =
      new Middleware[R, Err, AIn, AOut, AIn, AOut] {
        override def apply[R1 <: R, Err1 >: Err](http: Http[R1, Err1, AIn, AOut])(implicit
          trace: Trace,
        ): Http[R1, Err1, AIn, AOut] =
          http.whenZIO(condition)
      }
  }
}
