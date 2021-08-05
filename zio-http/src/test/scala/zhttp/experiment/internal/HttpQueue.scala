package zhttp.experiment.internal

import io.netty.buffer.Unpooled
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.http._
import io.netty.util.CharsetUtil
import zhttp.experiment.HApp
import zhttp.service.{EventLoopGroup, UnsafeChannelExecutor}
import zio.{Queue, UIO, ZIO}

case class HttpQueue(rtm: zio.Runtime[Any], channel: EmbeddedChannel, queue: Queue[HttpObject]) {
  def dispatch(a: HttpObject): UIO[Boolean] = UIO(channel.writeInbound(a))
  def receive: UIO[HttpObject]              = queue.take

  def request(version: HttpVersion, method: HttpMethod, url: String, headers: HttpHeaders): UIO[Boolean] = {
    dispatch(new DefaultHttpRequest(version, method, url, headers))
  }

  def request(version: HttpVersion, method: HttpMethod, url: String): UIO[Boolean] = {
    dispatch(new DefaultHttpRequest(version, method, url))
  }

  def content(text: String, isLast: Boolean = false): UIO[Boolean] = {
    if (isLast)
      dispatch(new DefaultLastHttpContent(Unpooled.copiedBuffer(text.getBytes(CharsetUtil.UTF_8))))
    else
      dispatch(new DefaultHttpContent(Unpooled.copiedBuffer(text.getBytes(CharsetUtil.UTF_8))))
  }

  def last: UIO[Boolean] = dispatch(new DefaultLastHttpContent(Unpooled.EMPTY_BUFFER))

}

object HttpQueue {
  def make[R](
    app: HApp[R, Throwable],
  ): ZIO[R with EventLoopGroup, Nothing, HttpQueue] =
    for {
      zExec <- UnsafeChannelExecutor.make[R]
      rtm   <- ZIO.runtime[Any]
      queue <- Queue.unbounded[HttpObject]
    } yield {
      val embeddedChannel = new EmbeddedChannel(app.compile(zExec)) {
        override def handleOutboundMessage(msg: AnyRef): Unit = {
          rtm.unsafeRunAsync_(queue.offer(msg.asInstanceOf[HttpObject]))
          super.handleOutboundMessage(msg)
        }
      }
      HttpQueue(rtm, embeddedChannel, queue)
    }
}
