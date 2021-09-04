package zhttp.service.client

import io.netty.channel.{ChannelHandlerContext, ChannelPromise, SimpleChannelInboundHandler}
import io.netty.handler.codec.http.HttpResponse

final case class MultiplexClearTextFallback(sp:ChannelPromise)
  extends SimpleChannelInboundHandler[HttpResponse]() {
  @throws[Exception]
  override protected def channelRead0(ctx: ChannelHandlerContext, msg: HttpResponse): Unit = { // If this handler is hit then no upgrade has been attempted and the client is just talking HTTP.

    println("Fallback! the attempt for http2 failed. now trying to resolve the promise by the response from the server for the upgrade request")
    println("handlers before fallback")
    println(ctx.pipeline().names())
    sp.setFailure( new RuntimeException ("Server doesn't support http2"))
    ()
  }
}