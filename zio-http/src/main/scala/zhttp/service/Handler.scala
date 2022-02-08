package zhttp.service

import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.{ChannelHandlerContext, SimpleChannelInboundHandler}
import io.netty.handler.codec.http._
import zhttp.http._
import zhttp.service.server.WebSocketUpgrade
import zio.{Task, UIO, ZIO}

import java.net.{InetAddress, InetSocketAddress}

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
      app,
      new Request {
        override def method: Method = Method.fromHttpMethod(jReq.method())

        override def url: URL = URL.fromString(jReq.uri()).getOrElse(null)

        override def headers: Headers = Headers.make(jReq.headers())

        override private[zhttp] def bodyAsByteBuf: Task[ByteBuf] = Task(jReq.content())

        override def remoteAddress: Option[InetAddress] = {
          ctx.channel().remoteAddress() match {
            case m: InetSocketAddress => Some(m.getAddress)
            case _                    => None
          }
        }
      },
    )
  }

  /**
   * Executes http apps
   */
  private def unsafeRun[A](
    jReq: FullHttpRequest,
    http: Http[R, Throwable, A, Response],
    a: A,
  )(implicit ctx: Ctx): Unit = {
    http.execute(a) match {
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
        ctx.fireChannelRead((Response.fromHttpError(HttpError.InternalServerError(cause = Some(e))), jReq)): Unit
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
