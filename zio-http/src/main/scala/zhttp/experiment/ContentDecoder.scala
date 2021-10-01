package zhttp.experiment

import io.netty.buffer.ByteBufUtil
import zhttp.http.{HTTP_CHARSET, HttpData}
import zio.{Chunk, Queue, UIO, ZIO}

sealed trait ContentDecoder[-R, +E, -A, +B] { self => }

object ContentDecoder {

  case object Text extends ContentDecoder[Any, Nothing, Any, String]

  case class Step[R, E, S, A, B](state: S, next: (A, S, Boolean) => ZIO[R, E, (Option[B], S)])
      extends ContentDecoder[R, E, A, B]

  private[zhttp] case class BackPressure[B](queue: Option[Queue[B]] = None, isFirst: Boolean = true) {
    self =>
    def withQueue(queue: Queue[B]): BackPressure[B] = if (self.queue.isEmpty) self.copy(queue = Option(queue)) else self
    def withFirst(cond: Boolean): BackPressure[B]   = if (cond == isFirst) self else self.copy(isFirst = cond)
  }

  def decodeContent[R, E, B](
    decoder: ContentDecoder[R, Throwable, Chunk[Byte], B],
    content: HttpData[R, E],
  ): ZIO[R, Throwable, Option[B]] =
    decoder match {
      case Text                                          =>
        content match {
          case HttpData.Empty           => ZIO.fail(ContentDecoder.Error.DecodeEmptyContent)
          case HttpData.Text(text, _)   => ZIO(Option(text))
          case HttpData.Binary(_)       => ???
          case HttpData.BinaryN(data)   => ZIO(Option(data.toString(HTTP_CHARSET)))
          case HttpData.BinaryStream(_) => ???
          case HttpData.Socket(_)       => ZIO.fail(ContentDecoder.Error.DecodeEmptyContent)
        }
      case step: Step[R, Throwable, Any, Chunk[Byte], B] =>
        content match {
          case HttpData.Empty           => ZIO.fail(ContentDecoder.Error.DecodeEmptyContent)
          case HttpData.Text(_, _)      => ???
          case HttpData.Binary(data)    => step.next(data, step.state, true).map(a => a._1)
          case HttpData.BinaryN(data)   =>
            step.next(Chunk.fromArray(ByteBufUtil.getBytes(data)), step.state, true).map(a => a._1)
          case HttpData.BinaryStream(_) => ???
          case HttpData.Socket(_)       => ZIO.fail(ContentDecoder.Error.DecodeEmptyContent)
        }
    }

  val text: ContentDecoder[Any, Nothing, Any, String] = Text

  def collect[R, E, S, A, B](state: S)(
    run: (A, S, Boolean) => ZIO[R, E, (Option[B], S)],
  ): ContentDecoder[R, E, A, B] =
    Step(state, run)

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

  object Error {
    case object ContentDecodedOnce extends Error
    case object DecodeEmptyContent extends Error
  }
}
