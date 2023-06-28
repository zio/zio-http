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

import scala.annotation.tailrec
import scala.collection.immutable.ListMap
import scala.language.implicitConversions

import zio.{Chunk, NonEmptyChunk}

import zio.http.Path

/**
 * A codec for paths, which consists of segments, where each segment may be a
 * literal, an integer, a long, a string, a UUID, or the trailing path.
 *
 * {{{
 * import zio.http.endpoint.PathCodec._
 *
 * val pathCodec = empty / "users" / int("user-id") / "posts" / string("post-id")
 * }}}
 */
sealed trait PathCodec[A] { self =>
  import PathCodec._

  /**
   * Attaches documentation to the path codec, which may be used when generating
   * developer docs for a route.
   */
  def ??(doc: Doc): PathCodec[A]

  def ++[B](that: PathCodec[B])(implicit combiner: Combiner[A, B]): PathCodec[combiner.Out] =
    PathCodec.Concat(self, that, combiner)

  /**
   * Returns a new pattern that is extended with the specified segment pattern.
   */
  final def /[B](segment: SegmentCodec[B])(implicit combiner: Combiner[A, B]): PathCodec[combiner.Out] =
    self ++ Segment[B](segment)

  final def asType[B](implicit ev: A =:= B): PathCodec[B] = self.asInstanceOf[PathCodec[B]]

  /**
   * Decodes a method and path into a value of type `A`.
   */
  final def decode(path: Path): Either[String, A] = {
    import PathCodec.Opt._

    val instructions = optimize
    val segments     = path.segments

    var i                           = 0
    var j                           = 0
    var fail                        = ""
    val stack: java.util.Deque[Any] = new java.util.ArrayDeque[Any](2)

    // For root:
    stack.push(())

    while (i < instructions.length) {
      val opt = instructions(i)

      opt match {
        case Match(value) =>
          if (j >= segments.length || segments(j) != value) {
            fail = s"Expected path segment \"${value}\" but found end of path"
            i = instructions.length
          } else {
            stack.push(())
            j = j + 1
          }

        case Combine(combiner0) =>
          val combiner = combiner0.asInstanceOf[Combiner[Any, Any]]
          val right    = stack.pop()
          val left     = stack.pop()
          stack.push(combiner.combine(left, right))

        case IntOpt =>
          if (j >= segments.length) {
            fail = "Expected integer path segment but found end of path"
            i = instructions.length
          } else {
            val segment = segments(j)
            j = j + 1
            try {
              stack.push(segment.toInt)
            } catch {
              case _: NumberFormatException =>
                fail = s"Expected integer path segment but found \"${segment}\""
                i = instructions.length
            }
          }

        case LongOpt   =>
          if (j >= segments.length) {
            fail = "Expected long path segment but found end of path"
            i = instructions.length
          } else {
            val segment = segments(j)
            j = j + 1
            try {
              stack.push(segment.toLong)
            } catch {
              case _: NumberFormatException =>
                fail = s"Expected long path segment but found ${segment}"
                i = instructions.length
            }
          }
        case StringOpt =>
          if (j >= segments.length) {
            fail = "Expected text path segment but found end of path"
            i = instructions.length
          } else {
            val segment = segments(j)
            j = j + 1
            stack.push(segment)
          }

        case UUIDOpt =>
          if (j >= segments.length) {
            fail = "Expected UUID path segment but found end of path"
            i = instructions.length
          } else {
            val segment = segments(j)
            j = j + 1
            try {
              stack.push(java.util.UUID.fromString(segment.toString))
            } catch {
              case _: IllegalArgumentException =>
                fail = s"Expected UUID path segment but found ${segment}"
                i = instructions.length
            }
          }

        case BoolOpt =>
          if (j >= segments.length) {
            fail = "Expected boolean path segment but found end of path"
            i = instructions.length
          } else {
            val segment = segments(j)
            j = j + 1

            if (segment == "true") {
              stack.push(true)
            } else if (segment == "false") {
              stack.push(false)
            } else {
              fail = s"Expected boolean path segment but found ${segment}"
              i = instructions.length
            }
          }

        case TrailingOpt =>
          // Consume all Trailing, possibly empty:
          if (j >= segments.length) {
            val result =
              if (path.hasTrailingSlash) Path.root else Path.empty

            stack.push(result)
          } else {
            val flags =
              if (j == 0) path.flags
              else if (path.hasTrailingSlash) Path.Flags(Path.Flag.TrailingSlash)
              else 0

            stack.push(Path(flags, segments.drop(j)))
            j = segments.length
          }

        case Unit =>
          stack.push(())
      }

      i = i + 1
    }
    if (fail != "") Left(fail)
    else {
      if (j < segments.length) {
        val rest = segments.drop(j).mkString("/")
        Left(s"Expected end of path but found: ${rest}")
      } else {
        Right(stack.pop().asInstanceOf[A])
      }
    }
  }

