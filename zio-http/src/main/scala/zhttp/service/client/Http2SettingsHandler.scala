package example.client

import io.netty.channel.{ChannelHandlerContext, ChannelPromise, SimpleChannelInboundHandler}
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http2.Http2Settings

import java.util.concurrent.TimeUnit

/**
 * Reads the first {@link Http2Settings} object and notifies a {@link ChannelPromise}
 */
case class Http2SettingsHandler(promise: ChannelPromise, jReq: FullHttpRequest, scheme: String)
    extends SimpleChannelInboundHandler[Http2Settings] {

  /**
   * Wait for this handler to be added after the upgrade to HTTP/2, and for initial preface handshake to complete.
   * Exception if timeout or other failure occurs
   */
  @throws[Exception]
  def awaitSettings(timeout: Long, unit: TimeUnit): Unit = {
    if (!promise.awaitUninterruptibly(timeout, unit)) throw new IllegalStateException("Timed out waiting for settings")
    if (!promise.isSuccess) throw new RuntimeException(promise.cause)
  }

  @throws[Exception]
  override protected def channelRead0(ctx: ChannelHandlerContext, msg: Http2Settings): Unit = {
    promise.setSuccess
    if (scheme == "https") {
      //incase of http the first request will be send from the upgrade handler
      ctx.channel().writeAndFlush(jReq)
    } else
      ()
    ctx.pipeline.remove(this)
    ()
  }

}
