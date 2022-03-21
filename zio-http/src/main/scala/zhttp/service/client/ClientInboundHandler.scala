package zhttp.service.client

import io.netty.channel.{ChannelHandlerContext, SimpleChannelInboundHandler}
import io.netty.handler.codec.http.{FullHttpRequest, FullHttpResponse}
<<<<<<< HEAD
import zhttp.logging.Logger
import zhttp.service.Client.ClientResponse
=======
import zhttp.http.Response
>>>>>>> 2f470811cbd0c9601e16763b61d8de29a7a89527
import zhttp.service.HttpRuntime
import zio.Promise

/**
 * Handles HTTP response
 */
final class ClientInboundHandler[R](
  zExec: HttpRuntime[R],
  jReq: FullHttpRequest,
  promise: Promise[Throwable, Response],
  isWebSocket: Boolean,
) extends SimpleChannelInboundHandler[FullHttpResponse](true) {

  private val log = Logger.getLogger("zhttp.service.client.ClientInboundHandler")

  override def channelActive(ctx: ChannelHandlerContext): Unit = {
    if (isWebSocket) {
      ctx.fireChannelActive(): Unit
    } else {
      ctx.writeAndFlush(jReq)
      ()
    }
  }

  override def channelRead0(ctx: ChannelHandlerContext, msg: FullHttpResponse): Unit = {
    msg.touch("handlers.ClientInboundHandler-channelRead0")

    log.trace(s"Received message: $msg")
    zExec.unsafeRun(ctx)(promise.succeed(Response.unsafeFromJResponse(msg)))
    if (isWebSocket) {
      ctx.fireChannelRead(msg.retain())
      ctx.pipeline().remove(ctx.name()): Unit
    }
  }

  override def exceptionCaught(ctx: ChannelHandlerContext, error: Throwable): Unit = {
    log.error(s"Exception caught.", error)

    zExec.unsafeRun(ctx)(promise.fail(error))
    releaseRequest()
  }

  private def releaseRequest(): Unit = {
    log.debug(s"Reference count: ${jReq.refCnt()}")
    if (jReq.refCnt() > 0) {
      jReq.release(jReq.refCnt()): Unit
    }
  }
}
