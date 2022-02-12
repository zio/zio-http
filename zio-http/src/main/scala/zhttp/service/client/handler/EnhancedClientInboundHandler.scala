package zhttp.service.client.handler

//import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.{ChannelHandlerContext, SimpleChannelInboundHandler}
import io.netty.handler.codec.http.{FullHttpRequest, FullHttpResponse}
import zio.Promise
import zhttp.service.Client.ClientResponse
import zhttp.service.HttpRuntime
//import zhttp.service.client.transport.ClientConnectionManager

/**
 * Handles HTTP response
 */
//@Sharable
final case class EnhancedClientInboundHandler[R](
  zExec: HttpRuntime[R],
//  connectionManager: ClientConnectionManager,
  jReq: FullHttpRequest,
  promise: Promise[Throwable, ClientResponse],
) extends SimpleChannelInboundHandler[FullHttpResponse](true) {

  override def channelRead0(ctx: ChannelHandlerContext, msg: FullHttpResponse): Unit = {
    val headers = msg.headers()
    println(s"CHANNEL READ: ${ctx.channel()} == \n\n HEADERS FROM MSG: $headers ###  ${headers.get("content-length")} ### ${headers.get("Location")}")
    msg.touch("handlers.ClientInboundHandler-channelRead0")
    zExec.unsafeRun(ctx)(promise.succeed(ClientResponse.unsafeFromJResponse(msg)))
    ctx.pipeline().remove(ctx.name())
    ()
  }

//  override def userEventTriggered(ctx: ChannelHandlerContext, evt: Any): Unit = {
//    println(s"USER EVENT: ${ctx.channel()} === ${evt}")
//    ctx.writeAndFlush(evt.asInstanceOf[FullHttpRequest].retain())
////    ctx.fireChannelActive()
//    releaseRequest()
//    ()
//  }

  override def channelActive(ctx: ChannelHandlerContext): Unit = {
    println(s"CHANNEL ACTIVE: ${ctx.channel()} === ${jReq.headers()}")
    ctx.writeAndFlush(jReq.retain())
    releaseRequest()
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
}
