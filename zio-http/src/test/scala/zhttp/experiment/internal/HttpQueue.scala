package zhttp.experiment.internal

import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.http.HttpMessage
import zhttp.experiment.HApp
import zhttp.service.{EventLoopGroup, UnsafeChannelExecutor}
import zio.{Queue, UIO, ZIO}

case class HttpQueue(rtm: zio.Runtime[Any], channel: EmbeddedChannel, queue: Queue[HttpMessage]) {
  def offer(a: HttpMessage): UIO[Boolean] = UIO(channel.writeInbound(a))
  def take: UIO[HttpMessage]              = queue.take
}

object HttpQueue {
  def make[R](
    app: HApp[R, Throwable],
  ): ZIO[R with EventLoopGroup, Nothing, HttpQueue] =
    for {
      zExec <- UnsafeChannelExecutor.make[R]
      rtm   <- ZIO.runtime[Any]
      queue <- Queue.unbounded[HttpMessage]
    } yield {
      val embeddedChannel = new EmbeddedChannel(app.compile(zExec)) {
        override def handleOutboundMessage(msg: AnyRef): Unit = {
          rtm.unsafeRunAsync_(queue.offer(msg.asInstanceOf[HttpMessage]))
          super.handleOutboundMessage(msg)
        }
      }
      HttpQueue(rtm, embeddedChannel, queue)
    }
}
