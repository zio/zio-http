package zio.http.service

import io.netty.channel.{ChannelHandlerContext, SimpleChannelInboundHandler}
import io.netty.handler.codec.http._
import zio.http.{Request, Response}
import zio.{Promise, Unsafe}

final class ClientInboundStreamingHandler[R](
  val zExec: HttpRuntime[R],
  req: Request,
  promise: Promise[Throwable, Response],
) extends SimpleChannelInboundHandler[HttpObject](false)
    with ClientRequestHandler[R] {

  private implicit val unsafeClass: Unsafe = Unsafe.unsafe

  override def channelActive(ctx: ChannelHandlerContext): Unit = {
    writeRequest(req)(ctx): Unit
  }

  override def channelRead0(ctx: ChannelHandlerContext, msg: HttpObject): Unit = {
    msg match {
      case response: HttpResponse =>
        ctx.channel().config().setAutoRead(false)
        zExec.runUninterruptible {
          promise
            .succeed(
              Response.unsafe.fromStreamingJResponse(
                ctx,
                response,
              ),
            )
        }(ctx, unsafeClass)
      case content: HttpContent   =>
        ctx.fireChannelRead(content): Unit

      case err => throw new IllegalStateException(s"Client unexpected message type: ${err}")
    }
  }

  override def exceptionCaught(ctx: ChannelHandlerContext, error: Throwable): Unit = {
    zExec.run(promise.fail(error).uninterruptible)(ctx, unsafeClass)
  }

}
