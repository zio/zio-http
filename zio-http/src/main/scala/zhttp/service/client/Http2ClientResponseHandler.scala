package zhttp.service.client

import io.netty.channel.{ChannelHandlerContext, SimpleChannelInboundHandler}
import io.netty.handler.codec.http._
import io.netty.handler.codec.http2.HttpConversionUtil
import zhttp.service.UnsafeChannelExecutor
import zio.Promise

import scala.collection.mutable.Map

/**
 * Process {@link FullHttpResponse} translated from HTTP/2 frames
 */
case class Http2ClientResponseHandler(zExec: UnsafeChannelExecutor[Any])
    extends SimpleChannelInboundHandler[FullHttpResponse] { // Use a concurrent map because we add and iterate from the main thread (just for the purposes of the example),
  // but Netty also does a get on the map when messages are received in a EventLoop thread.
  val streamIdMap: Map[Int, FP] = Map.empty[Int, FP]
  class FP(promise: Promise[Throwable, FullHttpResponse]) {
    def getPromise = promise
  }

  def put(streamId: Int, promise: Promise[Throwable, FullHttpResponse]) =
    streamIdMap.put(streamId, new FP(promise))

  @throws[Exception]
  override protected def channelRead0(ctx: ChannelHandlerContext, msg: FullHttpResponse): Unit = {
    val streamId = msg.headers.getInt(HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text)
    if (streamId == null) {
      System.err.println("Http2ClientResponseHandler unexpected message received: " + msg)
      return
    }
    val fp       = streamIdMap.get(streamId)
    if (fp == null) System.err.println("Message received for unknown stream id " + streamId)
    else {
      zExec.unsafeExecute_(ctx)(fp.get.getPromise.succeed(msg.retain()).unit)
    }
    ()
  }


}
