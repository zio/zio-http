package zio.http

import zio._
import zio.http.model.Status

trait Route[-R, +Err, -In, +Out] { self =>

  final def @@[R1 <: R, Err1 >: Err, In1 <: In, Out1 >: Out, In2, Out2](
    aspect: RouteAspect[R1, Err1, In1, Out1, In2, Out2],
  ): Route[R1, Err1, In2, Out2] =
    aspect(self)

  final def @@[R1 <: R, Err1 >: Err, In1 <: In, Out1 >: Out, In2 <: In, Out2](
    aspect: HandlerAspect[R1, Err1, In1, Out1, In2, Out2],
  ): Route[R1, Err1, In2, Out2] =
    Route.fromHandlerHExit[In2] { in =>
      self.toHandlerOrNull(in).flatMap { handler =>
        if (handler eq null) HExit.succeed(null)
        else HExit.succeed(aspect(handler))
      }
    }

  final def ++[R1 <: R, Err1 >: Err, In1 <: In, Out1 >: Out](
    that: Route[R1, Err1, In1, Out1],
  ): Route[R1, Err1, In1, Out1] =
    self.defaultWith(that)

  final def defaultWith[R1 <: R, Err1 >: Err, In1 <: In, Out1 >: Out](
    that: Route[R1, Err1, In1, Out1],
  ): Route[R1, Err1, In1, Out1] =
    Route.fromHandlerHExit[In1] { in =>
      self.toHandlerOrNull(in).flatMap { handler =>
        if (handler eq null) that.toHandlerOrNull(in)
        else HExit.succeed(handler)
      }
    }

  final def map[Out1](f: Out => Out1)(implicit trace: Trace): Route[R, Err, In, Out1] =
    Route.fromHandlerHExit[In] { in =>
      self.toHandlerOrNull(in).map { handler =>
        if (handler eq null) null
        else handler.map(f)
      }
    }

  final def mapError[Err1](f: Err => Err1)(implicit trace: Trace): Route[R, Err1, In, Out] =
    Route.fromHandlerHExit[In] { in =>
      self.toHandlerOrNull(in).mapError(f).map { handler =>
        if (handler eq null) null
        else handler.mapError(f)
      }
    }

  final def provideEnvironment(r: ZEnvironment[R])(implicit trace: Trace): Route[Any, Err, In, Out] =
    Route.fromHandlerHExit[In] { in =>
      self
        .toHandlerOrNull(in)
        .map { handler =>
          if (handler ne null) handler.provideEnvironment(r)
          else null
        }
        .provideEnvironment(r)
    }

  final def provideLayer[Err1 >: Err, R0](layer: ZLayer[R0, Err1, R])(implicit
    trace: Trace,
  ): Route[R0, Err1, In, Out] =
    Route.fromHandlerHExit[In] { in =>
      self
        .toHandlerOrNull(in)
        .map { handler =>
          if (handler ne null) handler.provideLayer(layer)
          else null
        }
        .provideLayer(layer)
    }

  final def provideSomeEnvironment[R1](f: ZEnvironment[R1] => ZEnvironment[R])(implicit
    trace: Trace,
  ): Route[R1, Err, In, Out] =
    Route.fromHandlerHExit[In] { in =>
      self
        .toHandlerOrNull(in)
        .map { handler =>
          if (handler ne null) handler.provideSomeEnvironment(f)
          else null
        }
        .provideSomeEnvironment(f)
    }

  final def provideSomeLayer[R0, R1: Tag, Err1 >: Err](
    layer: ZLayer[R0, Err1, R1],
  )(implicit ev: R0 with R1 <:< R, trace: Trace): Route[R0, Err1, In, Out] =
    Route.fromHandlerHExit[In] { in =>
      self
        .toHandlerOrNull(in)
        .map { handler =>
          if (handler ne null) handler.provideSomeLayer(layer)
          else null
        }
        .provideSomeLayer(layer)
    }

