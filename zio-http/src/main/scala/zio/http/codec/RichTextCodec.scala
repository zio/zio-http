/*
 * Copyright 2021 - 2023 Sporta Technologies PVT LTD & the ZIO HTTP contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.http.codec

import java.lang.Integer.parseInt

import scala.annotation.tailrec
import scala.collection.immutable.BitSet

import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.{Chunk, NonEmptyChunk}

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
    self.asType[Unit].transform(_ => b)(_ => ())

  final def asType[B](implicit ev: A =:= B): RichTextCodec[B] =
    self.asInstanceOf[RichTextCodec[B]]

  final def collectOrFail(failure: String)(pf: PartialFunction[A, A]): RichTextCodec[A] =
    transformOrFailLeft[A] {
      case x if pf.isDefinedAt(x) => Right(pf(x))
      case _                      => Left(failure)
    }(identity)

  final def decode(value: CharSequence): Either[String, A] =
    RichTextCodec.parse(value, self).map(_._2)

  /**
   * Constructs documentation for this rich text codec.
   */
  final def describe: Doc = Doc.p(Doc.Span.code(RichTextCodec.describe(self)))

  /**
   * Tags the codec with a label used in the documentation
   */
  final def ??(label: String): RichTextCodec.Tagged[A]     = tagged(label)
  final def tagged(label: String): RichTextCodec.Tagged[A] = RichTextCodec.Tagged(label, self)

  /**
   * Tags the codec with a label used in the documentation. The label will be
   * used but not explained
   */
  final def ?!(label: String): RichTextCodec.Tagged[A]                = taggedUnexplained(label)
  final def taggedUnexplained(label: String): RichTextCodec.Tagged[A] =
    RichTextCodec.Tagged(label, self, descriptionNotNeeded = true)

  /**
   * Encodes a value into a string, or if this is not possible, fails with an
   * error message.
   */
  final def encode(value: A): Either[String, String] = RichTextCodec.encode(value, self)

  final def optional(default: A): RichTextCodec[Option[A]] =
    self.transform[Option[A]](a => Some(a))(_.fold(default)(identity))

  lazy val repeat: RichTextCodec[Chunk[A]] =
    ((self ~ repeat).transform[NonEmptyChunk[A]](t => NonEmptyChunk(t._1, t._2: _*))(c =>
      (c.head, c.tail),
    ) | RichTextCodec.empty.as(Chunk.empty[A]))
      .transform[Chunk[A]](_ match {
        case Left(nonEmpty)    => nonEmpty
        case Right(maybeEmpty) => maybeEmpty
      })(c => c.nonEmptyOrElse[Either[NonEmptyChunk[A], Chunk[A]]](Right(c))(Left(_)))

  final def singleton: RichTextCodec[NonEmptyChunk[A]] =
    self.transform(a => NonEmptyChunk(a))(_.head)

  final def transform[B](f: A => B)(g: B => A): RichTextCodec[B] =
    self.transformOrFail[B](a => Right(f(a)))(b => Right(g(b)))

  final def transformOrFail[B](f: A => Either[String, B])(g: B => Either[String, A]): RichTextCodec[B] =
    RichTextCodec.TransformOrFail(self, f, g)

  final def transformOrFailLeft[B](f: A => Either[String, B])(g: B => A): RichTextCodec[B] =
    self.transformOrFail[B](f)(b => Right(g(b)))

  final def transformOrFailRight[B](f: A => B)(g: B => Either[String, A]): RichTextCodec[B] =
    self.transformOrFail[B](a => Right(f(a)))(g)

  /**
   * Converts this codec of `A` into a codec of `Unit` by specifying a canonical
   * value to use when an HTTP client needs to generate a value for this codec.
   */
  final def const(canonical: A): RichTextCodec[Unit] =
    self.transform[Unit](_ => ())(_ => canonical)

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
  private[codec] case object Empty                                        extends RichTextCodec[Unit]
  private[codec] final case class CharIn(set: BitSet)                     extends RichTextCodec[Char]
  private[codec] final case class TransformOrFail[A, B](
    codec: RichTextCodec[A],
    to: A => Either[String, B],
    from: B => Either[String, A],
  ) extends RichTextCodec[B]
  private[codec] final case class Alt[A, B](left: RichTextCodec[A], right: RichTextCodec[B])
      extends RichTextCodec[Either[A, B]]
  private[codec] final case class Lazy[A](codec0: () => RichTextCodec[A]) extends RichTextCodec[A] {
    lazy val codec: RichTextCodec[A] = codec0()
  }
  private[codec] final case class Zip[A, B, C](
    left: RichTextCodec[A],
    right: RichTextCodec[B],
    combiner: Combiner.WithOut[A, B, C],
  ) extends RichTextCodec[C]

  private[codec] final case class Tagged[A](
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
    filter(c => c >= '0' && c <= '9').transform(c => parseInt(c.toString))(x => x.toString.head)

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
  val letter: RichTextCodec[Char] = filter(_.isLetter) ?! "letter"

  /**
   * A codec that describes a literal character sequence.
   */
  def literal(lit: String): RichTextCodec[String] = {
    def loop(list: List[Char]): RichTextCodec[Unit] =
      list match {
        case head :: tail =>
          char(head).const(head) ~> loop(tail)
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
          CharIn(BitSet(head.toUpper.toInt, head.toLower.toInt)).const(head) ~> loop(tail)
        case Nil          => empty
      }

    loop(lit.toList).as(lit)
  }

  /**
   * A codec that describes any number of whitespace characters.
   */
  lazy val whitespaces: RichTextCodec[Unit] =
    whitespaceChar.repeat.transform(_ => ())(_ => Chunk.empty)

  /**
   * A codec that describes a single whitespace character.
   */
  lazy val whitespaceChar: RichTextCodec[Unit] = filter(_.isWhitespace).const(' ')

  private def describe[A](codec: RichTextCodec[A]): String = {

    sealed trait DocPart
    object DocPart {

      private val escapedChars: Map[Char, String] =
        "“”[]\\\"".map(c => c -> s"\\$c").toMap ++ Map(
          '\t' -> "\\t",
          '\b' -> "\\b",
          '\r' -> "\\r",
          '\n' -> "\\n",
          '\f' -> "\\f",
        )

      object Empty                                       extends DocPart {
        override def toString: String = "«empty»"
      }
      final case class Literal(ranges: List[CharRanges]) extends DocPart {
        override def toString: String = s"“${ranges.mkString}”"
      }

      final case class CharRange(from: Char, to: Char) extends DocPart {
        override def toString: String = if (from == to) escapedChars.getOrElse(from, from.toString)
        else if (from + 1 == to)
          s"${escapedChars.getOrElse(from, from.toString)}${escapedChars.getOrElse(to, to.toString)}"
        else s"${escapedChars.getOrElse(from, from.toString)}-${escapedChars.getOrElse(to, to.toString)}"
      }

      final case class CharRanges(ranges: List[CharRange]) extends DocPart {
        override def toString: String = ranges match {
          case CharRange(from, to) :: Nil if from == to => escapedChars.getOrElse(from, from.toString)
          case _                                        => s"[${ranges.map(_.toString).mkString}]"
        }
      }

      final case class Ref(name: String)              extends DocPart {
        override def toString: String = s"«$name»"
      }
      final case class Sequence(parts: List[DocPart]) extends DocPart {

        override def toString: String =
          parts.map {
            case a: Alternatives => s"($a)"
            case p               => p.toString
          }.mkString(" ")

      }
      final case class Alternatives(parts: List[DocPart])    extends DocPart {
        override def toString: String = parts.map(_.toString).mkString(" | ")
      }
      final case class Optional(it: DocPart)                 extends DocPart {
        override def toString: String =
          it match {
            case _: Alternatives | _: Sequence => s"($it)?"
            case _                             => s"$it?"
          }
      }
      final case class Defintion(name: String, doc: DocPart) extends DocPart {
        override def toString: String = s"«$name» ⩴ $doc"
      }
    }

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
      description: DocPart,
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

    def explain(tagged: Tagged[_], namesSeen: Set[String]): PartialDescription = {
      val pd = loop(tagged.codec, namesSeen + tagged.name, tagged)
      pd.copy(description = DocPart.Defintion(tagged.name, pd.description))
    }

    def loop(
      codec: RichTextCodec[_],
      namesSeen: Set[String],
      explaining: RichTextCodec[_],
      seen: Boolean = false,
    ): PartialDescription = {

      cycles.get(codec) match {
        case Some(tagged) if explaining != tagged || seen => loop(tagged, namesSeen, explaining, seen)
        case _                                            =>
          codec match {
            case Empty       => PartialDescription(DocPart.Empty)
            case CharIn(set) =>
              val firstChunk = if (set('-')) Chunk[(Int, Int)](('-', '-')) else Chunk.empty[(Int, Int)]
              val tuple      = set.filterNot(_ == '-').foldLeft((firstChunk, (-1, -1))) { case ((acc, (min, max)), c) =>
                if (min == -1) (acc, (c, c))
                else if (c == max + 1) (acc, (min, c))
                else (acc :+ ((min, max)), (c, c))
              }

              val finalElement = if (tuple._2 == ((-1, -1))) Chunk.empty else Chunk(tuple._2)

              val chunk: Chunk[DocPart.CharRange] = (tuple._1 ++ finalElement).map { case (min, max) =>
                DocPart.CharRange(min.toChar, max.toChar)
              }
              PartialDescription(
                DocPart.CharRanges(
                  chunk.toList,
                ),
              )

            case Alt(left, right)                                    =>
              val leftDescription  = loop(left, namesSeen, explaining, cycles.contains(left))
              val rightDescription =
                loop(
                  right,
                  namesSeen ++ leftDescription.taggedToDescribe.map(_.name),
                  explaining,
                  cycles.contains(right),
                )
              PartialDescription(
                (leftDescription.description, rightDescription.description) match {
                  case (DocPart.Empty, DocPart.Empty)                   => DocPart.Empty
                  case (DocPart.Empty, r @ DocPart.Optional(_))         => r
                  case (l @ DocPart.Optional(_), DocPart.Empty)         => l
                  case (DocPart.Empty, r: DocPart.CharRanges)           => DocPart.Optional(DocPart.Literal(List(r)))
                  case (DocPart.Empty, r)                               => DocPart.Optional(r)
                  case (l: DocPart.CharRanges, DocPart.Empty)           => DocPart.Optional(DocPart.Literal(List(l)))
                  case (DocPart.CharRanges(lr), DocPart.CharRanges(rr)) => DocPart.CharRanges(lr ++ rr)
                  case (l: DocPart.CharRanges, r) => DocPart.Alternatives(List(DocPart.Literal(List(l)), r))
                  case (l, r: DocPart.CharRanges) => DocPart.Alternatives(List(l, DocPart.Literal(List(r))))
                  case (l, DocPart.Empty)         => DocPart.Optional(l)
                  case (DocPart.Alternatives(la), DocPart.Alternatives(ra)) => DocPart.Alternatives(la ++ ra)
                  case (DocPart.Alternatives(la), r)                        => DocPart.Alternatives(la :+ r)
                  case (l, DocPart.Alternatives(ra))                        => DocPart.Alternatives(l :: ra)
                  case (l, r)                                               => DocPart.Alternatives(List(l, r))
                },
                leftDescription.taggedToDescribe ++ rightDescription.taggedToDescribe,
              )
            case l @ Lazy(_)                                         => loop(l.codec, namesSeen, explaining, seen)
            case Zip(left, right, _)                                 =>
              val l                = cycles.getOrElse(left, left)
              val r                = cycles.getOrElse(right, right)
              val leftDescription  = loop(l, namesSeen, explaining, cycles.contains(l))
              val rightDescription =
                loop(r, namesSeen ++ leftDescription.taggedToDescribe.map(_.name), explaining, cycles.contains(r))
              val d                =
                (leftDescription.description, rightDescription.description) match {
                  case (DocPart.Empty, rs)                              => rs
                  case (ls, DocPart.Empty)                              => ls
                  case (DocPart.Literal(lt), DocPart.Literal(rt))       => DocPart.Literal(lt ++ rt)
                  case (lr: DocPart.CharRanges, rr: DocPart.CharRanges) => DocPart.Literal(List(lr, rr))
                  case (DocPart.Literal(lt), rr: DocPart.CharRanges)    => DocPart.Literal(lt :+ rr)
                  case (lr: DocPart.CharRanges, DocPart.Literal(rt))    => DocPart.Literal(lr :: rt)
                  case (lr: DocPart.CharRanges, rs) => DocPart.Sequence(List(DocPart.Literal(List(lr)), rs))
                  case (ls, rr: DocPart.CharRanges) => DocPart.Sequence(List(ls, DocPart.Literal(List(rr))))
                  case (DocPart.Sequence(lps), DocPart.Sequence(rps)) => DocPart.Sequence(lps ++ rps)
                  case (DocPart.Sequence(lps), rs)                    => DocPart.Sequence(lps :+ rs)
                  case (ls, DocPart.Sequence(rps))                    => DocPart.Sequence(ls :: rps)
                  case (ls, rs)                                       => DocPart.Sequence(List(ls, rs))

                }
              PartialDescription(d, leftDescription.taggedToDescribe ++ rightDescription.taggedToDescribe)
            case TransformOrFail(c, _, _)                            => loop(c, namesSeen, explaining, seen)
            case t @ Tagged(_, c, false) if explaining == t && !seen =>
              loop(c, namesSeen, explaining, seen = true)
            case t @ Tagged(name, _, false) if !namesSeen(name)      => PartialDescription(DocPart.Ref(name), List(t))
            case Tagged(name, _, _)                                  => PartialDescription(DocPart.Ref(name))
          }
      }
    }

    @tailrec
    def getLines(
      linesRev: List[String],
      codecsToDescribe: List[RichTextCodec[_]],
      namesSeen: Set[String],
    ): List[String] = {
      codecsToDescribe match {
        case Nil       => linesRev
        case h :: tail =>
          val (partialDescription, thisName) = h match {
            case t @ Tagged(_, _, false) => (explain(t, namesSeen), Set(t.name))
            case c                       => (loop(c, namesSeen, Empty), Set.empty[String])
          }
          val line                           = (partialDescription.description match {
            case d: DocPart.CharRanges => DocPart.Literal(List(d))
            case d                     => d
          }).toString

          val stillToDescribe = tail ++ partialDescription.taggedToDescribe
          val namesSeenNow    = namesSeen ++ partialDescription.taggedToDescribe.map(_.name) ++ thisName

          getLines(line :: linesRev, stillToDescribe, namesSeenNow)
      }
    }

    getLines(Nil, List(cycles.getOrElse(codec, codec)), Set.empty).reverse.mkString("\n")
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
