package zio.http
package netty

import io.netty.handler.codec.http.{FullHttpResponse, HttpRequest}
import zio.ZIO
import zio.http.{HExit, HttpError, Response}
import io.netty.channel.ChannelHandlerContext

/**
 * Handles all advanced scenarios that are left out by the FastPassWriter. It
 * handles websockets, streaming and other side-effects.
 */
private[zio] trait ServerFullResponseWriter[R] {
  self: ServerInboundHandler[R] =>

  import ServerInboundHandler.{log, Unsafe}

  def attemptFullWrite[R1 >: R](exit: HExit[R1, Throwable, Response], jRequest: HttpRequest)(implicit
    ctx: ChannelHandlerContext,
  ): ZIO[R, Throwable, Unit] = {
    for {
      response <- exit.toZIO.unrefine { case error => Option(error) }.catchAll {
        case None        => ZIO.succeed(HttpError.NotFound(jRequest.uri()).toResponse)
        case Some(error) => ZIO.succeed(HttpError.InternalServerError(cause = Some(error)).toResponse)
      }
      _        <-
        if (response.isWebSocket) ZIO.attempt(self.upgradeToWebSocket(jRequest, response))
        else
          for {
            jResponse <- response.encode()
            _         <- ZIO.attempt(Unsafe.setServerTime(self.time, response, jResponse))
            _         <- ZIO.attempt(ctx.writeAndFlush(jResponse))
            flushed   <- if (!jResponse.isInstanceOf[FullHttpResponse]) response.body.write(ctx) else ZIO.succeed(true)
            _         <- ZIO.attempt(ctx.flush()).when(!flushed)
          } yield ()

      _ <- ZIO.attempt(Unsafe.setContentReadAttr(false))
    } yield log.debug("Full write performed")

  }
}
