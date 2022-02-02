package zhttp.service.client.experimental.handler

import io.netty.channel.{ChannelDuplexHandler, ChannelHandlerContext}
import io.netty.handler.timeout.{IdleState, IdleStateEvent}

class ZIdleStateAwareHandler extends ChannelDuplexHandler {

  override def userEventTriggered(ctx: ChannelHandlerContext, evt: Any): Unit = {
    println(s"USER EVENT TRIGERED  ---- IDLE STATE AWARE !!!!! ctx ID: ${ctx.channel().id()}")
    if (evt.isInstanceOf[IdleStateEvent]) {
      val e = evt.asInstanceOf[IdleStateEvent]
      println(s"e: ${e.state()}")
      if (e.state() == IdleState.READER_IDLE) {
        println(s"MY IDLE STATE AWARE ;;; READER IDLE")
        ctx.close(): Unit
      } else if (e.state() == IdleState.WRITER_IDLE) {
        println(s"MY IDLE STATE AWARE ;;; WRITER  IDLE")
//        ctx.writeAndFlush("new PingMessage()"): Unit
      } else if (e.state() == IdleState.ALL_IDLE) {
        println(s"MY IDLE STATE AWARE ;;; ${ctx.channel().id()} ALL  IDLE")
        //        ctx.writeAndFlush("new PingMessage()"): Unit
        //        ctx.close(): Unit
      }
    }
  }
}
