package zhttp.service.client


import java.util

import io.netty.channel.{ChannelFuture, ChannelHandlerContext, SimpleChannelInboundHandler}
import io.netty.handler.codec.http.FullHttpResponse
import io.netty.handler.codec.http2.HttpConversionUtil
import zhttp.core.{JFullHttpResponse, JSharable}
import zio.Promise

import scala.collection.mutable.Map


/**
 * Process {@link io.netty.handler.codec.http.FullHttpResponse} translated from HTTP/2 frames
 */
@JSharable
final case class Http2ResponseHandler() extends SimpleChannelInboundHandler[FullHttpResponse] {
  val streamidPromiseMap :Map[Integer, util.Map.Entry[ChannelFuture, Promise[Throwable, JFullHttpResponse]]]= Map.empty[Integer, util.Map.Entry[ChannelFuture, Promise[Throwable, JFullHttpResponse]]]


  /**
   * Create an association between an anticipated response stream id and a {@link io.netty.channel.ChannelPromise}
   *
   * @param streamId    The stream for which a response is expected
   * @param writeFuture A future that represent the request write operation
   * @param promise     The promise object that will be used to wait/notify events
   * @return The previous object associated with {@code streamId}
   * @see HttpResponseHandler#awaitResponses(long, java.util.concurrent.TimeUnit)
   */
  def put(streamId: Int, writeFuture: ChannelFuture, promise: Promise[Throwable, JFullHttpResponse]): Option[util.Map.Entry[ChannelFuture, Promise[Throwable, JFullHttpResponse]]] = streamidPromiseMap.put(streamId, new util.AbstractMap.SimpleEntry[ChannelFuture, Promise[Throwable, JFullHttpResponse]](writeFuture, promise))

//  /**
//   * Wait (sequentially) for a time duration for each anticipated response
//   *
//   * @param timeout Value of time to wait for each response
//   * @param unit    Units associated with {@code timeout}
//   * @see HttpResponseHandler#put(int, io.netty.channel.ChannelPromise)
//   */
//  def awaitResponses(timeout: Long, unit: TimeUnit): Unit = {
//    val itr = streamidPromiseMap.iterator
//    while ( {
//      itr.hasNext
//    }) {
//      val entry = itr.next
//      val writeFuture = entry._2.getKey
//      if (!writeFuture.awaitUninterruptibly(timeout, unit)) throw new IllegalStateException("Timed out waiting to write for stream id " + entry._1)
//      if (!writeFuture.isSuccess) throw new RuntimeException(writeFuture.cause)
//      val promise = entry._2.getValue
//      if (!promise.awaitUninterruptibly(timeout, unit)) throw new IllegalStateException("Timed out waiting for response on stream id " + entry._1)
//      if (!promise.isSuccess) throw new RuntimeException(promise.cause)
//      System.out.println("---Stream id: " + entry._1 + " received---")
//     streamidPromiseMap.remove(entry._1)
//    }
//  }

  @throws[Exception]
  override protected def channelRead0(ctx: ChannelHandlerContext, msg: FullHttpResponse): Unit = {
    val streamId = msg.headers.getInt(HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text)
    if (streamId == null) {
      System.err.println("HttpResponseHandler unexpected message received: " + msg)
      return
    }
    val entry = streamidPromiseMap.get(streamId)
    if (entry == null) System.err.println("Message received for unknown stream id " + streamId)
    else {
      entry.get.getValue.succeed(msg)
      streamidPromiseMap.remove(streamId)
      ()
    }
  }

  override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable): Unit = streamidPromiseMap.foreach(p=>{p._2.getValue.fail(cause)
  streamidPromiseMap.remove(p._1)} )
}