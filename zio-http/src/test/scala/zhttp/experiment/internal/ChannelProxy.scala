package zhttp.experiment.internal

import io.netty.buffer.Unpooled
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.http._
import io.netty.util.CharsetUtil
import zhttp.experiment.HttpEndpoint
import zhttp.service.{EventLoopGroup, HttpRuntime}
import zio.{Exit, Queue, UIO, ZIO}

import scala.concurrent.ExecutionContext

case class ChannelProxy(
  inbound: Queue[HttpObject],
  outbound: Queue[HttpObject],
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

  def receive: UIO[HttpObject] = outbound.take

  def receiveN(n: Int): UIO[List[HttpObject]] = outbound.takeUpTo(n)

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
    ZIO.foreach(iter.zipWithIndex)({ case (a, i) => data(a, isLast = i == iter.size - 1) }).unit
  }

  def end: UIO[Unit] = scheduleWrite(new DefaultLastHttpContent(Unpooled.EMPTY_BUFFER))

  def end(text: String): UIO[Unit] = data(text, true)

  /**
   * Handles all the outgoing messages ie all the `ctx.write()` and `ctx.writeAndFlush()` that happens inside of the
   * HttpEndpoint.
   */
  override def handleOutboundMessage(msg: AnyRef): Unit = {
    // `rtm.unsafeRunAsync_` needs to execute in a single threaded env only.
    // Otherwise, it is possible to have messages being inserted out of order.
    rtm
      .unsafeRunAsync(outbound.offer(msg.asInstanceOf[HttpObject])) {
        case Exit.Failure(cause) => System.err.println(cause.prettyPrint)
        case _                   => ()
      }
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
      ec = ExecutionContext.fromExecutor(group)
      zExec    <- HttpRuntime.dedicated[R](group)
      outbound <- Queue.unbounded[HttpObject]
      inbound  <- Queue.unbounded[HttpObject]
      proxy    <- UIO {
        val ch = ChannelProxy(inbound, outbound, ec, rtm)
        ch.pipeline().addLast(app.compile(zExec))
        ch
      }
    } yield proxy
  }
}
