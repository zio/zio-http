package zhttp.experiment.internal

import io.netty.buffer.Unpooled
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.http._
import io.netty.util.CharsetUtil
import zhttp.experiment.HttpEndpoint
import zhttp.experiment.internal.ChannelProxy.MessageQueue
import zhttp.service.{EventLoopGroup, HttpRuntime}
import zio.internal.Executor
import zio.stm.TQueue
import zio.stream.ZStream
import zio.{Exit, Queue, UIO, ZIO}

import scala.concurrent.ExecutionContext

case class ChannelProxy(
  inbound: MessageQueue[HttpObject],
  outbound: MessageQueue[HttpObject],
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

  def receive: UIO[HttpObject]                = outbound.take
  def receiveN(n: Int): UIO[List[HttpObject]] = outbound.takeN(n)

  def request(
    url: String = "/",
    method: HttpMethod = HttpMethod.GET,
    headers: HttpHeaders = EmptyHttpHeaders.INSTANCE,
    version: HttpVersion = HttpVersion.HTTP_1_1,
  ): UIO[Unit] = {
    scheduleWrite(new DefaultHttpRequest(version, method, url, headers))
  }

  def write(text: String, isLast: Boolean = false): UIO[Unit] = {
    if (isLast)
      scheduleWrite(new DefaultLastHttpContent(Unpooled.copiedBuffer(text.getBytes(CharsetUtil.UTF_8))))
    else
      scheduleWrite(new DefaultHttpContent(Unpooled.copiedBuffer(text.getBytes(CharsetUtil.UTF_8))))
  }

  def data(iter: String*): UIO[Unit] = data(iter)

  def data(iter: Iterable[String]): UIO[Unit] = {
    ZIO.foreach(iter)(write(_, isLast = false)).unit
  }

  def end(iter: Iterable[String]): UIO[Unit] = {
    if (iter.isEmpty) end
    else ZIO.foreach(iter.zipWithIndex)({ case (c, i) => write(c, isLast = i == iter.size - 1) }).unit
  }

  def end: UIO[Unit] = scheduleWrite(new DefaultLastHttpContent(Unpooled.EMPTY_BUFFER))

  def end(iter: String*): UIO[Unit] = end(iter)

  /**
   * Handles all the outgoing messages ie all the `ctx.write()` and `ctx.writeAndFlush()` that happens inside of the
   * HttpEndpoint.
   */
  override def handleOutboundMessage(msg: AnyRef): Unit = {
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

  sealed trait MessageQueue[A] {
    def offer(msg: A): UIO[Unit]
    def take: UIO[A]
    def takeN(n: Int): UIO[List[A]]
    def asStream: ZStream[Any, Nothing, A]
  }

  object MessageQueue {
    case class Live[A](q: Queue[A]) extends MessageQueue[A] {
      override def offer(msg: A): UIO[Unit]           = q.offer(msg).unit
      override def take: UIO[A]                       = q.take
      override def takeN(n: Int): UIO[List[A]]        = q.takeN(n)
      override def asStream: ZStream[Any, Nothing, A] = ZStream.fromQueue(q)
    }

    case class Transactional[A](q: TQueue[A]) extends MessageQueue[A] {
      override def offer(msg: A): UIO[Unit]           = q.offer(msg).commit.unit
      override def take: UIO[A]                       = q.take.commit
      override def takeN(n: Int): UIO[List[A]] = {
        def loop(list: List[A]): UIO[List[A]] = {
          if (list.size == n) UIO(list)
          else q.take.commit.flatMap(i => loop(i :: list))
        }

        loop(Nil)
      }
      override def asStream: ZStream[Any, Nothing, A] = ZStream.fromTQueue(q)
    }

    def stm[A]: UIO[MessageQueue[A]]     = TQueue.unbounded[A].commit.map(Transactional(_))
    def default[A]: UIO[MessageQueue[A]] = Queue.unbounded[A].map(Live(_))
  }

  def make[R](app: HttpEndpoint[R, Throwable]): ZIO[R with EventLoopGroup, Nothing, ChannelProxy] = {
    for {
      group <- ZIO.access[EventLoopGroup](_.get)
      rtm   <- ZIO.runtime[Any]
      ec = ExecutionContext.fromExecutor(group)

      // !!! IMPORTANT !!!
      // `rtm.unsafeRunAsync_` needs to execute in a single threaded env only.
      // Otherwise, it is possible to have messages being inserted out of order.
      grtm = rtm.withExecutor(Executor.fromExecutionContext(2048)(ec))
      zExec    <- HttpRuntime.dedicated[R](group)
      outbound <- MessageQueue.default[HttpObject]
      inbound  <- MessageQueue.default[HttpObject]
      proxy    <- UIO {
        val ch = ChannelProxy(inbound, outbound, ec, grtm)
        ch.pipeline().addLast(app.compile(zExec))
        ch
      }
    } yield proxy
  }
}
