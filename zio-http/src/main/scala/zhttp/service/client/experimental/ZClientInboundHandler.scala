package zhttp.service.client.experimental

import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.{ChannelHandlerContext, SimpleChannelInboundHandler}
import io.netty.handler.codec.http.FullHttpRequest
import zhttp.service.HttpRuntime

//import scala.collection.mutable

/**
 * Handles HTTP response
 */
@Sharable
final case class ZClientInboundHandler[R](
  zExec: HttpRuntime[R],
  connectionManager: ZConnectionManager,
) extends SimpleChannelInboundHandler[Resp](false) {

  override def channelRead0(ctx: ChannelHandlerContext, clientResponse: Resp): Unit = {
    println(s"SimpleChannelInboundHandler SimpleChannelInboundHandler CHANNEL READ CONTEX ID: ${ctx.channel().id()}")
    val prom = connectionManager.currentExecRef(ctx.channel())
    zExec.unsafeRun(ctx) { prom._1.succeed(clientResponse) }
  }

  override def exceptionCaught(ctx: ChannelHandlerContext, error: Throwable): Unit = {
    val prom = connectionManager.currentExecRef(ctx.channel())
    zExec.unsafeRun(ctx)(zio.Task.fail(error))
    releaseRequest(prom._2)
  }

  override def channelActive(ctx: ChannelHandlerContext): Unit = {
    println(s"CHANNEL ACTIVE CONTEXT ID: ${ctx.channel().id()}")
    val prom = connectionManager.currentExecRef(ctx.channel())
    val jReq = prom._2
    ctx.writeAndFlush(jReq): Unit
    releaseRequest(jReq)
    ()
  }

  private def releaseRequest(jReq: FullHttpRequest): Unit = {
    if (jReq.refCnt() > 0) {
      jReq.release(jReq.refCnt()): Unit
    }
  }

}
