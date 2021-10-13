package zhttp.experiment.internal

import io.netty.buffer.Unpooled
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.http._
import io.netty.util.CharsetUtil
import zhttp.experiment.internal.HttpAppClient.{MessageQueue, ProxyChannel}
import zhttp.http.HttpApp
import zhttp.service.{EventLoopGroup, HTTP_HANDLER, HttpRuntime}
import zio._
import zio.internal.Executor
import zio.stm.TQueue
import zio.stream.ZStream

import scala.concurrent.ExecutionContext

case class HttpAppClient(outbound: MessageQueue[Any], channel: ProxyChannel) { self =>
  def receive: UIO[Any] = outbound.take

  def write(data: AnyRef): Task[Unit] = channel.writeM(data)

  def receiveN(n: Int): UIO[List[Any]] = outbound.takeN(n)

  def request(
    url: String = "/",
    method: HttpMethod = HttpMethod.GET,
    headers: HttpHeaders = EmptyHttpHeaders.INSTANCE,
    version: HttpVersion = HttpVersion.HTTP_1_1,
  ): Task[Unit] = {
    channel.writeM(new DefaultHttpRequest(version, method, url, headers))
  }

  def writeText(text: String, isLast: Boolean = false): Task[Unit] = {
    if (isLast)
      channel.writeM(new DefaultLastHttpContent(Unpooled.copiedBuffer(text.getBytes(CharsetUtil.UTF_8))))
    else
      channel.writeM(new DefaultHttpContent(Unpooled.copiedBuffer(text.getBytes(CharsetUtil.UTF_8))))
  }

  def data(iter: String*): Task[Unit] = data(iter)

  def data(iter: Iterable[String]): Task[Unit] = {
    ZIO.foreach(iter)(writeText(_)).as(())
  }

  def end(iter: Iterable[String]): Task[Unit] = {
    if (iter.isEmpty) end
    else
      ZIO
        .foreach(iter.zipWithIndex) { case (c, i) =>
          writeText(c, isLast = i == iter.size - 1)
        }
        .as(())
  }

  def end: Task[Unit] = channel.writeM(new DefaultLastHttpContent(Unpooled.EMPTY_BUFFER))

  def end(iter: String*): Task[Unit] = end(iter)
}

object HttpAppClient {
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

  final case class ProxyChannel(
    inbound: MessageQueue[Any],
    outbound: MessageQueue[Any],
    ec: ExecutionContext,
    rtm: zio.Runtime[Any],
    allowedThread: Thread,
  ) extends EmbeddedChannel() { self =>
    private var pendingRead: Boolean = false

    /**
     * Asserts if the function has been called within the same thread.
     */
    def assertThread(name: String): Unit = {
      val cThread = Thread.currentThread()
      assert(
        cThread == allowedThread,
        s"'${name}' was called from ${cThread.getName()}. Expected thread was: ${allowedThread.getName()}",
      )
    }

    /**
     * Schedules a `writeInbound` operation on the channel using the provided group. This is done to make sure that all
     * the execution of HttpApp happens in the same thread.
     */
    def writeM(msg: => AnyRef): Task[Unit] = Task {
      assertThread("writeM")

      val autoRead = self.config().isAutoRead

      if (autoRead || pendingRead) {

        // Calls to `writeInbound()` internally triggers `fireChannelRead()`
        // Which can call `ctx.read()` within the channel handler.
        // `ctx.read` can set the pendingRead flag to true, which should be carried forwarded.
        // So `pendingRead` should be set to false before `writeInbound` is called.
        self.pendingRead = false

        // Triggers `fireChannelRead` on the channel handler.
        self.writeInbound(msg): Unit
      } else {

        // Insert message into internal "read" queue.
        // Messages will be pulled from that queue every time `ctx.read` is called.
        self.handleInboundMessage(msg): Unit

      }

    }
      .on(ec)

    /**
     * Handles all the outgoing messages ie all the `ctx.write()` and `ctx.writeAndFlush()` that happens inside of the
     * HttpApp.
     */
    override def handleOutboundMessage(msg: AnyRef): Unit = {
      assertThread("handleOutboundMessage")
      rtm
        .unsafeRunAsync(outbound.offer(msg.asInstanceOf[Any])) {
          case Exit.Failure(cause) => System.err.println(cause.prettyPrint)
          case _                   => ()
        }
    }

    /**
     * Called whenever `ctx.read()` is called from withing the HttpApp
     */
    override def doBeginRead(): Unit = {
      assertThread("doBeginRead")
      val msg = self.readInbound[Any]()
      if (msg == null) {
        self.pendingRead = true
      } else {
        self.writeInbound(msg): Unit
        self.pendingRead = false

      }

    }
  }

  def deploy[R](app: HttpApp[R, Throwable]): ZIO[R with EventLoopGroup, Nothing, HttpAppClient] = {
    for {
      // Create a promise that resolves with the thread that is allowed for the execution
      // It is later used to guarantee that all the execution happens on the same thread.
      threadRef <- Promise.make[Nothing, Thread]
      group     <- ZIO.access[EventLoopGroup](_.get)
      rtm       <- ZIO.runtime[Any]
      ec = ExecutionContext.fromExecutor(group)

      // !!! IMPORTANT !!!
      // `rtm.unsafeRunAsync_` needs to execute in a single threaded env only.
      // Otherwise, it is possible to have messages being inserted out of order.
      grtm = rtm.withExecutor(Executor.fromExecutionContext(2048)(ec))
      zExec    <- HttpRuntime.dedicated[R](group)
      outbound <- MessageQueue.default[Any]
      inbound  <- MessageQueue.default[Any]
      _        <- ZIO.effectSuspendTotal(threadRef.succeed(Thread.currentThread())).on(ec)
      thread   <- threadRef.await
      proxy    <- UIO {

        val channel = ProxyChannel(inbound, outbound, ec, grtm, thread)
        channel.pipeline().addLast(HTTP_HANDLER, app.compile(zExec))
        HttpAppClient(outbound, channel)
      }.on(ec)
    } yield proxy
  }
}
