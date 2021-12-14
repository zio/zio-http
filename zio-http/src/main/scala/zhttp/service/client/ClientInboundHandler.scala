package zhttp.service.client

import io.netty.channel.{ChannelHandlerContext, SimpleChannelInboundHandler}
import io.netty.handler.codec.http.FullHttpRequest
import zhttp.service.Client.ClientResponse
import zhttp.service.HttpRuntime
import zio.Promise

/**
 * Handles HTTP response
 */
final case class ClientInboundHandler[R](
  zExec: HttpRuntime[R],
  jReq: FullHttpRequest,
  promise: Promise[Throwable, ClientResponse],
) extends SimpleChannelInboundHandler[ClientResponse](false) {

  override def channelRead0(ctx: ChannelHandlerContext, clientResponse: ClientResponse): Unit = {
    zExec.unsafeRun(ctx)(promise.succeed(clientResponse))
  }

  override def exceptionCaught(ctx: ChannelHandlerContext, error: Throwable): Unit = {
    super.exceptionCaught(ctx, error)
    releaseRequest()
  }

  override def channelActive(ctx: ChannelHandlerContext): Unit = {
    ctx.writeAndFlush(jReq): Unit
    releaseRequest()
  }

  private def releaseRequest(): Unit = {
    if (jReq.refCnt() > 0) {
      jReq.release(jReq.refCnt()): Unit
    }
  }
}
