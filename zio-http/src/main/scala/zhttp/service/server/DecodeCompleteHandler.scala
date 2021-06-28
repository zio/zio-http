package zhttp.service.server

import io.netty.buffer.{ByteBufUtil, Unpooled => JUnpooled}
import io.netty.channel.{ChannelHandlerContext, SimpleChannelInboundHandler => JSimpleChannelInboundHandler}
import io.netty.handler.codec.http.{DefaultHttpContent => JDefaultHttpContent, LastHttpContent => JLastHttpContent}
import zhttp.core.{JChannelHandlerContext, JHttpRequest}
import zhttp.http._
import zhttp.service.Server.Settings
import zhttp.service.{ChannelFuture, EncodeResponse, UnsafeChannelExecutor}
import zio.Chunk

private[zhttp] final case class DecodeCompleteHandler[R](
  zExec: UnsafeChannelExecutor[R],
  settings: Settings[R, Throwable],
  jReq: JHttpRequest,
  cb: Chunk[Byte] => Response[R, Throwable, Any],
) extends JSimpleChannelInboundHandler[JDefaultHttpContent](false)
    with EncodeResponse
    with ExecuterHelper[R] {

  override def handlerAdded(ctx: ChannelHandlerContext): Unit = {
    ctx.channel.config.setAutoRead(true)
    ()
  }
  private def writeContent(ctx: JChannelHandlerContext, content: Content[R, Throwable, Any]) = content match {
    case Content.BufferedContent(data) =>
      zExec.unsafeExecute_(ctx) {
        for {
          _ <- data.foreachChunk(c =>
            ChannelFuture.unit(
              ctx.writeAndFlush(JUnpooled.wrappedBuffer(c.toArray)),
            ),
          )
          _ <- ChannelFuture.unit(ctx.writeAndFlush(JLastHttpContent.EMPTY_LAST_CONTENT))
        } yield ()
      }
    case Content.CompleteContent(data) =>
      ctx.write(JUnpooled.copiedBuffer(data.toArray), ctx.channel().voidPromise())
      ctx.writeAndFlush(JLastHttpContent.EMPTY_LAST_CONTENT)
    case Content.EmptyContent          => ctx.writeAndFlush(JLastHttpContent.EMPTY_LAST_CONTENT)
  }
  override def channelRead0(ctx: ChannelHandlerContext, msg: JDefaultHttpContent): Unit = {
    def asyncWriteResponse(res: Response[R, Throwable, Any]) = res match {
      case res @ Response.Default(_, _, content) =>
        ctx.write(
          encodeResponse(jReq.protocolVersion(), res),
          ctx.channel().voidPromise(),
        )
        writeContent(ctx, content)
        ()
      case _                                     => ()
    }
    executeAsync(zExec, settings, ctx, decodeJRequest(jReq))(_ =>
      asyncWriteResponse(cb(Chunk.fromArray(ByteBufUtil.getBytes(msg.content())))),
    )
  }

  override def exceptionCaught(ctx: JChannelHandlerContext, cause: Throwable): Unit = {
    super.exceptionCaught(ctx, cause)
  }
}
