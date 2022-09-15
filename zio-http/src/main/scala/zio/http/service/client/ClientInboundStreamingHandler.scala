package zio.http.service.client

import io.netty.channel.{ChannelHandlerContext, SimpleChannelInboundHandler}
import io.netty.handler.codec.http._
import zhttp.http.Request
import zhttp.service.{CLIENT_INBOUND_HANDLER, CLIENT_STREAMING_BODY_HANDLER, HttpRuntime}
import zio.Promise
import zio.http.service.{ClientRequestHandler, ClientResponseStreamHandler}

final class ClientInboundStreamingHandler[R](
  val zExec: HttpRuntime[R],
  req: Request,
  promise: Promise[Throwable, Response],
) extends SimpleChannelInboundHandler[HttpObject](false)
    with ClientRequestHandler[R] {

  override def channelActive(ctx: ChannelHandlerContext): Unit = {
    writeRequest(req)(ctx): Unit
  }

  override def channelRead0(ctx: ChannelHandlerContext, msg: HttpObject): Unit = {
    msg match {
      case response: HttpResponse =>
        ctx.channel().config().setAutoRead(false)
        zExec.unsafeRun(ctx) {
          promise
            .succeed(
              Response.unsafeFromJResponse(
                response,
                HttpData.UnsafeAsync { callback =>
                  ctx
                    .pipeline()
                    .addAfter(
                      CLIENT_INBOUND_HANDLER,
                      CLIENT_STREAMING_BODY_HANDLER,
                      new ClientResponseStreamHandler(callback(ctx)),
                    ): Unit
                },
              ),
            )
            .uninterruptible

        }
      case content: HttpContent   =>
        ctx.fireChannelRead(content): Unit

      case err => throw new IllegalStateException(s"Client unexpected message type: ${err}")
    }
  }

  override def exceptionCaught(ctx: ChannelHandlerContext, error: Throwable): Unit = {
    zExec.unsafeRun(ctx)(promise.fail(error).uninterruptible)
  }

}
