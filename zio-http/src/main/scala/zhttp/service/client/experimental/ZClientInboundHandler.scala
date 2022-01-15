package zhttp.service.client.experimental

import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.{ChannelHandlerContext, SimpleChannelInboundHandler}
import io.netty.handler.codec.http.FullHttpRequest
//import io.netty.handler.timeout.{IdleState, IdleStateEvent}
import zhttp.service.HttpRuntime
import zio.Promise

/**
 * Handles HTTP response
 */
@Sharable
final case class ZClientInboundHandler[R](
                         zExec: HttpRuntime[R],
                         jReq: FullHttpRequest,
                         promise: Promise[Throwable, Resp],
                       ) extends SimpleChannelInboundHandler[Resp](false) {

  override def channelRead0(ctx: ChannelHandlerContext, clientResponse: Resp): Unit = {
    println(s"CHANNEL READ CONTEX ID: ${ctx.channel().id()}")
//    println(s"ZClientInboundHandler : CTX: $ctx === channelRead : $clientResponse")
    zExec.unsafeRun(ctx)(promise.succeed(clientResponse))
  }

  override def exceptionCaught(ctx: ChannelHandlerContext, error: Throwable): Unit = {
//    println(s"ZClientInboundHandler ERROR response: $error")
    zExec.unsafeRun(ctx)(promise.fail(error))
    releaseRequest()
  }

  override def channelActive(ctx: ChannelHandlerContext): Unit = {
    println(s"CHANNEL ACTIVE CONTEX ID: ${ctx.channel().id()}")
//    println(s"ZClientInboundHandler channelActive CTXXXX $ctx")
    ctx.writeAndFlush(jReq): Unit
    releaseRequest()
  }

  private def releaseRequest(): Unit = {
    if (jReq.refCnt() > 0) {
      jReq.release(jReq.refCnt()): Unit
    }
  }

//  override def userEventTriggered(ctx: ChannelHandlerContext, evt: Any): Unit = {
//    println(s"USER EVENT TRIGERED ---- CLIENT INBOUND HANDLER")
//    if (evt.isInstanceOf[IdleStateEvent]) {
//      val e = evt.asInstanceOf[IdleStateEvent]
//      if (e.state() == IdleState.READER_IDLE) {
//        ctx.close(): Unit
//      } else if (e.state() == IdleState.WRITER_IDLE) {
//        ctx.writeAndFlush("new PingMessage()"): Unit
//      }
//    }
//  }

}