  final def tapAllZIO[R1 <: R, Err1 >: Err](
    onFailure: Cause[Err] => ZIO[R1, Err1, Any],
    onSuccess: Out => ZIO[R1, Err1, Any],
    onUnhandled: ZIO[R1, Err1, Any],
  )(implicit trace: Trace): Route[R1, Err1, In, Out] =
    Route.fromHandlerHExit[In] { in =>
      self.toHandlerOrNull(in).flatMap { handler =>
        if (handler eq null) HExit.fromZIO(onUnhandled) *> HExit.succeed(null)
        else HExit.succeed(handler.tapAllZIO(onFailure, onSuccess))
      }
    }

  final def tapErrorCauseZIO[R1 <: R, Err1 >: Err](
    f: Cause[Err] => ZIO[R1, Err1, Any],
  )(implicit trace: Trace): Route[R1, Err1, In, Out] =
    self.tapAllZIO(f, _ => ZIO.unit, ZIO.unit)

  final def tapErrorZIO[R1 <: R, Err1 >: Err](
    f: Err => ZIO[R1, Err1, Any],
  )(implicit trace: Trace): Route[R1, Err1, In, Out] =
    self.tapAllZIO(cause => cause.failureOption.fold[ZIO[R1, Err1, Any]](ZIO.unit)(f), _ => ZIO.unit, ZIO.unit)

  final def tapUnhandledZIO[R1 <: R, Err1 >: Err](
    f: ZIO[R1, Err1, Any],
  )(implicit trace: Trace): Route[R1, Err1, In, Out] =
    self.tapAllZIO(_ => ZIO.unit, _ => ZIO.unit, f)

  final def tapZIO[R1 <: R, Err1 >: Err](f: Out => ZIO[R1, Err1, Any])(implicit
    trace: Trace,
  ): Route[R1, Err1, In, Out] =
    self.tapAllZIO(_ => ZIO.unit, f, ZIO.unit)

  final def toHandler[R1 <: R, Err1 >: Err, In1 <: In, Out1 >: Out](default: Handler[R1, Err1, In1, Out1])(implicit
    trace: Trace,
  ): Handler[R1, Err1, In1, Out1] =
    Handler
      .fromFunctionZIO[In1] { in =>
        self.toHandlerOrNull(in).toZIO.map { handler =>
          if (handler ne null) handler
          else default
        }
      }
      .flatten

  // TODO: unsafe api
  private[zio] def toHandlerOrNull(in: In): HExit[R, Err, Handler[R, Err, In, Out]]

  final def toHandler(in: In): HExit[R, Err, Option[Handler[R, Err, In, Out]]] =
    self.toHandlerOrNull(in).map(Option(_))

  // TODO: unsafe api
  final private[zio] def toHExit(in: In): HExit[R, Err, Out] = {
    val hExit = toHExitOrNull(in)
    if (hExit ne null) hExit
    else HExit.die(new MatchError(in))
  }

  // TODO: unsafe api
  final private[zio] def toHExitOrNull(in: In): HExit[R, Err, Out] = {
    self.toHandlerOrNull(in).flatMap { handler =>
      if (handler ne null) handler.apply(in)
      else null
    }
  }

  final def toZIO(in: In)(implicit trace: Trace): ZIO[R, Option[Err], Out] = {
    val hExit = toHExitOrNull(in)
    if (hExit ne null) hExit.toZIO.mapError(Some(_))
    else ZIO.fail(None)
  }

  final def withMiddleware[R1 <: R, In1 <: In, In2, Out2](
    middleware: api.Middleware[R1, In2, Out2],
  )(implicit ev1: In1 <:< Request, ev2: Out <:< Response): HttpRoute[R1, Err] =
    middleware(self.asInstanceOf[HttpRoute[R, Err]])

