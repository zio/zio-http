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
  jReq: FullHttpRequest,
  connectionManager: ZConnectionManager,
) extends SimpleChannelInboundHandler[Resp](false) {

  override def channelRead0(ctx: ChannelHandlerContext, clientResponse: Resp): Unit = {
    println(s"SimpleChannelInboundHandler SimpleChannelInboundHandler CHANNEL READ CONTEX ID: ${ctx.channel().id()}")
    zExec.unsafeRun(ctx) {
      println(s"GETTING CLIENT RESPONSE: $clientResponse")
      val prom = connectionManager.currentExecRef(ctx.channel())
      println(s"FOUND PROMISE: $prom")
      prom.succeed(clientResponse)
    }
  }

  override def exceptionCaught(ctx: ChannelHandlerContext, error: Throwable): Unit = {
//    println(s"ZClientInboundHandler ERROR response: $error")
    zExec.unsafeRun(ctx)(zio.Task.fail(error))
    releaseRequest()
  }

  override def channelActive(ctx: ChannelHandlerContext): Unit = {
    println(s"CHANNEL ACTIVE CONTEXT ID: ${ctx.channel().id()}")
//    println(s" FIRING REQUEST : ${reqRef(ctx.channel())}")
//    ctx.writeAndFlush(jReq): Unit
    ctx.flush()
    releaseRequest()
  }

  private def releaseRequest(): Unit = {
    if (jReq.refCnt() > 0) {
      jReq.release(jReq.refCnt()): Unit
    }
  }

}
