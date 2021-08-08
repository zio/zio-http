package zhttp.experiment.internal

import io.netty.buffer.Unpooled
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.channel.{EventLoopGroup => JEventLoopGroup}
import io.netty.handler.codec.http._
import io.netty.util.CharsetUtil
import zhttp.experiment.HApp
import zhttp.service.{EventLoopGroup, UnsafeChannelExecutor}
import zio.{Queue, UIO, ZIO}

case class ChannelProxy(queue: Queue[HttpObject], channel: EmbeddedChannel, group: JEventLoopGroup)
    extends EmbeddedChannel {

  /**
   * Schedules a `writeInbound` operation on the channel using the provided group. This is done to make sure that all
   * the execution of HApp happens in the same thread.
   */
  private def scheduleWrite(msg: => HttpObject): UIO[Unit] = UIO {
    group.execute(() => channel.writeInbound(msg): Unit)
  }

  def receive: UIO[HttpObject] = queue.take

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
    ZIO.foreach(iter)(a => data(a)) *> end
  }

  def end: UIO[Unit] = scheduleWrite(new DefaultLastHttpContent(Unpooled.EMPTY_BUFFER))
}

object ChannelProxy {
  def make[R](app: HApp[R, Throwable]): ZIO[R with EventLoopGroup, Nothing, ChannelProxy] =
    for {

      group <- ZIO.access[EventLoopGroup](_.get)
      zExec <- UnsafeChannelExecutor.dedicated[R](group)
      rtm   <- ZIO.runtime[Any]
      queue <- Queue.unbounded[HttpObject]

    } yield {
      val embeddedChannel = new EmbeddedChannel(app.compile(zExec)) { self =>
        /**
         * Handles all the outgoing messages ie all the `ctx.write()` and `ctx.writeAndFlush()` that happens inside of
         * the HApp.
         */
        override def handleOutboundMessage(msg: AnyRef): Unit = {
          rtm.unsafeRunAsync_(queue.offer(msg.asInstanceOf[HttpObject]))
          super.handleOutboundMessage(msg)
        }

        /**
         * Called whenever `ctx.read()` is called from withing the HApp
         */
        override def doBeginRead(): Unit = {
          val msg = self.readInbound[HttpObject]()
          if (msg != null) {
            super.writeInbound(msg): Unit
          }
        }

        /**
         * This is used to simulate the requests and the content coming from the client.
         */
        override def writeInbound(msgs: AnyRef*): Boolean = {
          if (self.config().isAutoRead) {

            // If auto-read is set to `true` continue with the default behaviour.
            super.writeInbound(msgs: _*)
          } else {

            // If auto-read is set to `false` insert the messages into the internal queue.
            for (msg <- msgs) {
              handleInboundMessage(msg)
            }
            true
          }
        }
      }

      ChannelProxy(queue, embeddedChannel, group)
    }
}