  final def when[In1 <: In](f: In1 => Boolean)(implicit trace: Trace): Route[R, Err, In1, Out] =
    Route.fromHandlerHExit[In1] { in =>
      if (f(in)) self.toHandlerOrNull(in)
      else null
    }

  final def whenZIO[R1 <: R, Err1 >: Err, In1 <: In](
    f: In1 => ZIO[R1, Err1, Boolean],
  )(implicit trace: Trace): Route[R1, Err1, In1, Out] =
    Route.fromHandlerZIO { (in: In1) =>
      f(in).mapError(Some(_)).flatMap {
        case true  =>
          self.toHandlerOrNull(in).toZIO.mapError(Some(_)).flatMap { handler =>
            if (handler eq null) ZIO.fail(None)
            else ZIO.succeed(handler)
          }
        case false =>
          ZIO.fail(None)
      }
    }

  final def withDefaultErrorResponse(implicit trace: Trace, ev1: Request <:< In, ev2: Out <:< Response): App[R] =
    self.mapError { _ =>
      Response(status = Status.InternalServerError)
    }.asInstanceOf[App[R]]
}

object Route {

  def collect[In]: Collect[In] = new Collect[In](())

  def collectHandler[In]: CollectHandler[In] = new CollectHandler[In](())

  def collectZIO[In]: CollectZIO[In] = new CollectZIO[In](())

  def empty: Route[Any, Nothing, Any, Nothing] =
    (_: Any) => HExit.succeed(null)

  def fromHandler[R, Err, In, Out](handler: Handler[R, Err, In, Out])(implicit trace: Trace): Route[R, Err, In, Out] =
    (_: In) => HExit.succeed(handler)

  // TODO: unsafe api
  private[zio] def fromHandlerHExit[In] = new FromHandlerHExit[In](())

  def fromHandlerZIO[In]: FromHandlerZIO[In] = new FromHandlerZIO[In](())

  final class Collect[In](val self: Unit) extends AnyVal {
    def apply[Out](pf: PartialFunction[In, Out])(implicit trace: Trace): Route[Any, Nothing, In, Out] =
      Route.collectHandler[In].apply(pf.andThen(Handler.succeed(_)))
  }

  final class CollectHandler[In](val self: Unit) extends AnyVal {
    def apply[R, Err, Out](pf: PartialFunction[In, Handler[R, Err, In, Out]])(implicit
      trace: Trace,
    ): Route[R, Err, In, Out] =
      (in: In) => HExit.succeed(pf.applyOrElse(in, (_: In) => null))
  }

  final class CollectZIO[In](val self: Unit) extends AnyVal {
    def apply[R, Err, Out](pf: PartialFunction[In, ZIO[R, Err, Out]])(implicit
      trace: Trace,
    ): Route[R, Err, In, Out] =
      Route.collectHandler[In].apply(pf.andThen(Handler.fromZIO(_)))
  }

  private[zio] final class FromHandlerHExit[In](val self: Unit) extends AnyVal {
    def apply[R, Err, Out](f: In => HExit[R, Err, Handler[R, Err, In, Out]])(implicit
      trace: Trace,
    ): Route[R, Err, In, Out] =
      (in: In) => f(in)
  }

  final class FromHandlerZIO[In](val self: Unit) extends AnyVal {
    def apply[R, Err, Out](f: In => ZIO[R, Option[Err], Handler[R, Err, In, Out]])(implicit
      trace: Trace,
    ): Route[R, Err, In, Out] =
      (in: In) =>
        HExit.fromZIO(f(in).catchAll {
          case None    => ZIO.succeed(null)
          case Some(e) => ZIO.fail(e)
        })
  }

  implicit class HttpRouteSyntax[R, Err](val self: HttpRoute[R, Err]) extends AnyVal {
    def whenPathEq(path: Path): HttpRoute[R, Err] =
      self.when[Request](_.path == path)

    def whenPathEq(path: String): HttpRoute[R, Err] =
      self.when[Request](_.path.encode == path)
  }
}
