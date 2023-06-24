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
 * val pathCodec = root / "users" / int("user-id") / "posts" / string("post-id")
 * }}}
 */
sealed trait PathCodec[A] { self =>
  import PathCodec._

  /**
   * Attaches documentation to the path codec, which may be used when generating
   * developer docs for a route.
   */
  def ??(doc: Doc): PathCodec[A]

  /**
   * Returns a new pattern that is extended with the specified segment pattern.
   */
  final def /[B](segment: SegmentCodec[B])(implicit combiner: Combiner[A, B]): PathCodec[combiner.Out] =
    PathCodec.Child[A, B, combiner.Out](this, segment, combiner)

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

        case Combine(combiner) =>
          val right = stack.pop()
          val left  = stack.pop()
          stack.push(combiner.asInstanceOf[Combiner[Any, Any]].combine(left, right))

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
            stack.push(segment.toString)
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
            stack.push(Path.empty)
          } else {
            stack.push(Path(0, segments.drop(j)))
            j = segments.length
          }
      }

      i = i + 1
    }
    if (fail != "") Left(fail)
    else {
      if (j < segments.length) {
        val rest = segments.drop(j).mkString("/")
        Left(s"Expected end of path but found: ${rest}")
      } else Right(stack.pop().asInstanceOf[A])
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
    @annotation.tailrec
    def loop(path: PathCodec[_], value: Any, acc: Path): Path = path match {
      case PathCodec.Root(_) => acc

      case PathCodec.Child(parent, segment, combiner, _) =>
        val (left, right) = combiner.separate(value.asInstanceOf[combiner.Out])

        loop(parent, left, segment.format(right.asInstanceOf[segment.Type]) ++ acc)
    }

    val path = loop(self, value, Path.empty)

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
    @tailrec
    def loop(pattern: PathCodec[_], acc: Chunk[Opt]): Chunk[Opt] =
      pattern match {
        case PathCodec.Root(_)                   => acc
        case Child(parent, segment, combiner, _) =>
          val segmentOpt = segment.asInstanceOf[SegmentCodec[_]] match {
            case SegmentCodec.Literal(value, _) => Opt.Match(value)
            case SegmentCodec.IntSeg(_, _)      => Opt.IntOpt
            case SegmentCodec.LongSeg(_, _)     => Opt.LongOpt
            case SegmentCodec.Text(_, _)        => Opt.StringOpt
            case SegmentCodec.UUID(_, _)        => Opt.UUIDOpt
            case SegmentCodec.BoolSeg(_, _)     => Opt.BoolOpt
            case SegmentCodec.Trailing(_)       => Opt.TrailingOpt
          }
          loop(parent, segmentOpt +: Opt.Combine(combiner) +: acc)
      }

    loop(self, Chunk.empty).materialize.toArray
  }

  /**
   * Renders the path codec as a string.
   */
  def render: String = {
    import SegmentCodec._

    def loop(path: PathCodec[_]): String = path match {
      case PathCodec.Root(_) => ""

      case PathCodec.Child(parent, segment, _, _) =>
        loop(parent) + (segment.asInstanceOf[SegmentCodec[_]] match {
          case Literal(value, _) => s"/$value"
          case IntSeg(name, _)   => s"/{$name}"
          case LongSeg(name, _)  => s"/{$name}"
          case Text(name, _)     => s"/{$name}"
          case BoolSeg(name, _)  => s"/${name}"
          case UUID(name, _)     => s"/{$name}"
        })
    }

    loop(self)
  }

  /**
   * Returns the segments of the path codec.
   */
  def segments: Chunk[SegmentCodec[_]] = {
    def loop(path: PathCodec[_], acc: Chunk[SegmentCodec[_]]): Chunk[SegmentCodec[_]] = path match {
      case PathCodec.Root(_) => acc

      case PathCodec.Child(parent, segment, _, _) => loop(parent, acc :+ segment)
    }

    loop(self, Chunk.empty)
  }

  override def toString(): String = render
}
object PathCodec          {

  private[http] final case class SegmentSubtree[+A](
    literals: ListMap[String, SegmentSubtree[A]],
    others: Chunk[(SegmentCodec[_], SegmentSubtree[A])],
    value: Chunk[A],
  ) {
    self =>
    import SegmentSubtree._

    def ++[A1 >: A](that: SegmentSubtree[A1]): SegmentSubtree[A1] =
      SegmentSubtree(
        mergeMaps(self.literals, that.literals)(_ ++ _),
        self.others ++ that.others,
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
          val flattened = subtree.others

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
              // Keep looking:
              index += 1
            }
          }

          if (subtree eq null) {
            result = Chunk.empty
            i = segments.length
          }
        }
      }

      result
    }
  }
  object SegmentSubtree {
    def single[A](segments: Iterable[SegmentCodec[_]], value: A): SegmentSubtree[A] =
      segments.foldLeft[SegmentSubtree[A]](SegmentSubtree(ListMap(), Chunk.empty, Chunk(value))) {
        case (subtree, segment) =>
          val literals =
            segment match {
              case SegmentCodec.Literal(value, _) => ListMap(value -> subtree)
              case _                              => ListMap.empty[String, SegmentSubtree[A]]
            }

          val others =
            segment match {
              case SegmentCodec.Literal(_, _) => Chunk.empty
              case _                          => Chunk((segment, subtree))
            }

          SegmentSubtree(literals, others, Chunk.empty)
      }

    val empty: SegmentSubtree[Nothing] =
      SegmentSubtree(ListMap(), Chunk.empty, Chunk.empty)
  }

  private def mergeMaps[A, B](left: ListMap[A, B], right: ListMap[A, B])(f: (B, B) => B): ListMap[A, B] =
    right.foldLeft(left) { case (acc, (k, v)) =>
      acc.updatedWith(k) {
        case None     => Some(v)
        case Some(v0) => Some(f(v0, v))
      }
    }

  /**
   * Constructs a path codec from a method and a path literal.
   */
  def apply(value: String): PathCodec[Unit] = {
    val path = Path(value)

    path.segments.foldLeft[PathCodec[Unit]](Root()) { (pathSpec, segment) =>
      pathSpec./[Unit](SegmentCodec.literal(segment))
    }
  }

  /**
   * The root path codec.
   */
  def root: PathCodec[Unit] = Root()

  private[http] final case class Root(doc: Doc = Doc.empty) extends PathCodec[Unit] {
    def ??(doc: Doc): Root = copy(doc = this.doc + doc)
  }
  private[http] final case class Child[A, B, C](
    parent: PathCodec[A],
    segment: SegmentCodec[B],
    combiner: Combiner.WithOut[A, B, C],
    doc: Doc = Doc.empty,
  ) extends PathCodec[C] {
    def ??(doc: Doc): Child[A, B, C] = copy(doc = this.doc + doc)
  }

  private[http] val someUnit = Some(())

  private[http] sealed trait Opt
  private[http] object Opt {
    final case class MethodOpt(method: zio.http.Method) extends Opt
    final case class Match(value: String)               extends Opt
    final case class Combine(combiner: Combiner[_, _])  extends Opt
    case object IntOpt                                  extends Opt
    case object LongOpt                                 extends Opt
    case object StringOpt                               extends Opt
    case object UUIDOpt                                 extends Opt
    case object BoolOpt                                 extends Opt
    case object TrailingOpt                             extends Opt
  }
}
