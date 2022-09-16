package zio.http.service

import io.netty.channel.{ChannelHandlerContext, SimpleChannelInboundHandler}
import io.netty.handler.codec.http._
import zio.http.{Request, Response}
import zio.{Promise, Unsafe}

final class ClientInboundStreamingHandler[R](
  val zExec: HttpRuntime[R],
  req: Request,
  onResponse: Promise[Throwable, Response],
  onComplete: Promise[Throwable, Unit],
) extends SimpleChannelInboundHandler[HttpObject](false)
    with ClientRequestHandler[R] {

  private implicit val unsafeClass: Unsafe = Unsafe.unsafe

  override def handlerAdded(ctx: Ctx): Unit = {
    if (ctx.channel().isActive && ctx.channel().isRegistered) {
      writeRequest(req)(ctx): Unit
    }
  }

  override def channelRegistered(ctx: Ctx): Unit =
    super.channelRegistered(ctx)

  override def channelActive(ctx: ChannelHandlerContext): Unit = {
    writeRequest(req)(ctx): Unit
  }

  override def channelRead0(ctx: ChannelHandlerContext, msg: HttpObject): Unit = {
    msg match {
      case response: HttpResponse =>
        ctx.channel().config().setAutoRead(false)
        zExec.runUninterruptible {
          onResponse
            .succeed(
              Response.unsafe.fromStreamingJResponse(
                ctx,
                response,
                zExec,
                onComplete,
              ),
            )
        }(ctx, unsafeClass)
      case content: HttpContent   =>
        ctx.fireChannelRead(content): Unit

      case err => throw new IllegalStateException(s"Client unexpected message type: ${err}")
    }
  }

  override def exceptionCaught(ctx: ChannelHandlerContext, error: Throwable): Unit = {
    zExec.runUninterruptible {
      onResponse.fail(error) *> onComplete.fail(error)
    }(ctx, unsafeClass)
  }

}
