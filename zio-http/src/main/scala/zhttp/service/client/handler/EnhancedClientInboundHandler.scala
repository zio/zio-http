package zhttp.service.client.handler

import io.netty.channel.{ChannelHandlerContext, SimpleChannelInboundHandler}
import io.netty.handler.codec.http.{FullHttpRequest, FullHttpResponse}
import zhttp.service.Client.ClientResponse
import zhttp.service.HttpRuntime
import zhttp.service.client.model.ClientConnectionState.ReqKey
import zhttp.service.client.model.Connection
import zhttp.service.client.transport.ClientConnectionManager
import zio.Promise

/**
 * Handles HTTP response
 */
//@Sharable
final case class EnhancedClientInboundHandler[R](
  zExec: HttpRuntime[R],
  jReq: FullHttpRequest,
  promise: Promise[Throwable, ClientResponse],
  connectionManager: ClientConnectionManager,
  reqKey: ReqKey,
  connection: Connection,
) extends SimpleChannelInboundHandler[FullHttpResponse](true) {

  override def channelRead0(ctx: ChannelHandlerContext, msg: FullHttpResponse): Unit = {
//    println(s"CHANNEL READ: ${ctx.channel().id()} ")
    zExec.unsafeRun(ctx)(promise.succeed(ClientResponse.unsafeFromJResponse(msg)))
    ()
  }

  override def channelActive(ctx: ChannelHandlerContext): Unit = {
//    println(s"CHANNEL ACTIVE: ${ctx.channel().id()} ")
    ctx.writeAndFlush(jReq)
    releaseRequest()
    ()
//    releaseRequest()
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

  override def handlerAdded(ctx: ChannelHandlerContext): Unit = {
//    println(s"ECI ADDED ${ctx.channel().id()} ${ctx.name()} ${ctx.channel().isActive}")
    if (ctx.channel().isActive) {
      ctx.writeAndFlush(jReq)
    }
    else ctx.fireChannelActive()
    ()
  }

  override def handlerRemoved(ctx: ChannelHandlerContext): Unit = {
//    println(s"ECI REMOVED ${ctx.channel().id()} ${ctx.name()} ${ctx.channel().isActive}")
  }
}
