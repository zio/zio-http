package zhttp.service.client.handler

import io.netty.channel.{ChannelHandlerContext, SimpleChannelInboundHandler}
import io.netty.handler.codec.http.{FullHttpRequest, FullHttpResponse}
import zhttp.service.Client.ClientResponse
import zhttp.service.HttpRuntime
import zio.Promise

/**
 * Handles HTTP response
 */
//@Sharable
final case class EnhancedClientInboundHandler[R](
  zExec: HttpRuntime[R],
  jReq: FullHttpRequest,
  promise: Promise[Throwable, ClientResponse],
) extends SimpleChannelInboundHandler[FullHttpResponse](true) {

  override def channelRead0(ctx: ChannelHandlerContext, msg: FullHttpResponse): Unit = {
    zExec.unsafeRun(ctx)(promise.succeed(ClientResponse.unsafeFromJResponse(msg)))
  }

  override def channelActive(ctx: ChannelHandlerContext): Unit = {
    ctx.writeAndFlush(jReq)
    releaseRequest(): Unit
  }

  override def exceptionCaught(ctx: ChannelHandlerContext, error: Throwable): Unit = {
//    println(s"EXCEPTION: $error")
    zExec.unsafeRun(ctx)(promise.fail(error))
    releaseRequest()
  }

  private def releaseRequest(): Unit = {
    if (jReq.refCnt() > 0) {
      jReq.release(jReq.refCnt()): Unit
    }
  }

  override def handlerAdded(ctx: ChannelHandlerContext): Unit = {
    if (ctx.channel().isActive) {
      ctx.writeAndFlush(jReq)
    } else ctx.fireChannelActive()
    ()
  }
}
