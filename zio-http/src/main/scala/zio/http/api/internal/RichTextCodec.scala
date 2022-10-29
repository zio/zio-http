package zio.http.api.internal

import jdk.jfr.Description
import zio.Chunk
import zio.http.api.Combiner

import java.lang.Integer.parseInt
import java.lang.StringBuilder
import java.util.stream.DoubleStream.DoubleMapMultiConsumer
import scala.annotation.tailrec
import scala.collection.immutable.{BitSet, ListMap}
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
  final def ~[B](that: RichTextCodec[B])(implicit combiner: Combiner[A, B]): RichTextCodec[combiner.Out] =
    RichTextCodec.Zip(self, RichTextCodec.defer(that), combiner)

  /**
   * Returns a new codec that is the fallback composition of this codec and the
   * specified codec, preferring this codec, but falling back to the specified
   * codec in the event of failure.
   */
  final def |[B](that: => RichTextCodec[B]): RichTextCodec[Either[A, B]] =
    RichTextCodec.Alt(self, RichTextCodec.defer(that))

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

  final def encode(value: A): Either[Throwable, String] = RichTextCodec.encode(value, self)

  private def parse(value: CharSequence): Either[String, (CharSequence, A)] =
    this match {
      case _: RichTextCodec.Empty.type =>
        // A =:= Unit
        Right((value, ().asInstanceOf[A]))

      case ci: RichTextCodec.CharIn =>
        // A =:= Char
        if (value.length == 0 || !ci.set.contains(value.charAt(0).toInt))
          Left(s"Not found: ${ci.set.toArray.map(_.toChar).mkString}")
        else
          Right((value.subSequence(1, value.length), value.charAt(0).asInstanceOf[A]))

      case RichTextCodec.TransformOrFail(codec, to, _) =>
        codec.parse(value).flatMap { case (rest, a0) => to(a0).map(a => (rest, a)) }

      case alt: RichTextCodec.Alt[a, b] =>
        // A =:= Either[a, b]
        alt.left.parse(value) match {
          case Right((rest, a)) => Right((rest, Left(a).asInstanceOf[A]))
          case Left(errorLeft)  =>
            alt.right.parse(value) match {
              case Right((rest, b)) => Right((rest, Right(b).asInstanceOf[A]))
              case Left(errorRight) => Left(s"($errorLeft, $errorRight)")
            }
        }

      case RichTextCodec.Lazy(codec0) =>
        codec0().parse(value)

      case RichTextCodec.Zip(left, right, combiner) =>
        for {
          l <- left.parse(value)
          r <- right.parse(l._1)
        } yield (r._1, combiner.combine(l._2, r._2))
    }

  final def decode(value: CharSequence): Either[String, A] =
    parse(value).map(_._2)

  final def describe: zio.http.api.Doc = {
    import zio.http.api.Doc
    import zio.http.api.Doc._
    import RichTextCodec._

    // Merges two counts maps, adding the counts for equal keys
    def mergeMaps[K, V](a: ListMap[K, V], b: ListMap[K, V])(add: (V, V) => V): ListMap[K, V] =
      b.foldLeft(a) { case (r, (k, va)) =>
        r.get(k) match {
          case None     => r + (k -> va)
          case Some(vb) => r + (k -> add(va, vb))
        }
      }

    case class DescriptionAndCount(descr: DescriptionPart, count: Int)
    type RefsMap = ListMap[RichTextCodec[_], DescriptionAndCount]

    @tailrec
    def descriptionPartToDescriptionList(
                                          documented: List[(Span, Doc)],
                                          refs: RefsMap,
                                          refNames: Map[DescriptionPart, String],
                                          toDescribe: DescriptionPart,
                                          described: Set[DescriptionPart]
    ): (Map[DescriptionPart, String], List[(Span, Doc)]) = {
      def toSpans(
                   refNames: Map[DescriptionPart, String],
                   d: DescriptionPart,
      ): (Map[DescriptionPart, String], List[Span]) = {

        def merge(parts: (Map[DescriptionPart, String], List[Span])*): (Map[DescriptionPart, String], List[Span]) =
          parts.reduce((a, b) => (a._1 ++ b._1, a._2 ++ b._2))

        def code(c: String) = (Map.empty[DescriptionPart, String], List(Span.code(c)))

        d match {
          case DescriptionPart.Text(str)           => (refNames, List(Span.text(str)))
          case DescriptionPart.CharRange(from, to) =>
            (refNames, List(Span.text(from.toString), Span.code("-"), Span.text(to.toString)))
          case DescriptionPart.CharRanges(ranges)  =>
            (
              refNames,
              Span.code("[") :: (ranges.map(part => toSpans(refNames, part)._2).reduce((a, b) => a ++ b) :+ Span.code(
                "]",
              )),
            )
          case DescriptionPart.Sequence(seq)       =>
            seq.map {
              case part @ DescriptionPart.Alternatives(_) => merge(code("("), toSpans(refNames, part), code(")"))
              case part                                   => toSpans(refNames, part)
            }.reduce((a, b) => merge(a, (Map.empty, List(Span.space)), b))
          case DescriptionPart.Named(name)         => (refNames, List(Span.code(s"<$name>")))
          case DescriptionPart.Alternatives(alts)  =>
            alts.map(part => toSpans(refNames, part)).reduce((a, b) => merge(a, code(" | "), b))
          case DescriptionPart.Optional(part)      =>
            part match {
              case DescriptionPart.Sequence(_) | DescriptionPart.Alternatives(_) =>
                merge(code("("), toSpans(refNames, part), code(")?"))
              case _ => merge(toSpans(refNames, part), code("?"))
            }
          case DescriptionPart.Empty               => (refNames, List(Span.code("<empty>")))
          case DescriptionPart.Ref(of)             =>
            val DescriptionAndCount(descr, count) = refs(of)
            if (count < 2) {
              toSpans(refNames, descr)
            } else {
              refNames.get(descr) match {
                case Some(name) => (refNames, List(Span.code(name)))
                case None    =>
                  val name = s"<${refNames.size}>"
                  (refNames + (descr -> name), List(Span.code(name)))
              }
            }
        }
      }
      val myRef = refNames(toDescribe)
      val (nextRefNames, spans) = toSpans(refNames, toDescribe)
      val stillToDescribe: Map[DescriptionPart, String] = nextRefNames -- described - toDescribe
      stillToDescribe.headOption match {
        case None => (nextRefNames, documented :+ (Span.code(myRef), p(Span.spans(spans))))
        case Some(next) => descriptionPartToDescriptionList(documented :+ (Span.code(myRef), p(Span.spans(spans))), refs, nextRefNames, next._1, described + toDescribe)
      }
    }

    def description(refs: RefsMap, it: RichTextCodec[_]): (RefsMap, DescriptionPart) = {
      def refOrDesc(c: RichTextCodec[_]): (RefsMap, DescriptionPart) = {
        refs.get(c) match {
          case Some(DescriptionAndCount(descr, count)) =>
            val newRefs: RefsMap = refs + (c -> DescriptionAndCount(descr, count + 1))
            (newRefs, DescriptionPart.Ref(c))
          case _                                       =>
            val (newRefs, descr) = description(refs + (c -> DescriptionAndCount(DescriptionPart.Empty, 1)), c)
            (newRefs + (c -> DescriptionAndCount(descr, newRefs(c).count)), descr)
        }
      }

      def mergeRefsMaps(lr: RefsMap, rr: RefsMap): RefsMap =
        mergeMaps(lr, rr)((a, b) => DescriptionAndCount(a.descr, a.count + b.count))

      it match {
        case RichTextCodec.Empty          => (refs, DescriptionPart.Empty)
        case CharIn(set)                  =>
          set.size match {
            case 0                              => (refs, DescriptionPart.Named("invalid"))
            case 1                              =>
              (refs, DescriptionPart.Text(set.head.toChar.toString))
            case _ if set == digitChars         => (refs, DescriptionPart.Named("digit"))
            case _ if set == letterChars        => (refs, DescriptionPart.Named("letter"))
            case _ if set == letterOrDigitChars => (refs, DescriptionPart.Named("letterOrDigit"))
            case _ if set == whitespaceChars    => (refs, DescriptionPart.Named("whitespace"))
            case _                              =>
              // TODO: Charater ranges should be reported as eg. A-Z instead of A | B | C ... | Z
              (refs, DescriptionPart.Alternatives(set.toList.map(c => DescriptionPart.Text(c.toChar.toString))))
          }
        case TransformOrFail(codec, _, _) =>
          refOrDesc(codec.asInstanceOf[RichTextCodec[_]])
        case Alt(left, right)             =>
          val (lRefs, l) = refOrDesc(left.asInstanceOf[RichTextCodec[_]])
          val (rRefs, r) = refOrDesc(right.asInstanceOf[RichTextCodec[_]])
          val newRefs    = mergeRefsMaps(lRefs, rRefs)
          (
            newRefs,
            (l, r) match {
              case (DescriptionPart.Empty, DescriptionPart.Empty)                       => DescriptionPart.Empty
              case (DescriptionPart.Empty, _)                                           => DescriptionPart.Optional(r)
              case (_, DescriptionPart.Empty)                                           => DescriptionPart.Optional(l)
              case (DescriptionPart.Alternatives(la), DescriptionPart.Alternatives(ra)) =>
                DescriptionPart.Alternatives(la ++ ra)
              case (DescriptionPart.Alternatives(la), _) => DescriptionPart.Alternatives(la :+ r)
              case (_, DescriptionPart.Alternatives(ra)) => DescriptionPart.Alternatives(l :: ra)
              case _                                     => DescriptionPart.Alternatives(List(l, r))
            },
          )
        case Lazy(codec0)                 =>
          refOrDesc(codec0().asInstanceOf[RichTextCodec[_]])
        case Zip(left, right, _)          =>
          val (lRefs, l) = refOrDesc(left.asInstanceOf[RichTextCodec[_]])
          val (rRefs, r) = refOrDesc(right.asInstanceOf[RichTextCodec[_]])
          val newRefs    = mergeRefsMaps(lRefs, rRefs)
          (
            newRefs,
            (l, r) match {
              case (DescriptionPart.Empty, _)                                   => r
              case (_, DescriptionPart.Empty)                                   => l
              case (DescriptionPart.Text(lt), DescriptionPart.Text(rt))         => DescriptionPart.Text(lt + rt)
              case (DescriptionPart.Sequence(ls), DescriptionPart.Sequence(rs)) => DescriptionPart.Sequence(ls ++ rs)
              case (DescriptionPart.Sequence(ls), _)                            => DescriptionPart.Sequence(ls :+ r)
              case (_, DescriptionPart.Sequence(rs))                            => DescriptionPart.Sequence(l :: rs)
              case _                                                            => DescriptionPart.Sequence(List(l, r))
            },
          )
      }
    }
    val (refs, descr) = description(ListMap.empty, self)

    val (_, descriptions) = descriptionPartToDescriptionList(List.empty, refs, Map(descr -> "<main>"), descr, Set.empty)

    descriptionList(descriptions :_*)
  }

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

  /**
   * Returns a lazy codec, which can be used to define recursive codecs.
   */
  def defer[A](codec: => RichTextCodec[A]): RichTextCodec[A] =
    Lazy(() => codec)

  /**
   * A codec that describes a single specified character.
   */
  def char(c: Char): RichTextCodec[Char] = CharIn(BitSet(c.toInt))

  private val whitespaceChars    = BitSet((Char.MinValue to Char.MaxValue).filter(_.isWhitespace).map(_.toInt): _*)
  private val letterChars        = BitSet((Char.MinValue to Char.MaxValue).filter(_.isLetter).map(_.toInt): _*)
  private val digitChars         = BitSet((Char.MinValue to Char.MaxValue).filter(_.isDigit).map(_.toInt): _*)
  private val letterOrDigitChars = BitSet((Char.MinValue to Char.MaxValue).filter(_.isLetterOrDigit).map(_.toInt): _*)

  /**
   * A codec that describes a digit character.
   */
  val digit: RichTextCodec[Int] = CharIn(digitChars).transform[Int](c => parseInt(c.toString), x => x.toString.head)

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
  val letter: RichTextCodec[Char] = CharIn(letterChars)

  /**
   * A codec that describes a letter or digit character.
   */
  val letterOrDigit: RichTextCodec[Char] = CharIn(letterOrDigitChars)

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
   * A codec that describes any number of whitespace characters.
   */
  lazy val whitespaces: RichTextCodec[Unit] =
    whitespaceChar.repeat.transform(_ => (), _ => Chunk.empty)

  /**
   * A codec that describes a single whitespace character.
   */
  lazy val whitespaceChar: RichTextCodec[Unit] = CharIn(whitespaceChars).unit(' ')

  private def encode[A](value: A, self: RichTextCodec[A]): Either[Throwable, String] = {
    self match {
      case RichTextCodec.Empty                           => Right("")
      case RichTextCodec.CharIn(_)                       => { Right(value.asInstanceOf[Char].toString) }
      case RichTextCodec.TransformOrFail(codec, _, from) =>
        from(value) match {
          case Left(err)     => Left(new Exception(err))
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
    }
  }

  private sealed trait DescriptionPart
  private object DescriptionPart {
    case class Text(str: String)                         extends DescriptionPart
    case class CharRange(from: Char, to: Char)           extends DescriptionPart
    case class CharRanges(ranges: List[CharRange])       extends DescriptionPart
    case class Sequence(seq: List[DescriptionPart])      extends DescriptionPart
    case class Named(name: String)                       extends DescriptionPart
    case class Ref(of: RichTextCodec[_])                 extends DescriptionPart
    case class Alternatives(alts: List[DescriptionPart]) extends DescriptionPart
    case class Optional(part: DescriptionPart)           extends DescriptionPart
    case object Empty                                    extends DescriptionPart
  }
}
