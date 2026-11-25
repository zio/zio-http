package zio.http

import java.nio.channels.ClosedChannelException

import zio._
import zio.stacktracer.TracingImplicits.disableAutoTrace

import zio.http.ChannelEvent._

/**
 * An in-memory `WebSocketChannel` backed by two queues. Events that the channel
 * receives are taken from `in`, and events that it sends are offered to `out`.
 * `TestClient` creates a pair of channels over the same two queues, one for the
 * client side and one for the server side, with `in` and `out` swapped.
 *
 * @param in
 *   The queue that `receive` and `receiveAll` read from
 * @param out
 *   The queue that `send` and `sendAll` write to
 * @param promise
 *   Completed by `shutdown`; once it is done, `send` and `sendAll` fail
 */
case class TestChannel(
  in: Queue[WebSocketChannelEvent],
  out: Queue[WebSocketChannelEvent],
  promise: Promise[Nothing, Unit],
) extends WebSocketChannel {

  /** Waits until the shutdown promise is completed, which `shutdown` does. */
  def awaitShutdown(implicit trace: Trace): UIO[Unit] =
    promise.await

  /** Takes the next event from `in`, waiting until one is available. */
  def receive(implicit trace: Trace): Task[WebSocketChannelEvent] =
    in.take

  /**
   * Takes events from `in` and runs `f` on each of them, until `f` has run on
   * an `ExceptionCaught` or an `Unregistered` event. The returned effect then
   * completes with `()`.
   */
  def receiveAll[Env, Err](f: WebSocketChannelEvent => ZIO[Env, Err, Any])(implicit
    trace: Trace,
  ): ZIO[Env, Err, Unit] = {
    lazy val loop: ZIO[Env, Err, Unit] =
      in.take.flatMap {
        case event: ChannelEvent.ExceptionCaught   => f(event).unit
        case event: ChannelEvent.Unregistered.type => f(event).unit
        case event                                 => f(event) *> ZIO.yieldNow *> loop
      }

    loop
  }

  /**
   * Offers the event to `out`. Fails with a
   * `java.nio.channels.ClosedChannelException` when the channel has been shut
   * down.
   */
  def send(in: WebSocketChannelEvent)(implicit trace: Trace): Task[Unit] =
    whenOpen(out.offer(in).unit)

  /**
   * Offers the events to `out`. Fails with a
   * `java.nio.channels.ClosedChannelException` when the channel has been shut
   * down.
   */
  def sendAll(in: Iterable[WebSocketChannelEvent])(implicit trace: Trace): Task[Unit] =
    whenOpen(out.offerAll(in).unit)

  private def whenOpen[A](zio: => Task[A])(implicit trace: Trace): Task[A] =
    promise.isDone.flatMap {
      case true  => ZIO.fail(new ClosedChannelException)
      case false => zio
    }

  /**
   * Offers an `Unregistered` event to both `in` and `out`, then completes the
   * shutdown promise. After this, `send` and `sendAll` fail on this channel.
   */
  def shutdown(implicit trace: Trace): UIO[Unit] =
    in.offer(ChannelEvent.Unregistered) *>
      out.offer(ChannelEvent.Unregistered) *>
      promise.succeed(()).unit
}

/** Constructor for [[TestChannel]]. */
object TestChannel {

  /**
   * Creates a `TestChannel` over the given queues and offers a
   * `UserEventTriggered(UserEvent.HandshakeComplete)` event to `out`.
   */
  def make(
    in: Queue[WebSocketChannelEvent],
    out: Queue[WebSocketChannelEvent],
    promise: Promise[Nothing, Unit],
  )(implicit trace: Trace): ZIO[Any, Nothing, TestChannel] =
    for {
      _ <- out.offer(UserEventTriggered(UserEvent.HandshakeComplete))
    } yield TestChannel(in, out, promise)
}