  /**
   * Returns the documentation for the path codec, if any.
   */
  def doc: Doc

  /**
   * Encodes a value of type `A` into the method and path that this route
   * pattern would successfully match against.
   */
  final def encode(value: A): Path = format(value)

  private[http] final def erase: PathCodec[Any] = self.asInstanceOf[PathCodec[Any]]

  /**
   * Formats a value of type `A` into a path. This is useful for embedding paths
   * into HTML that is rendered by the server.
   */
  final def format(value: A): Path = {
    def loop(path: PathCodec[_], value: Any): Path = path match {
      case PathCodec.Concat(left, right, combiner, _) =>
        val (leftValue, rightValue) = combiner.separate(value.asInstanceOf[combiner.Out])

        loop(left, leftValue) ++ loop(right, rightValue)

      case PathCodec.Segment(segment, _) =>
        segment.format(value.asInstanceOf[segment.Type])
    }

    val path = loop(self, value)

    if (path.nonEmpty) path.addLeadingSlash else path
  }

  /**
   * Determines if this pattern matches the specified method and path. Rather
   * than use this method, you should just try to decode it directly, for higher
   * performance, otherwise the same information will be decoded twice.
   */
  final def matches(path: Path): Boolean =
    decode(path).isRight

  private[http] lazy val optimize: Array[Opt] = {
    def loop(pattern: PathCodec[_]): Chunk[Opt] =
      pattern match {
        case PathCodec.Segment(segment, _) =>
          Chunk(segment.asInstanceOf[SegmentCodec[_]] match {
            case SegmentCodec.Empty(_)          => Opt.Unit
            case SegmentCodec.Literal(value, _) => Opt.Match(value)
            case SegmentCodec.IntSeg(_, _)      => Opt.IntOpt
            case SegmentCodec.LongSeg(_, _)     => Opt.LongOpt
            case SegmentCodec.Text(_, _)        => Opt.StringOpt
            case SegmentCodec.UUID(_, _)        => Opt.UUIDOpt
            case SegmentCodec.BoolSeg(_, _)     => Opt.BoolOpt
            case SegmentCodec.Trailing(_)       => Opt.TrailingOpt
          })

        case Concat(left, right, combiner, _) =>
          loop(left) ++ loop(right) ++ Chunk(Opt.Combine(combiner))
      }

    loop(self).materialize.toArray
  }

  /**
   * Renders the path codec as a string.
   */
  def render: String = {
    import SegmentCodec._

    def loop(path: PathCodec[_]): String = path match {
      case PathCodec.Concat(left, right, _, _) =>
        loop(left) + loop(right)

      case PathCodec.Segment(segment, _) => segment.render
    }

    loop(self)
  }

  /**
   * Returns the segments of the path codec.
   */
  def segments: Chunk[SegmentCodec[_]] = {
    def loop(path: PathCodec[_]): Chunk[SegmentCodec[_]] = path match {
      case PathCodec.Segment(segment, _) => Chunk(segment)

      case PathCodec.Concat(left, right, _, _) =>
        loop(left) ++ loop(right)
    }

    loop(self)
  }

  override def toString(): String = render
}
object PathCodec          {

  /**
   * Constructs a path codec from a method and a path literal.
   */
  def apply(value: String): PathCodec[Unit] = {
    val path = Path(value)

    path.segments.foldLeft[PathCodec[Unit]](PathCodec.empty) { (pathSpec, segment) =>
      pathSpec./[Unit](SegmentCodec.literal(segment))
    }
  }

  def bool(name: String): PathCodec[Boolean] = Segment(SegmentCodec.bool(name))

  /**
   * The empty / root path codec.
   */
  def empty: PathCodec[Unit] = Segment[Unit](SegmentCodec.Empty())

  def int(name: String): PathCodec[Int] = Segment(SegmentCodec.int(name))

  def literal(value: String): PathCodec[Unit] = apply(value)

  def long(name: String): PathCodec[Long] = Segment(SegmentCodec.long(name))

  implicit def path(value: String): PathCodec[Unit] = apply(value)

  def string(name: String): PathCodec[String] = Segment(SegmentCodec.string(name))

  def trailing: PathCodec[Path] = Segment(SegmentCodec.Trailing())

  def uuid(name: String): PathCodec[java.util.UUID] = Segment(SegmentCodec.uuid(name))

