package zio.http.api.internal

import zio.Chunk
import zio.http.api.Combiner

import java.lang.StringBuilder
import scala.collection.immutable.BitSet
import scala.util.control.NoStackTrace

/**
 * A [[RichTextCodec]] is a more compositional version of [[TextCodec]], which
 * has similar power to traditional parser combinators / pretty printers.
 * Although slower than the simpler text codecs, they can be utilized to parse
 * structured information in HTTP headers, which in turn allows generating much
 * better error messages and documentation than otherwise possible.
 */
sealed trait RichTextCodec[A] { self =>

  /**
   * Returns a new codec that is the sequential composition of this codec and
   * the specified codec, but which only produces the value of this codec.
   */
  final def <~(that: => RichTextCodec[Unit]): RichTextCodec[A] =
    self ~ RichTextCodec.defer(that)

  /**
   * Returns a new codec that is the sequential composition of this codec and
   * the specified codec, but which only produces the value of that codec.
   */
  final def ~>[B](that: => RichTextCodec[B])(implicit ev: A =:= Unit): RichTextCodec[B] =
    self.asType[Unit] ~ RichTextCodec.defer(that)

  /**
   * Returns a new codec that is the sequential composition of this codec and
   * the specified codec, producing the values of both as a tuple.
   */
  final def ~[B](that: RichTextCodec[B])(implicit combiner: Combiner[A, B]): RichTextCodec[combiner.Out] =
    RichTextCodec.Zip(self, RichTextCodec.defer(that), combiner)

  /**
   * Returns a new codec that is the fallback composition of this codec and the
   * specified codec, preferring this codec, but falling back to the specified
   * codec in the event of failure.
   */
  final def |[B](that: => RichTextCodec[B]): RichTextCodec[Either[A, B]] =
    RichTextCodec.Alt(self, RichTextCodec.defer(that))

  final def asType[B](implicit ev: A =:= B): RichTextCodec[B] =
    self.asInstanceOf[RichTextCodec[B]]

  final def collectOrFail(failure: String)(pf: PartialFunction[A, A]): RichTextCodec[A] =
    transformOrFailLeft[A](
      {
        case x if pf.isDefinedAt(x) => Right(pf(x))
        case _                      => Left(failure)
      },
      a => a,
    )

  // TODO
  final def encode(value: A): String = ???

  // TODO
  final def decode(value: CharSequence): Either[String, A] = ???

  // TODO
  final def describe: zio.http.api.Doc = ???

  final def optional(default: A): RichTextCodec[Option[A]] =
    self.transform(a => Some(a), { case None => default; case Some(a) => a })

  final def repeat: RichTextCodec[Chunk[A]] =
    (self ~ repeat).transformOrFailRight(
      t => Chunk(t._1) ++ t._2,
      c =>
        c.headOption match {
          case None       => Left("Expected an element but found an empty chunk")
          case Some(head) => Right((head, c.drop(1)))
        },
    )

  final def transform[B](f: A => B, g: B => A): RichTextCodec[B] =
    self.transformOrFail[B](a => Right(f(a)), b => Right(g(b)))

  final def transformOrFail[B](f: A => Either[String, B], g: B => Either[String, A]): RichTextCodec[B] =
    RichTextCodec.TransformOrFail(self, f, g)

  final def transformOrFailLeft[B](f: A => Either[String, B], g: B => A): RichTextCodec[B] =
    self.transformOrFail[B](f, b => Right(g(b)))

  final def transformOrFailRight[B](f: A => B, g: B => Either[String, A]): RichTextCodec[B] =
    self.transformOrFail[B](a => Right(f(a)), g)

  /**
   * Converts this codec of `A` into a codec of `Unit` by specifying a canonical
   * value to use when an HTTP client needs to generate a value for this codec.
   */
  final def unit(canonical: A): RichTextCodec[Unit] =
    self.transform[Unit](_ => (), _ => canonical)

  /**
   * Attempts to validate a decoded value, or fails using the specified failure
   * message.
   */
  final def validate(failure: String)(p: A => Boolean): RichTextCodec[A] =
    collectOrFail(failure) {
      case x if p(x) => x
    }
}
object RichTextCodec {
  private[internal] case object Empty                                        extends RichTextCodec[Unit]
  private[internal] final case class CharIn(set: BitSet)                     extends RichTextCodec[Unit]
  private[internal] final case class TransformOrFail[A, B](
    codec: RichTextCodec[A],
    to: A => Either[String, B],
    from: B => Either[String, A],
  ) extends RichTextCodec[B]
  private[internal] final case class Alt[A, B](left: RichTextCodec[A], right: RichTextCodec[B])
      extends RichTextCodec[Either[A, B]]
  private[internal] final case class Lazy[A](codec0: () => RichTextCodec[A]) extends RichTextCodec[A] {
    lazy val codec: RichTextCodec[A] = codec0()
  }
  private[internal] final case class Zip[A, B, C](
    left: RichTextCodec[A],
    right: RichTextCodec[B],
    combiner: Combiner.WithOut[A, B, C],
  ) extends RichTextCodec[C]

  /**
   * Returns a lazy codec, which can be used to define recursive codecs.
   */
  def defer[A](codec: => RichTextCodec[A]): RichTextCodec[A] =
    Lazy(() => codec)

  /**
   * A codec that describes a single specified character.
   */
  def char(c: Char): RichTextCodec[Unit] = CharIn(BitSet(c.toInt))

  /**
   * A codec that describes a digit character.
   */
  val digit: RichTextCodec[Unit] = filter(_.isDigit)

  /**
   * A codec that describes nothing at all. Such codecs successfully decode even
   * on empty input, and when encoded, do not produce any text output.
   */
  val empty: RichTextCodec[Unit] = Empty

  /**
   * Defines a new codec for a single character based on the specified
   * predicate.
   */
  def filter(pred: Char => Boolean): RichTextCodec[Unit] =
    CharIn(BitSet((Char.MinValue to Char.MaxValue).map(_.toInt): _*))

  /**
   * A codec that describes a letter character.
   */
  val letter: RichTextCodec[Unit] = filter(_.isLetter)

  /**
   * A codec that describes a literal character sequence.
   */
  def literal(lit: CharSequence): RichTextCodec[Unit] =
    lit.toString().foldLeft(empty) { case (acc, c) =>
      acc ~> char(c)
    }

  /**
   * A codec that describes any number of whitespace characters.
   */
  lazy val whitespaces: RichTextCodec[Unit] =
    whitespaceChar.repeat.transform(_ => (), _ => Chunk.empty)

  /**
   * A codec that describes a single whitespace character.
   */
  lazy val whitespaceChar: RichTextCodec[Unit] = filter(_.isWhitespace)
}
