package zhttp.http

import io.netty.buffer.{ByteBufUtil, Unpooled}
import zio.{Chunk, Queue, Task, UIO, ZIO}

sealed trait ContentDecoder[-R, +E, -A, +B] { self =>
  def decode(data: HttpData[Any, Throwable], method: Method, url: URL, headers: List[Header])(implicit
    ev: Chunk[Byte] <:< A,
  ): ZIO[R, Throwable, B] =
    ContentDecoder.decode(self.asInstanceOf[ContentDecoder[R, Throwable, Chunk[Byte], B]], data, method, url, headers)
}

object ContentDecoder {

  case object Text extends ContentDecoder[Any, Nothing, Any, String]

  case class Step[R, E, S, A, B](
    state: S,
    next: (A, S, Boolean, Method, URL, List[Header]) => ZIO[R, E, (Option[B], S)],
  ) extends ContentDecoder[R, E, A, B]

  private[zhttp] case class BackPressure[B](queue: Option[Queue[B]] = None, isFirst: Boolean = true) {
    self =>
    def withQueue(queue: Queue[B]): BackPressure[B] = if (self.queue.isEmpty) self.copy(queue = Option(queue)) else self
    def withFirst(cond: Boolean): BackPressure[B]   = if (cond == isFirst) self else self.copy(isFirst = cond)
  }

  val text: ContentDecoder[Any, Nothing, Any, String] = Text

  def collect[S, A]: PartiallyAppliedCollect[S, A] = new PartiallyAppliedCollect(())

  final class PartiallyAppliedCollect[S, A](val unit: Unit) extends AnyVal {
    def apply[R, E, B](s: S)(
      f: (A, S, Boolean, Method, URL, List[Header]) => ZIO[R, E, (Option[B], S)],
    ): ContentDecoder[R, E, A, B] = Step(s, f)
  }

  def collectAll[A]: ContentDecoder[Any, Nothing, A, Chunk[A]] = ContentDecoder.collect[Chunk[A], A](Chunk.empty) {
    case (a, chunk, true, _, _, _)  => UIO((Option(chunk :+ a), chunk))
    case (a, chunk, false, _, _, _) => UIO((None, chunk :+ a))
  }

  val backPressure: ContentDecoder[Any, Nothing, Chunk[Byte], Queue[Chunk[Byte]]] =
    ContentDecoder.collect(BackPressure[Chunk[Byte]]()) { case (msg, state, _, _, _, _) =>
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

  private def decode[R, B](
    decoder: ContentDecoder[R, Throwable, Chunk[Byte], B],
    data: HttpData[Any, Throwable],
    method: Method,
    url: URL,
    headers: List[Header],
  ): ZIO[R, Throwable, B] =
    data match {
      case HttpData.Empty                => ZIO.fail(ContentDecoder.Error.DecodeEmptyContent)
      case HttpData.Text(data, charset)  =>
        decoder match {
          case Text                                     => UIO(data.asInstanceOf[B])
          case step: ContentDecoder.Step[_, _, _, _, _] =>
            step
              .asInstanceOf[ContentDecoder.Step[R, Throwable, Any, Chunk[Byte], B]]
              .next(Chunk.fromArray(data.getBytes(charset)), step.state, true, method, url, headers)
              .map(a => a._1)
              .flatMap(contentFromOption)
        }
      case HttpData.BinaryStream(stream) =>
        decoder match {
          case Text                                     =>
            stream
              .fold(Unpooled.compositeBuffer())((s, b) => s.writeBytes(Array(b)))
              .map(b => b.toString(HTTP_CHARSET).asInstanceOf[B])
          case step: ContentDecoder.Step[_, _, _, _, _] =>
            stream
              .fold(Unpooled.compositeBuffer())((s, b) => s.writeBytes(Array(b)))
              .map(a => a.array().take(a.writerIndex()))
              .map(Chunk.fromArray(_))
              .flatMap(
                step
                  .asInstanceOf[ContentDecoder.Step[R, Throwable, Any, Chunk[Byte], B]]
                  .next(_, step.state, true, method, url, headers)
                  .map(a => a._1)
                  .flatMap(contentFromOption),
              )
        }
      case HttpData.BinaryChunk(data)         =>
        decoder match {
          case Text                                     => UIO((new String(data.toArray, HTTP_CHARSET)).asInstanceOf[B])
          case step: ContentDecoder.Step[_, _, _, _, _] =>
            step
              .asInstanceOf[ContentDecoder.Step[R, Throwable, Any, Chunk[Byte], B]]
              .next(data, step.state, true, method, url, headers)
              .map(a => a._1)
              .flatMap(contentFromOption)
        }
      case HttpData.BinaryByteBuf(data)        =>
        decoder match {
          case Text                                     => UIO(data.toString(HTTP_CHARSET).asInstanceOf[B])
          case step: ContentDecoder.Step[_, _, _, _, _] =>
            step
              .asInstanceOf[ContentDecoder.Step[R, Throwable, Any, Chunk[Byte], B]]
              .next(Chunk.fromArray(ByteBufUtil.getBytes(data)), step.state, true, method, url, headers)
              .map(a => a._1)
              .flatMap(contentFromOption)
        }
    }
  private def contentFromOption[B](a: Option[B]): Task[B] = {
    a match {
      case Some(value) => ZIO(value)
      case None        => ZIO.fail(ContentDecoder.Error.DecodeEmptyContent)
    }
  }

  object Error {
    case object ContentDecodedOnce extends Error
    case object DecodeEmptyContent extends Error
  }
}
