package zio.http.api.internal

import zio.http.api.{Combiner, Doc}
import zio.{Chunk, NonEmptyChunk}

import java.lang.Integer.parseInt
import java.lang.StringBuilder
import scala.annotation.tailrec
import scala.collection.immutable.BitSet
import scala.util.control.NoStackTrace

/**
 * A `RichTextCodec` is a more compositional version of `TextCodec`, which has
 * similar power to traditional parser combinators / pretty printers. Although
 * slower than the simpler text codecs, they can be utilized to parse structured
 * information in HTTP headers, which in turn allows generating much better
 * error messages and documentation than otherwise possible.
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
  final def ~[B](that: => RichTextCodec[B])(implicit combiner: Combiner[A, B]): RichTextCodec[combiner.Out] =
    RichTextCodec.Zip(self, RichTextCodec.defer(that), combiner)

  /**
   * Returns a new codec that is the fallback composition of this codec and the
   * specified codec, preferring this codec, but falling back to the specified
   * codec in the event of failure.
   */
  final def |[B](that: => RichTextCodec[B]): RichTextCodec[Either[A, B]] =
    RichTextCodec.Alt(self, RichTextCodec.defer(that))

  /**
   * Tranforms this constant unit codec to a constant codec of another type.
   */
  final def as[B](b: => B)(implicit ev: A =:= Unit): RichTextCodec[B] =
    self.asType[Unit].transform(_ => b, _ => ())

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

  final def decode(value: CharSequence): Either[String, A] =
    RichTextCodec.parse(value, self).map(_._2)

  /**
   * Constructs documentation for this rich text codec.
   */
  final def describe: Doc = Doc.p(Doc.Span.code(RichTextCodec.describe(self)))

  /**
   * Tags the codec with a label used in the documentation
   */
  final def @@(label: String): RichTextCodec.Tagged[A] = RichTextCodec.Tagged(label, self)

  /**
   * Tags the codec with a label used in the documentation. The label will be
   * used but not explained
   */
  final def @!(label: String): RichTextCodec.Tagged[A] = RichTextCodec.Tagged(label, self, descriptionNotNeeded = true)

  /**
   * Encodes a value into a string, or if this is not possible, fails with an
   * error message.
   */
  final def encode(value: A): Either[String, String] = RichTextCodec.encode(value, self)

  final def optional(default: A): RichTextCodec[Option[A]] =
    self.transform(a => Some(a), { case None => default; case Some(a) => a })

  lazy val repeat: RichTextCodec[Chunk[A]] =
    ((self ~ repeat).transform[NonEmptyChunk[A]](
      t => NonEmptyChunk(t._1, t._2: _*),
      c => (c.head, c.tail),
    ) | RichTextCodec.empty.as(Chunk.empty[A]))
      .transform[Chunk[A]](
        _ match {
          case Left(nonEmpty)    => nonEmpty
          case Right(maybeEmpty) => maybeEmpty
        },
        c => c.nonEmptyOrElse[Either[NonEmptyChunk[A], Chunk[A]]](Right(c))(Left(_)),
      )

  final def singleton: RichTextCodec[NonEmptyChunk[A]] =
    self.transform(a => NonEmptyChunk(a), _.head)

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
  private[internal] final case class CharIn(set: BitSet)                     extends RichTextCodec[Char]
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

  private[internal] final case class Tagged[A](
    name: String,
    codec: RichTextCodec[A],
    descriptionNotNeeded: Boolean = false,
  ) extends RichTextCodec[A]

  /**
   * Returns a lazy codec, which can be used to define recursive codecs.
   */
  def defer[A](codec: => RichTextCodec[A]): RichTextCodec[A] =
    Lazy(() => codec)

  /**
   * A codec that describes a single specified character.
   */
  def char(c: Char): RichTextCodec[Char] = CharIn(BitSet(c.toInt))

  /**
   * A codec that describes a digit character.
   */
  val digit: RichTextCodec[Int] =
    filter(c => c >= '0' && c <= '9').transform[Int](c => parseInt(c.toString), x => x.toString.head)

  /**
   * A codec that describes nothing at all. Such codecs successfully decode even
   * on empty input, and when encoded, do not produce any text output.
   */
  val empty: RichTextCodec[Unit] = Empty

  /**
   * Defines a new codec for a single character based on the specified
   * predicate.
   */
  def filter(pred: Char => Boolean): RichTextCodec[Char] =
    CharIn(BitSet((Char.MinValue to Char.MaxValue).filter(pred).map(_.toInt): _*))

  /**
   * A codec that describes a letter character.
   */
  val letter: RichTextCodec[Char] = filter(_.isLetter) @! "letter"

  /**
   * A codec that describes a literal character sequence.
   */
  def literal(lit: String): RichTextCodec[String] = {
    def loop(list: List[Char]): RichTextCodec[Unit] =
      list match {
        case head :: tail =>
          char(head).unit(head) ~> loop(tail)
        case Nil          => empty
      }

    loop(lit.toList).as(lit)
  }

  /**
   * A codec that describes a literal character sequence, ignoring case.
   */
  def literalCI(lit: String): RichTextCodec[String] = {
    def loop(list: List[Char]): RichTextCodec[Unit] =
      list match {
        case head :: tail =>
          CharIn(BitSet(head.toUpper.toInt, head.toLower.toInt)).unit(head) ~> loop(tail)
        case Nil          => empty
      }

    loop(lit.toList).as(lit)
  }

  /**
   * A codec that describes any number of whitespace characters.
   */
  lazy val whitespaces: RichTextCodec[Unit] =
    whitespaceChar.repeat.transform(_ => (), _ => Chunk.empty)

  /**
   * A codec that describes a single whitespace character.
   */
  lazy val whitespaceChar: RichTextCodec[Unit] = filter(_.isWhitespace).unit(' ')

  private def describe[A](codec: RichTextCodec[A]): String = {
    // Identifies cycles and names the anonymous cyclic ones
    def findCycles(
      seen: Set[RichTextCodec[_]],
      lastAnonymous: Int,
      tags: Map[RichTextCodec[_], Tagged[_]],
      codec: RichTextCodec[_],
    ): (Map[RichTextCodec[_], Tagged[_]], Int) = {
      if (seen(codec)) {
        (codec, tags.get(codec)) match {
          case (_, Some(_))            => (tags, lastAnonymous)
          case (Tagged(_, _, _), None) => (tags, lastAnonymous)
          case (_, None)               => (Map(codec -> Tagged(s"${lastAnonymous + 1}", codec)), lastAnonymous + 1)
        }
      } else
        codec match {
          case Empty | CharIn(_)        => (tags, lastAnonymous)
          case TransformOrFail(c, _, _) =>
            val res = findCycles(seen + codec, lastAnonymous, tags, c)
            (res._1 ++ res._1.get(c).map(t => codec -> t), res._2)
          case Alt(left, right)         =>
            val lc = findCycles(seen + codec, lastAnonymous, tags, left)
            findCycles(seen + codec, lc._2, tags ++ lc._1, right)
          case l @ Lazy(_)              =>
            val res = findCycles(seen + codec, lastAnonymous, tags, l.codec)
            (res._1 ++ res._1.get(l.codec).map(t => codec -> t), res._2)
          case Zip(left, right, _)      =>
            val lc = findCycles(seen + codec, lastAnonymous, tags, left)
            findCycles(seen + codec, lc._2, tags ++ lc._1, right)
          case t @ Tagged(_, c, _)      =>
            @tailrec def addTag(
              tags: Map[RichTextCodec[_], Tagged[_]],
              c: RichTextCodec[_],
            ): Map[RichTextCodec[_], Tagged[_]] =
              c match {
                case l @ Lazy(_)                  => addTag(tags + (c -> t), l.codec)
                case TransformOrFail(codec, _, _) => addTag(tags + (c -> t), codec)
                case _                            => tags + (c -> t)
              }
            findCycles(seen + codec, lastAnonymous, addTag(tags, c), c)
        }
    }

    val (cycles, _) = findCycles(Set.empty, 0, Map.empty, codec)
    final case class PartialDescription(
      description: String,
      taggedToDescribe: List[Tagged[_]] = Nil,
    )

    @tailrec
    def isAltInParens(codec: RichTextCodec[_]): Boolean =
      codec match {
        case Alt(_, _)                                          => true
        case Empty | CharIn(_) | Zip(_, _, _) | Tagged(_, _, _) => false
        case TransformOrFail(codec, _, _)                       => isAltInParens(codec)
        case Lazy(codec0)                                       => isAltInParens(codec0())
      }

    def loop(
      main: RichTextCodec[_],
      explaining: RichTextCodec[_],
      codec: RichTextCodec[_],
      namesSeen: Set[String],
    ): PartialDescription = {

      cycles.get(codec) match {
        case Some(tagged) if tagged != explaining => loop(main, explaining, tagged, namesSeen)
        case _                                    =>
          codec match {
            case Empty       => PartialDescription("")
            case CharIn(set) =>
              val firstChunk = if (set('-')) Chunk[(Int, Int)](('-', '-')) else Chunk.empty[(Int, Int)]
              val tuple      = set.filterNot(_ == '-').foldLeft((firstChunk, (-1, -1))) { case ((acc, (min, max)), c) =>
                if (min == -1) (acc, (c, c))
                else if (c == max + 1) (acc, (min, c))
                else (acc :+ ((min, max)), (c, c))
              }

              val finalElement = if (tuple._2 == ((-1, -1))) Chunk.empty else Chunk(tuple._2)

              val chunk = (tuple._1 ++ finalElement).map { case (min, max) =>
                if (min == max) min.toChar.toString
                else if (max == min + 1) s"${min.toChar}${max.toChar}"
                else s"${min.toChar}-${max.toChar}"
              }
              PartialDescription(
                if (chunk.isEmpty) ""
                else if (chunk.length == 1 && chunk.head.length == 1) chunk.mkString("")
                else chunk.mkString("[", "", "]"),
              )

            case Alt(left, right)             =>
              val leftDescription  = loop(main, explaining, left, namesSeen)
              val rightDescription =
                loop(main, explaining, right, namesSeen ++ leftDescription.taggedToDescribe.map(_.name))
              PartialDescription(
                s"${leftDescription.description} | ${rightDescription.description}",
                leftDescription.taggedToDescribe ++ rightDescription.taggedToDescribe,
              )
            case Lazy(codec0)                 => loop(main, explaining, codec0(), namesSeen)
            case Zip(left, right, _)          =>
              val l                = cycles.getOrElse(left, left)
              val r                = cycles.getOrElse(right, right)
              val leftDescription  = loop(main, explaining, l, namesSeen)
              val rightDescription =
                loop(main, explaining, r, namesSeen ++ leftDescription.taggedToDescribe.map(_.name))
              val d                =
                (isAltInParens(l), isAltInParens(r)) match {
                  case (false, false) => s"${leftDescription.description}${rightDescription.description}"
                  case (false, true)  => s"${leftDescription.description}(${rightDescription.description})"
                  case (true, false)  => s"(${leftDescription.description})${rightDescription.description}"
                  case (true, true)   => s"(${leftDescription.description})(${rightDescription.description})"
                }

              PartialDescription(d, leftDescription.taggedToDescribe ++ rightDescription.taggedToDescribe)
            case TransformOrFail(codec, _, _) => loop(main, explaining, codec, namesSeen)
            case t @ Tagged(name, c, false) if main == t && explaining != t =>
              val pd = loop(main, t, c, namesSeen)
              pd.copy(description = s"<$name> ::= ${pd.description}")
            case t @ Tagged(name, _, false) if !namesSeen(name)             => PartialDescription(s"<$name>", List(t))
            case Tagged(name, _, _)                                         => PartialDescription(s"<$name>")
          }
      }
    }

    val lines =
      LazyList.unfold[String, (List[RichTextCodec[_]], Set[String])](
        codec match {
          case Tagged(name, c, false) => (List(c), Set(name))
          case c                      => (List(c), cycles.get(c).map(t => Set(t.name)).getOrElse(Set.empty))
        },
      ) {
        case (Nil, _)            => None
        case (h :: t, namesSeen) =>
          val partialDescription = loop(cycles.getOrElse(h, h), Empty, h, namesSeen)

          Some(
            partialDescription.description -> (t ++ partialDescription.taggedToDescribe,
            namesSeen ++ partialDescription.taggedToDescribe.map(_.name)),
          )
      }

    lines.mkString("\n")
  }

  private def encode[A](value: A, self: RichTextCodec[A]): Either[String, String] = {
    self match {
      case RichTextCodec.Empty                           => Right("")
      case RichTextCodec.CharIn(_)                       => { Right(value.asInstanceOf[Char].toString) }
      case RichTextCodec.TransformOrFail(codec, _, from) =>
        from(value) match {
          case Left(err)     => Left(err)
          case Right(value2) =>
            codec.encode(value2)
        }
      case RichTextCodec.Alt(left, right)                =>
        value match {
          case Left(a)  => left.encode(a)
          case Right(b) => right.encode(b)
        }
      case RichTextCodec.Lazy(codec0)                    => codec0().encode(value)
      case RichTextCodec.Zip(left, right, combiner)      => {
        val (a, b) = combiner.separate(value)
        for {
          l <- left.encode(a)
          r <- right.encode(b)
        } yield l + r
      }
      case RichTextCodec.Tagged(_, codec, _)             => codec.encode(value)
    }
  }

  private def parse[A](value: CharSequence, self: RichTextCodec[A]): Either[String, (CharSequence, A)] =
    self match {
      case Empty =>
        Right((value, ()))

      case CharIn(bitset) =>
        if (value.length == 0 || !bitset.contains(value.charAt(0).toInt))
          Left(s"Not found: ${bitset.toArray.map(_.toChar).mkString}")
        else
          Right((value.subSequence(1, value.length), value.charAt(0)))

      case TransformOrFail(codec, to, _) =>
        parse(value, codec).flatMap { case (rest, a0) => to(a0).map(a => (rest, a)) }

      case Alt(left, right) =>
        parse(value, left) match {
          case Right((rest, a)) => Right((rest, Left(a)))
          case Left(errorLeft)  =>
            parse(value, right) match {
              case Right((rest, b)) => Right((rest, Right(b)))
              case Left(errorRight) => Left(s"($errorLeft, $errorRight)")
            }
        }

      case RichTextCodec.Lazy(codec0) =>
        parse(value, codec0())

      case RichTextCodec.Zip(left, right, combiner) =>
        for {
          l <- parse(value, left)
          r <- parse(l._1, right)
        } yield (r._1, combiner.combine(l._2, r._2))

      case RichTextCodec.Tagged(_, codec, _) => parse(value, codec)
    }

}
