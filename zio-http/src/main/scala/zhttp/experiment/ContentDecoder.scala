package zhttp.experiment

import zio.{Chunk, Queue, UIO, ZIO}

sealed trait ContentDecoder[-R, +E, +B] { self => }

object ContentDecoder {

  case object Text extends ContentDecoder[Any, Nothing, String]

  case class Custom[-R, +E, S, +B](state: S, run: (Chunk[Byte], S, Boolean) => ZIO[R, E, (Option[B], S)])
      extends ContentDecoder[R, E, B]

  private[zhttp] case class BackPressure[B](queue: Option[Queue[B]] = None, isFirst: Boolean = true) {
    self =>
    def withQueue(queue: Queue[B]): BackPressure[B] = if (self.queue.isEmpty) self.copy(queue = Option(queue)) else self
    def withFirst(cond: Boolean): BackPressure[B]   = if (cond == isFirst) self else self.copy(isFirst = cond)
  }

  val text: ContentDecoder[Any, Nothing, String] = Text

  def collect[R, E, S, B](state: S)(
    run: (Chunk[Byte], S, Boolean) => ZIO[R, E, (Option[B], S)],
  ): ContentDecoder[R, E, B] =
    Custom(state, run)

  val backPressure: ContentDecoder[Any, Nothing, Queue[Chunk[Byte]]] =
    ContentDecoder.collect(BackPressure[Chunk[Byte]]()) { case (msg, state, _) =>
      for {
        queue <- state.queue.fold(Queue.bounded[Chunk[Byte]](1))(UIO(_))
        _     <- queue.offer(msg)
      } yield (if (state.isFirst) Option(queue) else None, state.withQueue(queue).withFirst(false))
    }
}
