package zhttp.service.client.handler

import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.{ChannelHandlerContext, SimpleChannelInboundHandler}
import io.netty.handler.codec.http.FullHttpResponse
//import io.netty.util.ReferenceCountUtil
//import io.netty.handler.codec.http.FullHttpRequest
//import io.netty.handler.codec.http.FullHttpRequest
import zhttp.service.Client.ClientResponse
import zhttp.service.HttpRuntime
import zhttp.service.client.transport.ClientConnectionManager

/**
 * Handles HTTP response
 */
@Sharable
final case class NewClientInboundHandler[R](
  zExec: HttpRuntime[R],
  connectionManager: ClientConnectionManager
) extends SimpleChannelInboundHandler[FullHttpResponse](false) {

  override def channelRead0(ctx: ChannelHandlerContext, msg: FullHttpResponse): Unit = {
    val r = for {
      currAlloc <- connectionManager.connectionState.currentAllocatedChannels.get
      currentRuntime = currAlloc(ctx.channel())
      reqKey = currentRuntime.reqKey
      _ <- connectionManager.connectionState.currentAllocatedChannels.update(m => m - ctx.channel())
      _ <- currentRuntime.callback.succeed(ClientResponse.unsafeFromJResponse(msg))
      mp <- connectionManager.connectionState.idleConnectionsMap.get
      q = mp(reqKey)
      _ <- zio.Task(q.enqueue(ctx.channel()))
      _ <- zio.Task(println(s"AFTER ENQUEUEING CHANNEL: $q"))
    } yield ()
    zExec.unsafeRun(ctx){r}
  }

  override def exceptionCaught(ctx: ChannelHandlerContext, error: Throwable): Unit = {
    val r = for{
      currAlloc <- connectionManager.connectionState.currentAllocatedChannels.get
      currentRuntime = currAlloc(ctx.channel())
//      _ <- zio.ZIO.effect {
//        println(s"CURR ")
//        if (currentRuntime.currReq.refCnt() > 0) ReferenceCountUtil.release(currentRuntime.currReq)
//      }
//      reqKey <- connectionManager.getRequestKey(currentRuntime.currReq)
//      _ <- zio.Task.fail(error)
//      _ <- zio.ZIO.effect(releaseRequest(currentRuntime.currReq))
    } yield ()
    zExec.unsafeRun(ctx)(r)
//    releaseRequest(connectionRuntime.currReq): Unit
  }

  override def channelActive(ctx: ChannelHandlerContext): Unit = {
    val r = for{
      currAlloc <- connectionManager.connectionState.currentAllocatedChannels.get
      currentRuntime = currAlloc(ctx.channel())
      //      reqKey <- connectionManager.getRequestKey(currentRuntime.currReq)
      _ <- zio.ZIO.effect {
        ctx.writeAndFlush(currentRuntime.currReq)
//        releaseRequest(currentRuntime.currReq)
      }
    } yield ()
//    zio.Runtime.default.unsafeRun(r)
    zExec.unsafeRun(ctx)(r)
//    releaseRequest(jReq): Unit
  }

//  private def releaseRequest(jReq: io.netty.handler.codec.http.FullHttpRequest): Unit = {
//    if (jReq.refCnt() > 0) {
//      jReq.release(jReq.refCnt()): Unit
//    }
//  }

}
