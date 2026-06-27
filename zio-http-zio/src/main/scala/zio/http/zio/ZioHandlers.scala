package zio.http.zio

import zio._

import zio.http.ResultType._
import zio.http._

object ZioHandlers {
  private def runSync[A](effect: ZIO[Any, Any, A]): A =
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(effect).getOrThrowFiberFailure()
    }

  implicit val zioRequestHandler: ToHandler.Aux[Request => ZIO[Any, Response, Response], Any, Any] =
    new ToHandler[Request => ZIO[Any, Response, Response]] {
      type Ctx  = Any
      type Vars = Any

      override def toHandler(h: Request => ZIO[Any, Response, Response]): Handler[Ctx, Vars] =
        Handler.fromRequest { request =>
          try responseAsResult(runSync(h(request)))
          catch {
            case e: FiberFailure =>
              e.cause.failureOption match {
                case Some(response: Response) => haltAsResult(Halt(response))
                case _                        => throw e
              }
          }
        }
    }

  implicit val zioInfallibleHandler: ToHandler.Aux[ZIO[Any, Nothing, Response], Any, Any] =
    new ToHandler[ZIO[Any, Nothing, Response]] {
      type Ctx  = Any
      type Vars = Any

      override def toHandler(h: ZIO[Any, Nothing, Response]): Handler[Ctx, Vars] =
        Handler.fromRequest(_ => responseAsResult(runSync(h)))
    }
}
