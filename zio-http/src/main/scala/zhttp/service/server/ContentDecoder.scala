package zhttp.service.server

import io.netty.buffer.{ByteBuf, Unpooled}
import zhttp.http.{HTTP_CHARSET, Headers, HttpData, Method, URL}
import zio.{Chunk, Queue, Task, UIO, ZIO}

sealed trait ContentDecoder[-R, +E, -A, +B] { self =>
  def decode(data: HttpData, method: Method, url: URL, headers: Headers)(implicit
    ev: ByteBuf <:< A,
  ): ZIO[R, Throwable, B] =
    ContentDecoder.decode(self.asInstanceOf[ContentDecoder[R, Throwable, ByteBuf, B]], data, method, url, headers)
}

object ContentDecoder {

  def backPressure: ContentDecoder[Any, Nothing, ByteBuf, Queue[ByteBuf]] =
    ContentDecoder.collect(BackPressure[ByteBuf]()) { case (msg, state, _, _, _, _) =>
      for {
        queue <- state.queue.fold(Queue.bounded[ByteBuf](1))(UIO(_))
        _     <- queue.offer(msg)
      } yield (if (state.isFirst) Option(queue) else None, state.withQueue(queue).withFirst(false))
    }

  def collect[S, A]: PartiallyAppliedCollect[S, A] = new PartiallyAppliedCollect(())

  def collectAll[A]: ContentDecoder[Any, Nothing, A, Chunk[A]] =
    ContentDecoder.collect[Chunk[A], A](Chunk.empty) {
      case (a, chunk, true, _, _, _)  => UIO((Option(chunk :+ a), chunk))
      case (a, chunk, false, _, _, _) => UIO((None, chunk :+ a))
    }

  def text: ContentDecoder[Any, Nothing, Any, String] = Text

  private def contentFromOption[B](a: Option[B]): Task[B] = {
    a match {
      case Some(value) => ZIO(value)
      case None        => ZIO.fail(ContentDecoder.Error.DecodeEmptyContent)
    }
  }

  private def decode[R, B](
    decoder: ContentDecoder[R, Throwable, ByteBuf, B],
    data: HttpData,
    method: Method,
    url: URL,
    headers: Headers,
  ): ZIO[R, Throwable, B] =
    data match {
      case HttpData.Empty                => ZIO.fail(ContentDecoder.Error.DecodeEmptyContent)
      case HttpData.Text(data, charset)  =>
        decoder match {
          case Text                                     => UIO(data.asInstanceOf[B])
          case step: ContentDecoder.Step[_, _, _, _, _] =>
            step
              .asInstanceOf[ContentDecoder.Step[R, Throwable, Any, ByteBuf, B]]
              .next(Unpooled.wrappedBuffer(data.getBytes(charset)), step.state, true, method, url, headers)
              .map(a => a._1)
              .flatMap(contentFromOption)
        }
      case HttpData.BinaryStream(stream) =>
        decoder match {
          case Text                                     =>
            stream
              .fold(Unpooled.compositeBuffer())((s, b) => s.writeBytes(b))
              .map(b => b.toString(HTTP_CHARSET).asInstanceOf[B])
          case step: ContentDecoder.Step[_, _, _, _, _] =>
            stream
              .fold(Unpooled.compositeBuffer())((s, b) => s.writeBytes(b))
              .map(a => a.writerIndex(a.writerIndex()))
              .flatMap(
                step
                  .asInstanceOf[ContentDecoder.Step[R, Throwable, Any, ByteBuf, B]]
                  .next(_, step.state, true, method, url, headers)
                  .map(a => a._1)
                  .flatMap(contentFromOption),
              )
        }
      case HttpData.BinaryChunk(data)    =>
        decoder match {
          case Text                                     => UIO((new String(data.toArray, HTTP_CHARSET)).asInstanceOf[B])
          case step: ContentDecoder.Step[_, _, _, _, _] =>
            step
              .asInstanceOf[ContentDecoder.Step[R, Throwable, Any, ByteBuf, B]]
              .next(Unpooled.wrappedBuffer(data.toArray), step.state, true, method, url, headers)
              .map(a => a._1)
              .flatMap(contentFromOption)
        }
      case HttpData.BinaryByteBuf(data)  =>
        decoder match {
          case Text                                     => UIO(data.toString(HTTP_CHARSET).asInstanceOf[B])
          case step: ContentDecoder.Step[_, _, _, _, _] =>
            step
              .asInstanceOf[ContentDecoder.Step[R, Throwable, Any, ByteBuf, B]]
              .next(data, step.state, true, method, url, headers)
              .map(a => a._1)
              .flatMap(contentFromOption)
        }
      case _                             => ZIO.fail(ContentDecoder.Error.UnsupportedContent)
    }

  sealed trait Error extends Throwable with Product { self =>
    override def getMessage(): String =
      self match {
        case Error.ContentDecodedOnce => "Content has already been decoded once."
        case Error.DecodeEmptyContent => "Can not decode empty content"
        case Error.UnsupportedContent => "Unsupported content"
      }
  }

  final class PartiallyAppliedCollect[S, A](val unit: Unit) extends AnyVal {
    def apply[R, E, B](s: S)(
      f: (A, S, Boolean, Method, URL, Headers) => ZIO[R, E, (Option[B], S)],
    ): ContentDecoder[R, E, A, B] = Step(s, f)
  }

  private[zhttp] final case class Step[R, E, S, A, B](
    state: S,
    next: (A, S, Boolean, Method, URL, Headers) => ZIO[R, E, (Option[B], S)],
  ) extends ContentDecoder[R, E, A, B]

  private[zhttp] case class BackPressure[B](queue: Option[Queue[B]] = None, isFirst: Boolean = true) {
    self =>
    def withFirst(cond: Boolean): BackPressure[B] = if (cond == isFirst) self else self.copy(isFirst = cond)

    def withQueue(queue: Queue[B]): BackPressure[B] = if (self.queue.isEmpty) self.copy(queue = Option(queue)) else self
  }

  object Error {
    case object ContentDecodedOnce extends Error
    case object DecodeEmptyContent extends Error
    case object UnsupportedContent extends Error
  }

  private[zhttp] case object Text extends ContentDecoder[Any, Nothing, Any, String]
}
