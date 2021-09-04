package example.client

import io.netty.channel.{ChannelHandlerContext, ChannelPromise, SimpleChannelInboundHandler}
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http2.Http2Settings

import java.util.concurrent.TimeUnit

/**
 * Reads the first {@link Http2Settings} object and notifies a {@link ChannelPromise}
 */
class Http2SettingsHandler(val promise: ChannelPromise, jReq: Option[FullHttpRequest],scheme :String)
    extends SimpleChannelInboundHandler[Http2Settings] {

  /**
   * Wait for this handler to be added after the upgrade to HTTP/2, and for initial preface handshake to complete.
   *
   * @param timeout
   *   Time to wait
   * @param unit
   *   {@link TimeUnit} for {@code timeout}
   * @throws
   *   Exception if timeout or other failure occurs
   */
  @throws[Exception]
  def awaitSettings(timeout: Long, unit: TimeUnit): Unit = {
    if (!promise.awaitUninterruptibly(timeout, unit)) throw new IllegalStateException("Timed out waiting for settings")
    if (!promise.isSuccess) throw new RuntimeException(promise.cause)
  }

  @throws[Exception]
  override protected def channelRead0(ctx: ChannelHandlerContext, msg: Http2Settings): Unit = {
    promise.setSuccess
    // Only care about the first settings message
    if (scheme=="https"){
      println("sending request from settings handler")
      jReq match {
        case Some(value) => ctx.channel().writeAndFlush(value)
        case None => println("none")
      }
    } else
      ()
    ctx.pipeline.remove(this)
    ()
  }

  def multiplexpromise(timeout: Long = 5, unit: TimeUnit = TimeUnit.SECONDS):Boolean = {
    if (!promise.awaitUninterruptibly(timeout, unit)) return (false)
    if (!promise.isSuccess) false
    else true
  }

}
