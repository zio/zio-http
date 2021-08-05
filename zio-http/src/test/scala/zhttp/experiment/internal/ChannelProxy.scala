package zhttp.experiment.internal

import io.netty.buffer.Unpooled
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.http._
import io.netty.util.CharsetUtil
import zhttp.experiment.HApp
import zhttp.service.{EventLoopGroup, UnsafeChannelExecutor}
import zio.{Queue, UIO, ZIO}

case class ChannelProxy(rtm: zio.Runtime[Any], channel: EmbeddedChannel, queue: Queue[HttpObject]) {
  def dispatch(a: HttpObject): UIO[Boolean] = UIO(channel.writeInbound(a))
  def receive: UIO[HttpObject]              = queue.take

  def request(
    version: HttpVersion = HttpVersion.HTTP_1_1,
    method: HttpMethod = HttpMethod.GET,
    url: String = "/",
    headers: HttpHeaders = EmptyHttpHeaders.INSTANCE,
  ): UIO[Boolean] = {
    dispatch(new DefaultHttpRequest(version, method, url, headers))
  }

  def get(
    url: String = "/",
    headers: HttpHeaders = EmptyHttpHeaders.INSTANCE,
  ): UIO[Boolean] = request(url = url, headers = headers)

  def post(
    url: String = "/",
    headers: HttpHeaders = EmptyHttpHeaders.INSTANCE,
  ): UIO[Boolean] = request(method = HttpMethod.POST, url = url, headers = headers)

  def content(text: String, isLast: Boolean = false): UIO[Boolean] = {
    if (isLast)
      dispatch(new DefaultLastHttpContent(Unpooled.copiedBuffer(text.getBytes(CharsetUtil.UTF_8))))
    else
      dispatch(new DefaultHttpContent(Unpooled.copiedBuffer(text.getBytes(CharsetUtil.UTF_8))))
  }

  def last: UIO[Boolean] = dispatch(new DefaultLastHttpContent(Unpooled.EMPTY_BUFFER))

}

object ChannelProxy {
  def make[R](
    app: HApp[R, Throwable],
  ): ZIO[R with EventLoopGroup, Nothing, ChannelProxy] =
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
      ChannelProxy(rtm, embeddedChannel, queue)
    }
}
