/*
 * Copyright 2023 Sporta Technologies PVT LTD & the ZIO HTTP contributors.
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
package zio.http

import scala.annotation.tailrec
import scala.collection.immutable.ListMap
import scala.language.implicitConversions

import zio.{Chunk, NonEmptyChunk}

import zio.http.codec._

/**
 * A pattern for matching paths. Patterns may contain literals or variables,
 * such as integer, long, string, or UUID variables.
 *
 * Typically, your entry point constructor for a route pattern would be Method:
 *
 * {{{
 * import zio.http.Method
 * import zio.http.RoutePattern.Segment._
 *
 * val routePattern = Method.GET / "users" / int("user-id") / "posts" / string("post-id")
 * }}}
 */
sealed trait RoutePattern[A] { self =>
  import RoutePattern._

  /**
   * Attaches documentation to the route pattern, which may be used when
   * generating developer docs for a route.
   */
  def ??(doc: Doc): RoutePattern[A]

  /**
   * Returns a new pattern that is extended with the specified segment pattern.
   */
  final def /[B](segment: RoutePattern.Segment[B])(implicit combiner: Combiner[A, B]): RoutePattern[combiner.Out] =
    RoutePattern.Child[A, B, combiner.Out](this, segment, combiner)

  /**
   * Creates a route from this pattern and the specified handler.
   */
  final def ->[Env, Err](handler: Handler[Env, Err, Request, Response])(implicit ev: A =:= Unit): Route[Env, Err] =
    Route.unhandled(self.asType[Unit], (_: Unit) => handler)

  /**
   * Creates a route from this pattern and the specified handler.
   */
  final def ->[Env, Err](handler: A => Handler[Env, Err, Request, Response]): Route[Env, Err] =
    Route.unhandled(self, handler)

  final def asType[B](implicit ev: A =:= B): RoutePattern[B] = self.asInstanceOf[RoutePattern[B]]

