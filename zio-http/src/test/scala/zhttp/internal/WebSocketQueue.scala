package zhttp.internal

import zhttp.socket.WebSocketFrame
import zio.{Queue, UIO, URIO, ZIO, ZLayer}

object WebSocketQueue {

  trait Service {
    def offer(frame: WebSocketFrame): ZIO[Any, Nothing, Boolean]
    def shutdown: UIO[Unit]
    def queue: Queue[WebSocketFrame]
  }

  case class Live(q: Queue[WebSocketFrame]) extends Service {
    def offer(frame: WebSocketFrame): ZIO[Any, Nothing, Boolean] = q.offer(frame)
    def shutdown: UIO[Unit]                                      = q.shutdown
    def queue: Queue[WebSocketFrame]                             = q
  }

  def live: ZLayer[Any, Nothing, WebSocketQueue] = {
    for {
      queue <- Queue.unbounded[WebSocketFrame]
    } yield Live(queue)
  }.toLayer

  def offer(frame: WebSocketFrame): URIO[WebSocketQueue, Boolean] = ZIO.accessM[WebSocketQueue](_.get.offer(frame))
  def queue: URIO[WebSocketQueue, Queue[WebSocketFrame]]          = ZIO.access[WebSocketQueue](_.get.queue)
  def shutdown: ZIO[WebSocketQueue, Nothing, Unit]                = ZIO.accessM[WebSocketQueue](_.get.shutdown)
}
