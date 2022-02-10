package zhttp.service.client.handler

import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.{ChannelHandlerContext, SimpleChannelInboundHandler}
import io.netty.handler.codec.http.{FullHttpRequest, FullHttpResponse}
import zio.{Promise}
import zhttp.service.Client.ClientResponse
import zhttp.service.HttpRuntime
import zhttp.service.client.transport.ClientConnectionManager

/**
 * Handles HTTP response
 */
@Sharable
final case class EnhancedClientInboundHandler[R](
  zExec: HttpRuntime[R],
  connectionManager: ClientConnectionManager,
  jReq: FullHttpRequest,
  promise: Promise[Throwable, ClientResponse],
) extends SimpleChannelInboundHandler[FullHttpResponse](true) {

  override def channelRead0(ctx: ChannelHandlerContext, msg: FullHttpResponse): Unit = {
//    println(s"CHANNEL READ: $msg")
    msg.touch("handlers.ClientInboundHandler-channelRead0")

    zExec.unsafeRun(ctx)(promise.succeed(ClientResponse.unsafeFromJResponse(msg)))
  }

  override def channelActive(ctx: ChannelHandlerContext): Unit = {
    println(s"CHANNEL ACTIVE: ${ctx.channel()} === ")
    ctx.writeAndFlush(jReq)
    releaseRequest()
  }

  override def exceptionCaught(ctx: ChannelHandlerContext, error: Throwable): Unit = {
    println(s"EXCEPTION: $error")
    zExec.unsafeRun(ctx)(promise.fail(error))
    releaseRequest()
  }

  private def releaseRequest(): Unit = {
    if (jReq.refCnt() > 0) {
      jReq.release(jReq.refCnt()): Unit
    }
  }
}
