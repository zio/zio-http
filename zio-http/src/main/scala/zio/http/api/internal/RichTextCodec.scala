package zio.http.api.internal

import zio.Chunk
import zio.http.api.Combiner

import java.lang.Integer.parseInt
import java.lang.StringBuilder
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

  // TODO
  final def describe: zio.http.api.Doc = {
    import zio.http.api.Doc
    import zio.http.api.Doc._
    import RichTextCodec._

    // First we identify codecs that will be described as a reference to a separate description.
    // A must for recursively used codcecs, nice for repeatedly used ones

    // Merges two counts maps, adding the counts for equal keys
    def mergeCounts[K](a: ListMap[K, Int], b: ListMap[K, Int]): ListMap[K, Int] =
      b.foldLeft(a) { case (r, (key, count)) =>
        r.get(key) match {
          case None    => r + (key -> count)
          case Some(n) => r + (key -> (count + n))
        }
      }

    def countCodecs(counted: ListMap[RichTextCodec[_], Int], it: RichTextCodec[_]): ListMap[RichTextCodec[_], Int] =
      counted.getOrElse(it, 0) match {
        case n if n > 0 => counted + (it -> (n + 1))
        case _          =>
          it match {
            case RichTextCodec.Empty                    => counted // We will refrence `empty` explicitly anyway
            case CharIn(set) if set.sizeCompare(5) <= 0 =>
              counted // we will not create references for single characters or for small groups of characters
            case CharIn(_)                              => counted + (it -> 1)
            case TransformOrFail(codec, _, _)           =>
              val c = codec.asInstanceOf[RichTextCodec[_]]
              mergeCounts(counted, countCodecs(counted + (c -> 1), c))
            case Lazy(codec0)                           =>
              val c = codec0().asInstanceOf[RichTextCodec[_]]
              mergeCounts(counted, countCodecs(counted + (c -> 1), c))
            case Alt(left, right)                       =>
              val l = left.asInstanceOf[RichTextCodec[_]]
              val r = right.asInstanceOf[RichTextCodec[_]]
              mergeCounts(
                counted,
                mergeCounts(
                  countCodecs(counted + (l -> 1), l),
                  countCodecs(counted + (r -> 1), r),
                ),
              )
            case Zip(left, right, _)                    =>
              val l = left.asInstanceOf[RichTextCodec[_]]
              val r = right.asInstanceOf[RichTextCodec[_]]
              mergeCounts(
                counted,
                mergeCounts(
                  countCodecs(counted + (l -> 1), l),
                  countCodecs(counted + (r -> 1), r),
                ),
              )
          }
      }

    val counts                                     = countCodecs(ListMap(self -> 0), self)
    val references: ListMap[RichTextCodec[_], Int] = ListMap(counts.filter{
      case (codec, count) =>
        codec match {
          case CharIn(set) if set == digitChars => false
          case _ => count > 1
        }
      }.keysIterator.zipWithIndex.toList: _*)

    def describeAsDefinition(it: RichTextCodec[_]): (Span, Doc) = {
      Span.code(s"<${1 + references(it)}>") -> p(description(it))
    }

    def description(it: RichTextCodec[_]): Span = {

      def refOrDesc(c: RichTextCodec[_]): Span = {
        c match {
          case CharIn(set) if set == digitChars => Span.code("<digit>")
          case _ =>
            references.get(c) match {
              case Some(i) => Span.code(s"<${i + 1}>")
              case _ => description(c)
            }
        }
      }

      it match {
        case RichTextCodec.Empty          => Span.code("<empty>")
        case CharIn(set)                  =>
          set.size match {
            case 0 => Span.code("<invalid>")
            case 1 =>
              // TODO - collapse sequence of chars to one span of a longer string
              // TODO - display spaces visibly, when appropriate
              Span.text(set.head.toChar.toString)
            case _ =>
              // TODO - Instead of enumerating the characters, try to optimise it by describing it a range (A-Z) or Unicode block
              Span.code(" (") + set.toIndexedSeq
                .map(c => Span.text(c.toChar.toString))
                .reduce((a, b) => a + Span.code("|") + b) + Span.code(") ")
          }
        case TransformOrFail(codec, _, _) =>
          refOrDesc(codec.asInstanceOf[RichTextCodec[_]])
        case Alt(left, right)             =>
          val l = left.asInstanceOf[RichTextCodec[_]]
          val r = right.asInstanceOf[RichTextCodec[_]]
          // TODO: When of the alternatives is `empty` mark the rest simply as optional
          refOrDesc(l) + Span.code(" | ") + refOrDesc(r)
        case Lazy(codec0)                 =>
          refOrDesc(codec0().asInstanceOf[RichTextCodec[_]])
        case Zip(left, right, _)          =>
          val l = left.asInstanceOf[RichTextCodec[_]]
          val r = right.asInstanceOf[RichTextCodec[_]]
          (l, r) match {
            case (CharIn(_), CharIn(_))                    => refOrDesc(l) + refOrDesc(r)
            case (Alt(_, _), _) if !references.contains(l) =>
              r match {
                case Alt(_, _) if !references.contains(r) =>
                  Span.code("(") + description(l) + Span.code(") (") + description(r) + Span.code(")")
                case _                                    =>
                  Span.code("(") + description(l) + Span.code(")") + refOrDesc(r)
              }
            case (_, Alt(_, _)) if !references.contains(r) =>
              refOrDesc(l) + Span.code(" (") + description(r) + Span.code(")")
            case _                                         => refOrDesc(l) + Span.space + refOrDesc(r)
          }
      }
    }
    references.get(self).map(_ => Doc.empty).getOrElse(p(description(self))) +
    DescriptionList(references.map(r => describeAsDefinition(r._1)).toList)
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


  private val digitChars = BitSet((Char.MinValue to Char.MaxValue).filter(_.isDigit).map(_.toInt): _*)
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
  val letter: RichTextCodec[Char] = filter(_.isLetter)

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
  lazy val whitespaceChar: RichTextCodec[Unit] = filter(_.isWhitespace).unit(' ')

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
}
