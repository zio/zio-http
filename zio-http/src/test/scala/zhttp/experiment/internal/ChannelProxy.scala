package zhttp.experiment.internal

import io.netty.buffer.Unpooled
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.http._
import io.netty.util.CharsetUtil
import zhttp.experiment.HApp
import zhttp.service.{EventLoopGroup, UnsafeChannelExecutor}
import zio.{UIO, ZIO}
import zio.internal.Executor
import zio.stm.TQueue

import scala.concurrent.ExecutionContext

case class ChannelProxy(
  inbound: TQueue[HttpObject],
  outbound: TQueue[HttpObject],
  channel: EmbeddedChannel,
  ec: ExecutionContext,
) extends EmbeddedChannel {

  /**
   * Schedules a `writeInbound` operation on the channel using the provided group. This is done to make sure that all
   * the execution of HApp happens in the same thread.
   */
  private def scheduleWrite(msg: => HttpObject): UIO[Unit] = (for {
    canWrite <- UIO(channel.config().isAutoRead)
    _        <-
      if (canWrite) UIO { channel.writeInbound(msg) }
      else
        for {
          _ <- inbound.offer(msg).commit
        } yield ()
  } yield ()).on(ec)

  def receive: UIO[HttpObject] = outbound.take.commit

  def request(
    url: String = "/",
    method: HttpMethod = HttpMethod.GET,
    headers: HttpHeaders = EmptyHttpHeaders.INSTANCE,
    version: HttpVersion = HttpVersion.HTTP_1_1,
  ): UIO[Unit] = {
    scheduleWrite(new DefaultHttpRequest(version, method, url, headers))
  }

  def data(text: String, isLast: Boolean = false): UIO[Unit] = {
    if (isLast)
      scheduleWrite(new DefaultLastHttpContent(Unpooled.copiedBuffer(text.getBytes(CharsetUtil.UTF_8))))
    else
      scheduleWrite(new DefaultHttpContent(Unpooled.copiedBuffer(text.getBytes(CharsetUtil.UTF_8))))
  }

  def data(iter: Iterable[String]): UIO[Unit] = {
    ZIO.foreach(iter)(a => data(a, isLast = false)) *> end
  }

  def end: UIO[Unit] = scheduleWrite(new DefaultLastHttpContent(Unpooled.EMPTY_BUFFER))
}

object ChannelProxy {
  def make[R](app: HApp[R, Throwable]): ZIO[R with EventLoopGroup, Nothing, ChannelProxy] =
    for {

      group <- ZIO.access[EventLoopGroup](_.get)
      rtm   <- ZIO.runtime[Any]
      ec   = ExecutionContext.fromExecutor(group)
      exe  = Executor.fromExecutionContext(2048)(ec)
      gRtm = rtm.withExecutor(exe)
      zExec    <- UnsafeChannelExecutor.dedicated[R](group)
      outbound <- TQueue.unbounded[HttpObject].commit
      inbound  <- TQueue.unbounded[HttpObject].commit
      proxy    <- UIO {
        val embeddedChannel = new EmbeddedChannel(app.compile(zExec)) { self =>
          /**
           * Handles all the outgoing messages ie all the `ctx.write()` and `ctx.writeAndFlush()` that happens inside of
           * the HApp.
           */
          override def handleOutboundMessage(msg: AnyRef): Unit = {
            gRtm.unsafeRunAsync_(outbound.offer(msg.asInstanceOf[HttpObject]).commit)
          }

          /**
           * Called whenever `ctx.read()` is called from withing the HApp
           */
          override def doBeginRead(): Unit = {
            gRtm
              .unsafeRunAsync_(
                inbound.take.commit
                  .tap(msg =>
                    UIO {
                      self.writeInbound(msg)
                    },
                  )
                  .on(ec),
              ): Unit
          }
        }

        ChannelProxy(inbound, outbound, embeddedChannel, ec)
      }
    } yield proxy
}
