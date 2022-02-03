package zhttp.service

import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.{ChannelHandlerContext, SimpleChannelInboundHandler}
import io.netty.handler.codec.http._
import zhttp.http._
import zhttp.service.server.WebSocketUpgrade
import zio.{UIO, ZIO}

@Sharable
private[zhttp] final case class Handler[R](
  app: HttpApp[R, Throwable],
  runtime: HttpRuntime[R],
  config: Server.Config[R, Throwable],
) extends SimpleChannelInboundHandler[FullHttpRequest](false)
    with WebSocketUpgrade[R] { self =>

  type Ctx = ChannelHandlerContext

  override def channelRead0(ctx: Ctx, jReq: FullHttpRequest): Unit = {
    jReq.touch("server.Handler-channelRead0")
    implicit val iCtx: ChannelHandlerContext = ctx
    unsafeRun(
      jReq,
      app
    )
  }

  /**
   * Executes http apps
   */
  private def unsafeRun[A](
    jReq: FullHttpRequest,
    http: Http[R, Throwable, A, Response],
  )(implicit ctx: Ctx, ev2 : HttpConvertor[FullHttpRequest, A]): Unit = {
    http.execute(jReq) match {
      case HExit.Effect(resM) =>
        unsafeRunZIO {
          resM.foldM(
            {
              case Some(cause) =>
                UIO {
                  ctx.fireChannelRead(
                    (Response.fromHttpError(HttpError.InternalServerError(cause = Some(cause))), jReq),
                  )
                }
              case None        =>
                UIO {
                  ctx.fireChannelRead((Response.status(Status.NOT_FOUND), jReq))
                }
            },
            res =>
              if (self.isWebSocket(res)) UIO(self.upgradeToWebSocket(ctx, jReq, res))
              else {
                for {
                  _ <- UIO {
                    ctx.fireChannelRead((res, jReq))
                  }
                } yield ()
              },
          )
        }

      case HExit.Success(res) =>
        if (self.isWebSocket(res)) {
          self.upgradeToWebSocket(ctx, jReq, res)
        } else {
          ctx.fireChannelRead((res, jReq)): Unit
        }

      case HExit.Failure(e) =>
        ctx.fireChannelRead((e, jReq)): Unit
      case HExit.Empty      =>
        ctx.fireChannelRead((Response.status(Status.NOT_FOUND), jReq)): Unit
    }

  }

  /**
   * Executes program
   */
  private def unsafeRunZIO(program: ZIO[R, Throwable, Any])(implicit ctx: Ctx): Unit =
    runtime.unsafeRun(ctx) {
      program
    }
}
