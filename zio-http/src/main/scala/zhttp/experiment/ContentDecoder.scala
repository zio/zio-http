package zhttp.experiment

import io.netty.buffer.{ByteBuf, ByteBufUtil}
import zhttp.http.HTTP_CHARSET
import zio.{Chunk, Queue, UIO, ZIO}

sealed trait ContentDecoder[-R, +E, +B] { self =>
  def getContent(content: ByteBuf): ZIO[R, E, Option[B]]
}

object ContentDecoder {

  case object Text extends ContentDecoder[Any, Nothing, String] {
    override def getContent(content: ByteBuf): ZIO[Any, Nothing, Option[String]] =
      ZIO.succeed(Option(content.toString(HTTP_CHARSET)))
  }

  case class Custom[R, E, S, B](state: S, run: (Chunk[Byte], S, Boolean) => ZIO[R, E, (Option[B], S)])
      extends ContentDecoder[R, E, B] {
    override def getContent(content: ByteBuf): ZIO[R, E, Option[B]] = for {
      (a, _) <- run(Chunk.fromArray(ByteBufUtil.getBytes(content)), state, true)
    } yield a
  }

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

  sealed trait Error extends Throwable with Product { self =>
    override def getMessage(): String =
      self match {
        case Error.ContentDecodedOnce => "Content has already been decoded once."
        case Error.DecodeEmptyContent => "Can not decode empty content"
      }
  }

  object Error {
    case object ContentDecodedOnce extends Error
    case object DecodeEmptyContent extends Error
  }
}
