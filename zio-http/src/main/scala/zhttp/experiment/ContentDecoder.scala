package zhttp.experiment

import zhttp.http.HttpData
import zio.{Chunk, Queue, UIO, ZIO}

sealed trait ContentDecoder[-R, +E, -A, +B] { self =>
  def decode[R1 <: R, E1 >: E](data: HttpData[R1, E1])(implicit ev: Chunk[Byte] <:< A): ZIO[R1, E1, B] =
    ContentDecoder.decode(self.asInstanceOf[ContentDecoder[R1, E1, Chunk[Byte], B]], data)
}

object ContentDecoder {

  case object Text extends ContentDecoder[Any, Nothing, Any, String]

  case class Step[R, E, S, A, B](state: S, next: (A, S, Boolean) => ZIO[R, E, (Option[B], S)])
      extends ContentDecoder[R, E, A, B]

  private[zhttp] case class BackPressure[B](queue: Option[Queue[B]] = None, isFirst: Boolean = true) {
    self =>
    def withQueue(queue: Queue[B]): BackPressure[B] = if (self.queue.isEmpty) self.copy(queue = Option(queue)) else self
    def withFirst(cond: Boolean): BackPressure[B]   = if (cond == isFirst) self else self.copy(isFirst = cond)
  }

  val text: ContentDecoder[Any, Nothing, Any, String] = Text

  def collect[S, A]: PartiallyAppliedCollect[S, A] = new PartiallyAppliedCollect(())

  final class PartiallyAppliedCollect[S, A](val unit: Unit) extends AnyVal {
    def apply[R, E, B](s: S)(f: (A, S, Boolean) => ZIO[R, E, (Option[B], S)]): ContentDecoder[R, E, A, B] = Step(s, f)
  }

  val backPressure: ContentDecoder[Any, Nothing, Chunk[Byte], Queue[Chunk[Byte]]] =
    ContentDecoder.collect(BackPressure[Chunk[Byte]]()) { case (msg, state, _) =>
      for {
        queue <- state.queue.fold(Queue.bounded[Chunk[Byte]](1))(UIO(_))
        _     <- queue.offer(msg)
      } yield (if (state.isFirst) Option(queue) else None, state.withQueue(queue).withFirst(false))
    }

  sealed trait Error extends Throwable with Product { self =>
    override def getMessage(): String =
      self match {
        case Error.ContentDecodedOnce => "Content has already been decoded once."
        case Error.DecodeEmptyContent => "Can not decode empty content"
      }
  }

  private def decode[R, E, A](decoder: ContentDecoder[R, E, Chunk[Byte], A], data: HttpData[R, E]): ZIO[R, E, A] = ???

  object Error {
    case object ContentDecodedOnce extends Error
    case object DecodeEmptyContent extends Error
  }
}
