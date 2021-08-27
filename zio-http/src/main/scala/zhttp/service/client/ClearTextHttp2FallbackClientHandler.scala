package zhttp.service.client

import io.netty.channel.{ChannelHandler, ChannelHandlerContext, SimpleChannelInboundHandler}
import io.netty.handler.codec.http.{HttpClientCodec, HttpObjectAggregator, HttpResponse}

final case class ClearTextHttp2FallbackClientHandler(httpH: ChannelHandler)
  extends SimpleChannelInboundHandler[HttpResponse]() {
  @throws[Exception]
  override protected def channelRead0(ctx: ChannelHandlerContext, msg: HttpResponse): Unit = { // If this handler is hit then no upgrade has been attempted and the client is just talking HTTP.
    val pipeline = ctx.pipeline
    println(msg)
    println("4")
    println(pipeline.names())

    pipeline.removeFirst()
    pipeline.remove(this)
    pipeline.removeLast()
    pipeline.removeLast()
    pipeline.addLast(new HttpClientCodec)
      .addLast(new HttpObjectAggregator(Int.MaxValue)).addLast(httpH)

    println("5")
    println(pipeline.names())
    ctx.fireChannelRead(msg)
    ()
  }
}

