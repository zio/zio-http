package zio.http.datastar

import zio._

import zio.http.ServerSentEvent

/**
 * The handle that `events` puts in the environment of the handler it runs. The methods of `ServerSentEventGenerator` read it from the environment and queue their events on it.
 */
final class Datastar(private[http] val queue: Queue[ServerSentEvent[String]]) extends AnyVal

/** Package-private constructors for `Datastar`. */
object Datastar {
  private[http] def apply(queue: Queue[ServerSentEvent[String]]): Datastar =
    new Datastar(queue)

  private[http] val done: ServerSentEvent[String] = ServerSentEvent("done")

  private[http] def make: ZIO[Scope, Nothing, Datastar] =
    ZIO.acquireRelease(Queue.unbounded[ServerSentEvent[String]].map(Datastar(_)))(_.queue.shutdown)
}
