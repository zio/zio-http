package zhttp.service.client

import io.netty.channel.{ChannelHandlerContext, SimpleChannelInboundHandler}
import io.netty.handler.codec.http.websocketx.WebSocketClientProtocolHandler.ClientHandshakeStateEvent
import io.netty.handler.codec.http.websocketx.{WebSocketFrame => JWebSocketFrame}
import zhttp.service.{ChannelFuture, HttpRuntime}
import zhttp.socket.SocketApp.Handle
import zhttp.socket.{SocketApp, WebSocketFrame}
import zio.Queue

// TODO Remove duplication carried from ServerSocketHandler
case class ClientSocketHandler[R](zExec: HttpRuntime[R], ss: SocketApp[R], queue: Queue[WebSocketFrame])
    extends SimpleChannelInboundHandler[JWebSocketFrame] {

  override def channelUnregistered(ctx: ChannelHandlerContext): Unit = {
    ss.close match {
      case Some(v) => zExec.unsafeRun(ctx)((v(ctx.channel().remoteAddress()) *> queue.shutdown).uninterruptible)
      case None    => ctx.fireChannelUnregistered()
    }
    ()
  }

  override def userEventTriggered(ctx: ChannelHandlerContext, event: AnyRef): Unit = {
    import ClientHandshakeStateEvent._

    event.asInstanceOf[ClientHandshakeStateEvent] match {
      case HANDSHAKE_COMPLETE =>
        ss.open match {
          case Some(v) =>
            v match {
              case Handle.WithEffect(f) => zExec.unsafeRun(ctx)(f(ctx.channel().remoteAddress()))
              case Handle.WithSocket(s) =>
                zExec.unsafeRun(ctx)(
                  s(ctx.channel().remoteAddress())
                    .mapM(frame => ChannelFuture.unit(ctx.writeAndFlush(frame.toWebSocketFrame)))
                    .runDrain,
                )
            }
          case None    =>
            ctx.fireUserEventTriggered(event): Unit
        }
      case _                  => ()
    }
  }

  override def channelRead0(ctx: ChannelHandlerContext, msg: JWebSocketFrame): Unit = {
    ss.message match {
      case Some(v) =>
        WebSocketFrame.fromJFrame(msg) match {
          case Some(frame) => {
            zExec
              .unsafeRun(ctx)(
                queue.offer(frame) *>
                  v(frame)
                    .mapM(frame => ChannelFuture.unit(ctx.writeAndFlush(frame.toWebSocketFrame)))
                    .runDrain,
              )

          }
          case None        => ()
        }
      case None    => ()
    }
  }
}
