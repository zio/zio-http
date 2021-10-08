package zhttp.experiment

import io.netty.buffer.{ByteBufUtil, Unpooled}
import zhttp.http.{HTTP_CHARSET, RequestContent}
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

  def decodeContent[R, E <: Throwable, B](
    decoder: ContentDecoder[R, Throwable, Chunk[Byte], B],
    content: RequestContent[Any, E],
  ): ZIO[R, Throwable, Option[B]] =
    decoder match {
      case Text                                     =>
        content match {
          case RequestContent.Empty           => ZIO.fail(ContentDecoder.Error.DecodeEmptyContent)
          case RequestContent.Text(text, _)   => ZIO(Option(text))
          case RequestContent.Binary(data)    => ZIO(Some(new String(data.toArray, HTTP_CHARSET)))
          case RequestContent.BinaryN(data)   => ZIO(Option(data.toString(HTTP_CHARSET)))
          case RequestContent.BinaryStream(s) =>
            s.fold(Unpooled.compositeBuffer())((s, b) => s.writeBytes(Array(b)))
              .map(b => Some(b.toString(HTTP_CHARSET)))

        }
      case step: ContentDecoder.Step[_, _, _, _, _] =>
        content match {
          case RequestContent.Empty               => ZIO.fail(ContentDecoder.Error.DecodeEmptyContent)
          case RequestContent.Text(data, charset) =>
            step
              .asInstanceOf[ContentDecoder.Step[R, Throwable, Any, Chunk[Byte], B]]
              .next(Chunk.fromArray(data.getBytes(charset)), step.state, true)
              .map(a => a._1)
          case RequestContent.Binary(data)        =>
            step
              .asInstanceOf[ContentDecoder.Step[R, Throwable, Any, Chunk[Byte], B]]
              .next(data, step.state, true)
              .map(a => a._1)
          case RequestContent.BinaryN(data)       =>
            step
              .asInstanceOf[ContentDecoder.Step[R, Throwable, Any, Chunk[Byte], B]]
              .next(Chunk.fromArray(ByteBufUtil.getBytes(data)), step.state, true)
              .map(a => a._1)
          case RequestContent.BinaryStream(s)     =>
            s.fold(Unpooled.compositeBuffer())((s, b) => s.writeBytes(Array(b)))
              .map(_.array())
              .map(Chunk.fromArray(_))
              .flatMap(
                step
                  .asInstanceOf[ContentDecoder.Step[R, Throwable, Any, Chunk[Byte], B]]
                  .next(_, step.state, true)
                  .map(a => a._1),
              )
        }
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

  object Error {
    case object ContentDecodedOnce extends Error
    case object DecodeEmptyContent extends Error
  }
}
