package zhttp.experiment.internal

import io.netty.buffer.Unpooled
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.http._
import io.netty.util.CharsetUtil
import zhttp.experiment.HttpEndpoint
import zhttp.service.{EventLoopGroup, HttpRuntime}
import zio.internal.Executor
import zio.stm.TQueue
import zio.{UIO, ZIO}

import scala.concurrent.ExecutionContext

case class ChannelProxy(
  inbound: TQueue[HttpObject],
  outbound: TQueue[HttpObject],
  ec: ExecutionContext,
  rtm: zio.Runtime[Any],
) extends EmbeddedChannel { self =>

  private var pendingRead: Boolean = false

  /**
   * Schedules a `writeInbound` operation on the channel using the provided group. This is done to make sure that all
   * the execution of HttpEndpoint happens in the same thread.
   */
  private def scheduleWrite(msg: => HttpObject): UIO[Unit] = UIO {
    val autoRead = self.config().isAutoRead
    if (autoRead) self.writeInbound(msg): Unit
    else {
      self.handleInboundMessage(msg): Unit
      if (pendingRead) {
        self.doBeginRead()
        pendingRead = false
      }
    }
  }
    .on(ec)

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
    ZIO.foreach(iter)(a => data(a, isLast = false)).unit
  }

  def end: UIO[Unit] = scheduleWrite(new DefaultLastHttpContent(Unpooled.EMPTY_BUFFER))

  def end(text: String): UIO[Unit] = data(text, true)

  /**
   * Handles all the outgoing messages ie all the `ctx.write()` and `ctx.writeAndFlush()` that happens inside of the
   * HttpEndpoint.
   */
  override def handleOutboundMessage(msg: AnyRef): Unit = {
    rtm.unsafeRunAsync_(outbound.offer(msg.asInstanceOf[HttpObject]).commit)
  }

  /**
   * Called whenever `ctx.read()` is called from withing the HttpEndpoint
   */
  override def doBeginRead(): Unit = {
    val msg = self.readInbound[HttpObject]()
    if (msg == null) {
      pendingRead = true
    } else {
      self.writeInbound(msg): Unit
      pendingRead = false
    }
  }
}

object ChannelProxy {
  def make[R](app: HttpEndpoint[R, Throwable]): ZIO[R with EventLoopGroup, Nothing, ChannelProxy] = {
    for {
      group <- ZIO.access[EventLoopGroup](_.get)
      rtm   <- ZIO.runtime[Any]
      ec   = ExecutionContext.fromExecutor(group)
      exe  = Executor.fromExecutionContext(2048)(ec)
      gRtm = rtm.withExecutor(exe)
      zExec    <- HttpRuntime.dedicated[R](group)
      outbound <- TQueue.unbounded[HttpObject].commit
      inbound  <- TQueue.unbounded[HttpObject].commit
      proxy    <- UIO {
        val ch = ChannelProxy(inbound, outbound, ec, gRtm)
        ch.pipeline().addLast(app.compile(zExec))
        ch
      }
    } yield proxy
  }
}
