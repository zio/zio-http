package zhttp.service.client

import io.netty.channel.{ChannelHandlerContext, SimpleChannelInboundHandler}
import io.netty.handler.codec.http.FullHttpResponse
import zhttp.http.{Request, Response}
import zhttp.service.{Client, HttpRuntime}
import zhttp.service.server.content.handlers.ClientRequestHandler
import zio.Promise

/**
 * Handles HTTP response
 */
final class ClientInboundHandler[R](
  val rt: HttpRuntime[R],
  req: Request,
  promise: Promise[Throwable, Response],
  isWebSocket: Boolean,
  val config: Client.Config,
) extends SimpleChannelInboundHandler[FullHttpResponse](true)
    with ClientRequestHandler[R] {

  override def channelActive(ctx: ChannelHandlerContext): Unit = {
    if (isWebSocket) {
      ctx.fireChannelActive(): Unit
    } else {
      writeRequest(req)(ctx)
      ()
    }
  }

  override def channelRead0(ctx: ChannelHandlerContext, msg: FullHttpResponse): Unit = {
    msg.touch("handlers.ClientInboundHandler-channelRead0")

    rt.unsafeRun(ctx)(promise.succeed(Response.unsafeFromJResponse(msg)))
    if (isWebSocket) {
      ctx.fireChannelRead(msg.retain())
      ctx.pipeline().remove(ctx.name()): Unit
    }
  }

  override def exceptionCaught(ctx: ChannelHandlerContext, error: Throwable): Unit = {
    rt.unsafeRun(ctx)(promise.fail(error))
//    releaseRequest()
  }

}