  private[http] final case class Segment[A](segment: SegmentCodec[A], doc: Doc = Doc.empty) extends PathCodec[A] {
    def ??(doc: Doc): Segment[A] = copy(doc = this.doc + doc)
  }
  private[http] final case class Concat[A, B, C](
    left: PathCodec[A],
    right: PathCodec[B],
    combiner: Combiner.WithOut[A, B, C],
    doc: Doc = Doc.empty,
  ) extends PathCodec[C] {
    def ??(doc: Doc): Concat[A, B, C] = copy(doc = this.doc + doc)
  }

  private[http] val someUnit = Some(())

  /**
   * An optimized representation of the process of decoding a path and producing
   * a value. This is built for an evaluator that uses a stack.
   */
  private[http] sealed trait Opt
  private[http] object Opt {
    final case class Match(value: String)              extends Opt
    final case class Combine(combiner: Combiner[_, _]) extends Opt
    case object IntOpt                                 extends Opt
    case object LongOpt                                extends Opt
    case object StringOpt                              extends Opt
    case object UUIDOpt                                extends Opt
    case object BoolOpt                                extends Opt
    case object TrailingOpt                            extends Opt
    case object Unit                                   extends Opt
  }

  private[http] final case class SegmentSubtree[+A](
    literals: ListMap[String, SegmentSubtree[A]],
    others: ListMap[SegmentCodec[_], SegmentSubtree[A]],
    value: Chunk[A],
  ) {
    self =>
    import SegmentSubtree._

    def ++[A1 >: A](that: SegmentSubtree[A1]): SegmentSubtree[A1] =
      SegmentSubtree(
        mergeMaps(self.literals, that.literals)(_ ++ _),
        mergeMaps(self.others, that.others)(_ ++ _),
        self.value ++ that.value,
      )

    def add[A1 >: A](segments: Iterable[SegmentCodec[_]], value: A1): SegmentSubtree[A1] =
      self ++ SegmentSubtree.single(segments, value)

    def get(path: Path): Chunk[A] = {
      val segments = path.segments
      var subtree  = self
      var result   = subtree.value
      var i        = 0

      while (i < segments.length) {
        val segment = segments(i)

        if (subtree.literals.contains(segment)) {
          // Fast path, jump down the tree:
          subtree = subtree.literals(segment)

          result = subtree.value
          i = i + 1
        } else {
          // Slower fallback path. Have to evaluate all predicates at this node:
          val flattened = subtree.othersFlat

          var index = 0
          subtree = null

          while ((index < flattened.length) && (subtree eq null)) {
            val tuple   = flattened(index)
            val matched = tuple._1.matches(segments, i)

            if (matched >= 0) {
              subtree = tuple._2
              result = subtree.value
              i = i + matched
            } else {
              // No match found. Keep looking at alternate routes:
              index += 1
            }
          }

          if (subtree eq null) {
            result = Chunk.empty
            i = segments.length
          }
        }
      }

      // Might be some other matches because trailing matches everything:
      if (subtree ne null) {
        subtree.others.get(SegmentCodec.trailing) match {
          case Some(subtree) =>
            result = result ++ subtree.value
          case None          =>
        }
      }

      result
    }

    private var _othersFlat = null.asInstanceOf[Chunk[(SegmentCodec[_], SegmentSubtree[Any])]]

    private def othersFlat: Chunk[(SegmentCodec[_], SegmentSubtree[A])] = {
      if (_othersFlat eq null) _othersFlat = Chunk.fromIterable(others)
      _othersFlat.asInstanceOf[Chunk[(SegmentCodec[_], SegmentSubtree[A])]]
    }
  }
  object SegmentSubtree    {
    def single[A](segments: Iterable[SegmentCodec[_]], value: A): SegmentSubtree[A] =
      segments.collect { case x if x.nonEmpty => x }
        .foldRight[SegmentSubtree[A]](SegmentSubtree(ListMap(), ListMap(), Chunk(value))) { case (segment, subtree) =>
          val literals =
            segment match {
              case SegmentCodec.Literal(value, _) => ListMap(value -> subtree)
              case _                              => ListMap.empty[String, SegmentSubtree[A]]
            }

          val others =
            ListMap[SegmentCodec[_], SegmentSubtree[A]]((segment match {
              case SegmentCodec.Literal(_, _) => Chunk.empty
              case _                          => Chunk((segment, subtree))
            }): _*)

          SegmentSubtree(literals, others, Chunk.empty)
        }

    val empty: SegmentSubtree[Nothing] =
      SegmentSubtree(ListMap(), ListMap(), Chunk.empty)
  }

  private def mergeMaps[A, B](left: ListMap[A, B], right: ListMap[A, B])(f: (B, B) => B): ListMap[A, B] =
    right.foldLeft(left) { case (acc, (k, v)) =>
      acc.updatedWith(k) {
        case None     => Some(v)
        case Some(v0) => Some(f(v0, v))
      }
    }
}