  /**
   * Decodes a method and path into a value of type `A`.
   */
  final def decode(method: Method, path: Path): Either[String, A] = {
    import zio.http.RoutePattern.Opt._

    val instructions = optimize
    val segments     = path.segments

    var i                           = 0
    var j                           = 0
    var fail                        = ""
    val stack: java.util.Deque[Any] = new java.util.ArrayDeque[Any](2)
    while (i < instructions.length) {
      val opt = instructions(i)

      opt match {
        case MethodOpt(m) =>
          if (m != method) {
            fail = s"Expected HTTP method ${method} but found method ${m}"
            i = instructions.length
          } else {
            stack.push(())
          }

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
   * Returns the documentation for the route pattern, if any.
   */
  def doc: Doc

  /**
   * Encodes a value of type `A` into the method and path that this route
   * pattern would successfully match against.
   */
  final def encode(value: A): (Method, Path) = (method, format(value))

  /**
   * Formats a value of type `A` into a path. This is useful for embedding paths
   * into HTML that is rendered by the server.
   */
  final def format(value: A): Path = {
    @annotation.tailrec
    def loop(path: RoutePattern[_], value: Any, acc: Path): Path = path match {
      case RoutePattern.Root(_, _) => acc

      case RoutePattern.Child(parent, segment, combiner, _) =>
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
  final def matches(method: Method, path: Path): Boolean =
    decode(method, path).isRight

  /**
   * Returns the method of the route pattern.
   */
  final def method: Method = {
    // Faster than lazy val:
    if (_method eq null) {
      @annotation.tailrec
      def loop(path: RoutePattern[_]): Method = path match {
        case RoutePattern.Root(method, _) => method

        case RoutePattern.Child(parent, _, _, _) => loop(parent)
      }

      _method = loop(self)
    }
    _method
  }

  private var _method: Method = null.asInstanceOf[Method]

  private[http] lazy val optimize: Array[Opt] = {
    @tailrec
    def loop(pattern: RoutePattern[_], acc: Chunk[Opt]): Chunk[Opt] =
      pattern match {
        case RoutePattern.Root(method, _)        => Opt.MethodOpt(method) +: acc
        case Child(parent, segment, combiner, _) =>
          val segmentOpt = segment.asInstanceOf[Segment[_]] match {
            case Segment.Literal(value, _) => Opt.Match(value)
            case Segment.IntSeg(_, _)      => Opt.IntOpt
            case Segment.LongSeg(_, _)     => Opt.LongOpt
            case Segment.Text(_, _)        => Opt.StringOpt
            case Segment.UUID(_, _)        => Opt.UUIDOpt
            case Segment.Trailing(_)       => Opt.TrailingOpt
          }
          loop(parent, segmentOpt +: Opt.Combine(combiner) +: acc)
      }

    loop(self, Chunk.empty).materialize.toArray
  }

  /**
   * Renders the route pattern as a string.
   */
  def render: String = {
    import RoutePattern.Segment
    import RoutePattern.Segment._

    def loop(path: RoutePattern[_]): String = path match {
      case RoutePattern.Root(method, _) => method.name + " "

      case RoutePattern.Child(parent, segment, _, _) =>
        loop(parent) + (segment.asInstanceOf[Segment[_]] match {
          case Literal(value, _) => s"/$value"
          case IntSeg(name, _)   => s"/{$name}"
          case LongSeg(name, _)  => s"/{$name}"
          case Text(name, _)     => s"/{$name}"
          case UUID(name, _)     => s"/{$name}"
        })
    }

    loop(self)
  }

  /**
   * Returns the segments of the route pattern.
   */
  def segments: Chunk[Segment[_]] = {
    def loop(path: RoutePattern[_], acc: Chunk[Segment[_]]): Chunk[Segment[_]] = path match {
      case RoutePattern.Root(_, _) => acc

      case RoutePattern.Child(parent, segment, _, _) => loop(parent, acc :+ segment)
    }

    loop(self, Chunk.empty)
  }

  /**
   * Converts the route pattern into an HttpCodec that produces the same value.
   */
  def toHttpCodec: HttpCodec[HttpCodecType.Path with HttpCodecType.Method, A]

  override def toString(): String = render
}
object RoutePattern          {

  /**
   * A tree of route patterns, indexed by method and path.
   */
  private[http] final case class Tree[+A](roots: ListMap[Method, SegmentSubtree[A]]) { self =>
    def ++[A1 >: A](that: Tree[A1]): Tree[A1] =
      Tree(mergeMaps(self.roots, that.roots)(_ ++ _))

    def add[A1 >: A](routePattern: RoutePattern[_], value: A1): Tree[A1] =
      self ++ Tree(routePattern, value)

    def addAll[A1 >: A](pathPatterns: Iterable[(RoutePattern[_], A1)]): Tree[A1] =
      pathPatterns.foldLeft[Tree[A1]](self) { case (tree, (p, v)) =>
        tree.add(p, v)
      }

    def get(method: Method, path: Path): Chunk[A] =
      roots.get(method) match {
        case None        => Chunk.empty
        case Some(value) => value.get(path)
      }
  }
  private[http] object Tree                                                          {
    def apply[A](routePattern: RoutePattern[_], value: A): Tree[A] = {
      val method   = routePattern.method
      val segments = routePattern.segments

      val subtree = SegmentSubtree.single(segments, value)

      Tree(ListMap(method -> subtree))
    }

    val empty: Tree[Nothing] = Tree(ListMap.empty)
  }
  private[http] final case class SegmentSubtree[+A](
    literals: ListMap[String, SegmentSubtree[A]],
    others: Chunk[(Segment[_], SegmentSubtree[A])],
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
  object SegmentSubtree                                                              {
    def single[A](segments: Iterable[Segment[_]], value: A): SegmentSubtree[A] =
      segments.foldLeft[SegmentSubtree[A]](SegmentSubtree(ListMap(), Chunk.empty, Chunk(value))) {
        case (subtree, segment) =>
          val literals =
            segment match {
              case Segment.Literal(value, _) => ListMap(value -> subtree)
              case _                         => ListMap.empty[String, SegmentSubtree[A]]
            }

          val others =
            segment match {
              case Segment.Literal(_, _) => Chunk.empty
              case _                     => Chunk((segment, subtree))
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
   * Constructs a route pattern from a method and a path literal.
   */
  def apply(method: Method, value: String): RoutePattern[Unit] = {
    val path = Path(value)

    path.segments.foldLeft[RoutePattern[Unit]](Root(method)) { (pathSpec, segment) =>
      pathSpec./[Unit](Segment.literal(segment))
    }
  }

  /**
   * Constructs a route pattern from a method.
   */
  def fromMethod(method: Method): RoutePattern[Unit] = Root(method)

  sealed trait Segment[A] { self =>
    final type Type = A

    def ??(doc: Doc): Segment[A]

    def format(value: A): Path

    // Returns numbrer of segments matched, or -1 if not matched:
    def matches(segments: Chunk[String], index: Int): Int

    def toHttpCodec: HttpCodec[HttpCodecType.Path, A]
  }
  object Segment          {
    def int(name: String): Segment[Int] = Segment.IntSeg(name)

    implicit def literal(value: String): Segment[Unit] = Segment.Literal(value)

    def method(method: Method): RoutePattern[Unit] = Root(method)

    def long(name: String): Segment[Long] = Segment.LongSeg(name)

    def string(name: String): Segment[String] = Segment.Text(name)

    def trailing: Segment[Path] = Segment.Trailing()

    def uuid(name: String): Segment[java.util.UUID] = Segment.UUID(name)

    private[http] final case class Literal(value: String, doc: Doc = Doc.empty) extends Segment[Unit]           {
      def ??(doc: Doc): Literal = copy(doc = this.doc + doc)

      def format(unit: Unit): Path = Path(s"/$value")

      def matches(segments: Chunk[String], index: Int): Int = {
        if (index < 0 || index >= segments.length) -1
        else if (value == segments(index)) 1
        else -1
      }

      def toHttpCodec: HttpCodec[HttpCodecType.Path, Unit] = PathCodec.literal(value)
    }
    private[http] final case class IntSeg(name: String, doc: Doc = Doc.empty)   extends Segment[Int]            {
      def ??(doc: Doc): IntSeg = copy(doc = this.doc + doc)

      def format(value: Int): Path = Path(s"/$value")

      def matches(segments: Chunk[String], index: Int): Int = {
        if (index < 0 || index >= segments.length) -1
        else {
          val segment = segments(index)
          var i       = 0
          var defined = true
          while (i < segment.length) {
            if (!segment.charAt(i).isDigit) {
              defined = false
              i = segment.length
            }
            i += 1
          }
          if (defined && i >= 1) 1 else -1
        }
      }

      def toHttpCodec: HttpCodec[HttpCodecType.Path, Int] = PathCodec.int(name)
    }
    private[http] final case class LongSeg(name: String, doc: Doc = Doc.empty)  extends Segment[Long]           {
      def ??(doc: Doc): LongSeg = copy(doc = this.doc + doc)

      def format(value: Long): Path = Path(s"/$value")

      def matches(segments: Chunk[String], index: Int): Int = {
        if (index < 0 || index >= segments.length) -1
        else {
          val segment = segments(index)
          var i       = 0
          var defined = true
          while (i < segment.length) {
            if (!segment.charAt(i).isDigit) {
              defined = false
              i = segment.length
            }
            i += 1
          }
          if (defined && i >= 1) 1 else -1
        }
      }

      def toHttpCodec: HttpCodec[HttpCodecType.Path, Long] = PathCodec.long(name)
    }
    private[http] final case class Text(name: String, doc: Doc = Doc.empty)     extends Segment[String]         {
      def ??(doc: Doc): Text = copy(doc = this.doc + doc)

      def format(value: String): Path = Path(s"/$value")

      def matches(segments: Chunk[String], index: Int): Int =
        if (index < 0 || index >= segments.length) -1
        else 1

      def toHttpCodec: HttpCodec[HttpCodecType.Path, String] = PathCodec.string(name)
    }
    private[http] final case class UUID(name: String, doc: Doc = Doc.empty)     extends Segment[java.util.UUID] {
      def ??(doc: Doc): UUID = copy(doc = this.doc + doc)

      def format(value: java.util.UUID): Path = Path(s"/$value")

      def matches(segments: Chunk[String], index: Int): Int = {
        if (index < 0 || index >= segments.length) -1
        else {
          val segment = segments(index)

          var i       = 0
          var defined = true
          var group   = 0
          var count   = 0
          while (i < segment.length) {
            val char = segment.charAt(i)
            if ((char >= 48 && char <= 57) || (char >= 65 && char <= 70) || (char >= 97 && char <= 102))
              count += 1
            else if (char == 45) {
              if (
                group > 4 || (group == 0 && count != 8) || ((group == 1 || group == 2 || group == 3) && count != 4) || (group == 4 && count != 12)
              ) {
                defined = false
                i = segment.length
              }
              count = 0
              group += 1
            } else {
              defined = false
              i = segment.length
            }
            i += 1
          }
          if (defined && i == 36) 1 else -1
        }
      }

      def toHttpCodec: HttpCodec[HttpCodecType.Path, java.util.UUID] = PathCodec.uuid(name)
    }

    final case class Trailing(doc: Doc = Doc.empty) extends Segment[Path] { self =>
      def ??(doc: Doc): Segment[Path] = copy(doc = this.doc + doc)

      def format(value: Path): Path = value

      def matches(segments: Chunk[String], index: Int): Int =
        (segments.length - index).max(0)

      def toHttpCodec: HttpCodec[HttpCodecType.Path, Path] = ??? // FIXME
    }
  }

  private[http] final case class Root(method0: Method, doc: Doc = Doc.empty) extends RoutePattern[Unit] {
    def ??(doc: Doc): Root = copy(doc = this.doc + doc)

    def toHttpCodec: HttpCodec[HttpCodecType.Path with HttpCodecType.Method, Unit] =
      MethodCodec.method(method)
  }
  private[http] final case class Child[A, B, C](
    parent: RoutePattern[A],
    segment: Segment[B],
    combiner: Combiner.WithOut[A, B, C],
    doc: Doc = Doc.empty,
  ) extends RoutePattern[C] {
    def ??(doc: Doc): Child[A, B, C] = copy(doc = this.doc + doc)

    def toHttpCodec: HttpCodec[HttpCodecType.Path with HttpCodecType.Method, C] = {
      val parentCodec: HttpCodec[HttpCodecType.Path with HttpCodecType.Method, A] =
        parent.toHttpCodec

      val segmentCodec: HttpCodec[HttpCodecType.Path, B] = segment.toHttpCodec

      parentCodec.++(segmentCodec)(combiner)
    }
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
    case object TrailingOpt                             extends Opt
  }
}
