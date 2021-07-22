package zhttp.service.client


import io.netty.channel.{ChannelHandlerContext, ChannelInboundHandlerAdapter, ChannelInitializer}
import io.netty.channel.socket.SocketChannel
import io.netty.handler.codec.http._
import io.netty.handler.codec.http2._
import zhttp.core.JChannelHandler
import zhttp.service.client.ClientSSLHandler.ClientSSLOptions




class Http2ClientInitializer(sslOption: ClientSSLOptions,rh: JChannelHandler,scheme:String,maxContentLength: Int= Int.MaxValue) extends ChannelInitializer[SocketChannel] {


  @throws[Exception]
  override def initChannel(ch: SocketChannel): Unit = {
    val connection = new DefaultHttp2Connection(false)
    val connectionHandler: HttpToHttp2ConnectionHandler = new HttpToHttp2ConnectionHandlerBuilder().frameListener(new DelegatingDecompressorFrameListener(connection, new InboundHttp2ToHttpAdapterBuilder(connection).maxContentLength(maxContentLength).propagateSettings(true).build)).build()
    val settingsHandler =  Http2SettingsHandler(ch.newPromise)
    val pipeline=ch.pipeline()
    if (scheme=="https"){
      pipeline.addLast(ClientSSLHandler.ssl(sslOption,true).newHandler(ch.alloc()),connectionHandler,settingsHandler,rh)
      ()
    } else {
      val sourceCodec = new HttpClientCodec()
      val upgradeCodec = new Http2ClientUpgradeCodec(connectionHandler)
      val upgradeHandler = new HttpClientUpgradeHandler(sourceCodec,upgradeCodec,Int.MaxValue)
      pipeline.addLast(sourceCodec,upgradeHandler,new UpgradeRequestHandler(settingsHandler,rh))
      ()
    }
  }



  /**
   * A handler that triggers the cleartext upgrade to HTTP/2 by sending an initial HTTP request.
   */
  final private class UpgradeRequestHandler(sh: JChannelHandler,rh: JChannelHandler) extends ChannelInboundHandlerAdapter {
    @throws[Exception]
    override def channelActive(ctx: ChannelHandlerContext): Unit = {
      val upgradeRequest = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/")
      ctx.writeAndFlush(upgradeRequest)
      ctx.fireChannelActive
      // Done with this handler, remove it from the pipeline.
      ctx.pipeline.remove(this)
      ctx.pipeline().addLast(sh,rh)
      ()
    }
  }

}