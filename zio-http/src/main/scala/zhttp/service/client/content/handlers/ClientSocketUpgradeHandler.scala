package zhttp.service.client.content.handlers

import io.netty.buffer.Unpooled
import io.netty.channel.{ChannelHandlerContext, SimpleChannelInboundHandler}
import io.netty.handler.codec.http.FullHttpResponse
import zhttp.http.{Headers, Status}
import zhttp.service.Client.ClientResponse
import zhttp.service.HttpRuntime
import zio.Promise

final case class ClientSocketUpgradeHandler[R](
  zExec: HttpRuntime[R],
  pr: Promise[Throwable, ClientResponse],
) extends SimpleChannelInboundHandler[FullHttpResponse] {
  override def channelRead0(ctx: ChannelHandlerContext, msg: FullHttpResponse): Unit = {
    msg.touch()
    val response = ClientResponse(
      Status.fromHttpResponseStatus(msg.status()),
      Headers.decode(msg.headers()),
      Unpooled.copiedBuffer(msg.content()),
    )

    zExec.unsafeRun(ctx)(pr.succeed(response))
    ctx.fireChannelRead(msg.retain())
    ctx.pipeline().remove(ctx.name())
    ()
  }

  override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable): Unit = {
    zExec.unsafeRun(ctx)(pr.fail(cause))
  }
}
