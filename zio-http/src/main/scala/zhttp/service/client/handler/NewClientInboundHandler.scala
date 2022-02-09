package zhttp.service.client.handler

import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.{ChannelHandlerContext, SimpleChannelInboundHandler}
import io.netty.handler.codec.http.FullHttpRequest
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
) extends SimpleChannelInboundHandler[ClientResponse](false) {

  override def channelRead0(ctx: ChannelHandlerContext, clientResponse: ClientResponse): Unit = {
    println(s"triggering channel read ........... ")
    val r = for {
      currAlloc <- connectionManager.connectionState.currentAllocatedChannels.get
      currentRuntime = currAlloc(ctx.channel())
      reqKey = currentRuntime.reqKey
      _ <- zio.Task(println(s"BEFORE ENQUEUEING CHANNEL: "))
      _ <- currentRuntime.callback.succeed(clientResponse)
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
//      reqKey <- connectionManager.getRequestKey(currentRuntime.currReq)
//      _ <- zio.Task.fail(error)
      _ <- zio.ZIO.effect(releaseRequest(currentRuntime.currReq))
    } yield ()
    zExec.unsafeRun(ctx)(r)
//    releaseRequest(connectionRuntime.currReq): Unit
  }

  override def channelActive(ctx: ChannelHandlerContext): Unit = {
    val r = for{
      currAlloc <- connectionManager.connectionState.currentAllocatedChannels.get
      currentRuntime = currAlloc(ctx.channel())
      //      reqKey <- connectionManager.getRequestKey(currentRuntime.currReq)
      _ <- zio.ZIO.effect(ctx.writeAndFlush(currentRuntime.currReq))
      _ <- zio.ZIO.effect(releaseRequest(currentRuntime.currReq))
    } yield ()
    zExec.unsafeRun(ctx)(r)
//    val connectionRuntime = connectionManager.connectionState.currentAllocatedChannels(ctx.channel())
//    val jReq              = connectionRuntime.currReq
//    ctx.writeAndFlush(jReq)
//    releaseRequest(jReq): Unit
  }

  private def releaseRequest(jReq: FullHttpRequest): Unit = {
    if (jReq.refCnt() > 0) {
      jReq.release(jReq.refCnt()): Unit
    }
  }

}
