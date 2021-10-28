package zhttp.http

import io.netty.buffer.{ByteBufUtil, Unpooled}
import zio.{Chunk, Queue, Task, UIO, ZIO}

sealed trait ContentDecoder[-R, +E, -A, +B] { self =>
  def decode(data: HttpData[Any, Throwable])(implicit ev: Chunk[Byte] <:< A): ZIO[R, Throwable, B] =
    ContentDecoder.decode(self.asInstanceOf[ContentDecoder[R, Throwable, Chunk[Byte], B]], data)

  def foldM[R1 <: R, A1 <: A, E1, B1](
                                       ee: E => ContentDecoder[R1, E1, A1, B1],
                                       bb: B => ContentDecoder[R1, E1, A1, B1],
                                     ): ContentDecoder[R1, E1, A1, B1] = ContentDecoder.FoldM(self, ee, bb)
  def flatMap[R1 <: R, E1 >: E, A1 <: A, C1](f: B => ContentDecoder[R1, E1, A1, C1]): ContentDecoder[R1, E1, A1, C1] = {
    self.foldM(ContentDecoder.fail, f)
  }
  def andThen[R1 <: R, E1 >: E, B1 >: B, C](other: ContentDecoder[R1, E1, B1, C]): ContentDecoder[R1, E1, A, C] =
    ContentDecoder.Chain(self, other)
  def map[C](bc: B => C): ContentDecoder[R, E, A, C] = self.flatMap(b => ContentDecoder.succeed(bc(b)))

}

object ContentDecoder {

  sealed trait DExit[-R, +E, -A, +B]
  object DExit {
    case object Text                                                                              extends DExit[Any, Nothing, Any, String]
    case class Step[R, E, S, A, B](state: S, next: (A, S, Boolean) => ZIO[R, E, (Option[B], S)])  extends DExit[R, E, A, B]
  }


  case object Text extends ContentDecoder[Any, Nothing, Any, String]
  case class Step[R, E, S, A, B](state: S, next: (A, S, Boolean) => ZIO[R, E, (Option[B], S)])
      extends ContentDecoder[R, E, A, B]
  private case object Identity              extends ContentDecoder[Any, Nothing, Any, Nothing]
  private final case class Succeed[B](b: B) extends ContentDecoder[Any, Nothing, Any, B]
  private final case class Fail[E](e: E)    extends ContentDecoder[Any, E, Any, Nothing]
  private final case class FromEffectFunction[R, E, A, B](f: A => ZIO[R, E, B]) extends ContentDecoder[R, E, A, B]
  private final case class Collect[R, E, A, B](ab: PartialFunction[A, B])       extends ContentDecoder[R, E, A, B]

  private final case class Chain[R, E, A, B, C](self: ContentDecoder[R, E, A, B], other: ContentDecoder[R, E, B, C])
    extends ContentDecoder[R, E, A, C]
  private final case class FoldM[R, E, EE, A, B, BB](
                                                      self: ContentDecoder[R, E, A, B],
                                                      ee: E => ContentDecoder[R, EE, A, BB],
                                                      bb: B => ContentDecoder[R, EE, A, BB],
                                                    ) extends ContentDecoder[R, EE, A, BB]

  def fail[E](e: E): ContentDecoder[Any, E, Any, Nothing] = ContentDecoder.Fail(e)

  def succeed[B](b: B): ContentDecoder[Any, Nothing, Any, B] = ContentDecoder.Succeed(b)

  final class FromFunction[A](val unit: Unit) extends AnyVal {
    def apply[B](f: A => B): ContentDecoder[Any, Nothing, A, B] = ContentDecoder.identity[A].map(f)
  }
  def identity[A]: ContentDecoder[Any, Nothing, A, A] = ContentDecoder.Identity

  def fromFunction[A]: FromFunction[A] = new FromFunction[A](())

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

  def collectAll[A]: ContentDecoder[Any, Nothing, A, Chunk[A]] = ContentDecoder.collect[Chunk[A], A](Chunk.empty) {
    case (a, chunk, true)  => UIO((Option(chunk :+ a), chunk))
    case (a, chunk, false) => UIO((None, chunk :+ a))
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

  private def decode[R, B](
    decoder: ContentDecoder[R, Throwable, Chunk[Byte], B],
    data: HttpData[Any, Throwable],
  ): ZIO[R, Throwable, B] =
    data match {
      case HttpData.Empty                => ZIO.fail(ContentDecoder.Error.DecodeEmptyContent)
      case HttpData.Text(data, charset)  =>
        decoder match {
          case Text                                     => UIO(data.asInstanceOf[B])
          case step: ContentDecoder.Step[_, _, _, _, _] =>
            step
              .asInstanceOf[ContentDecoder.Step[R, Throwable, Any, Chunk[Byte], B]]
              .next(Chunk.fromArray(data.getBytes(charset)), step.state, true)
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
                  .next(_, step.state, true)
                  .map(a => a._1)
                  .flatMap(contentFromOption),
              )
        }
      case HttpData.Binary(data)         =>
        decoder match {
          case Text                                     => UIO((new String(data.toArray, HTTP_CHARSET)).asInstanceOf[B])
          case step: ContentDecoder.Step[_, _, _, _, _] =>
            step
              .asInstanceOf[ContentDecoder.Step[R, Throwable, Any, Chunk[Byte], B]]
              .next(data, step.state, true)
              .map(a => a._1)
              .flatMap(contentFromOption)
        }
      case HttpData.BinaryN(data)        =>
        decoder match {
          case Text                                     => UIO(data.toString(HTTP_CHARSET).asInstanceOf[B])
          case step: ContentDecoder.Step[_, _, _, _, _] =>
            step
              .asInstanceOf[ContentDecoder.Step[R, Throwable, Any, Chunk[Byte], B]]
              .next(Chunk.fromArray(ByteBufUtil.getBytes(data)), step.state, true)
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
