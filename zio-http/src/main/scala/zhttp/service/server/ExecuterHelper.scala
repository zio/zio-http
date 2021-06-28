package zhttp.service.server

import zhttp.core.JChannelHandlerContext
import zhttp.http.{HttpError, HttpResult, Response, SilentResponse, _}
import zhttp.service.Server.Settings
import zhttp.service.{DecodeJRequest, UnsafeChannelExecutor}
import zio._

trait ExecuterHelper[R] extends DecodeJRequest {

  /**
   * Asynchronously executes the Http app and passes the response to the callback.
   */
  private[zhttp] def executeAsync(
    zExec: UnsafeChannelExecutor[R],
    settings: Settings[R, Throwable],
    ctx: JChannelHandlerContext,
    req: Request[Any, Nothing, Any],
  )(
    cb: Response[R, Throwable, Any] => Unit,
  ): Unit =
    settings.http.execute(req).evaluate match {
      case HttpResult.Empty      => cb(Response.fromHttpError(HttpError.NotFound(req.url.path)))
      case HttpResult.Success(a) => cb(a)
      case HttpResult.Failure(e) => cb(SilentResponse[Throwable].silent(e))
      case HttpResult.Effect(z)  =>
        zExec.unsafeExecute(ctx, z) {
          case Exit.Success(res)   => cb(res)
          case Exit.Failure(cause) =>
            cause.failureOption match {
              case Some(Some(e)) => cb(SilentResponse[Throwable].silent(e))
              case Some(None)    => cb(Response.fromHttpError(HttpError.NotFound(req.url.path)))
              case None          =>
                ctx.close()
                ()
            }
        }
    }
}
