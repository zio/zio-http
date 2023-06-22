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

import scala.language.implicitConversions

import zio.Chunk

import zio.http.codec._

/**
 * A pattern for matching paths. Patterns may contain literals or variables,
 * such as integer, long, string, or UUID variables.
 *
 * Typically, your entry point constructor for a path pattern would be Method:
 *
 * {{{
 * import zio.http.Method
 * import zio.http.PathPattern.Segment._
 *
 * val pathPattern = Method.GET / "users" / int("user-id") / "posts" / string("post-id")
 * }}}
 */
sealed trait PathPattern[A] { self =>
  import PathPattern._

  /**
   * Returns a new pattern that is extended with the specified segment pattern.
   */
  final def /[B](segment: PathPattern.Segment[B])(implicit combiner: Combiner[A, B]): PathPattern[combiner.Out] =
    PathPattern.Child[A, B, combiner.Out](this, segment, combiner)

  /**
   * Decodes a method and path into a value of type `A`.
   */
  final def decode(method: Method, path: Path): Either[String, A] = {
    import zio.http.PathPattern.Opt._

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
      }

      i = i + 1
    }
    if (fail != "") Left(fail) else Right(stack.pop().asInstanceOf[A])
  }

  /**
   * Encodes a value of type `A` into a method and path.
   */
  final def encode(value: A): (Method, Path) = (method, format(value))

  /**
   * Formats a value of type `A` into a path. This is useful for embedding paths
   * into HTML that is rendered by the server.
   */
  final def format(value: A): Path = {
    @annotation.tailrec
    def loop(path: PathPattern[_], value: Any, acc: Path): Path = path match {
      case PathPattern.Root(_) => acc

      case PathPattern.Child(parent, segment, combiner) =>
        val (left, right) = combiner.separate(value.asInstanceOf[combiner.Out])

        loop(parent, left, segment.format(right.asInstanceOf[segment.Type]) ++ acc)
    }

    val path = loop(self, value, Path.empty)

    if (path.nonEmpty) path.addLeadingSlash else path
  }

  /**
   * Returns the method of the path pattern.
   */
  final lazy val method: Method = {
    @annotation.tailrec
    def loop(path: PathPattern[_]): Method = path match {
      case PathPattern.Root(method) => method

      case PathPattern.Child(parent, _, _) => loop(parent)
    }

    loop(self)
  }

  private[http] lazy val optimize: Array[Opt] = {
    def loop(pattern: PathPattern[_], acc: Chunk[Opt]): Chunk[Opt] =
      pattern match {
        case PathPattern.Root(method)         => Opt.MethodOpt(method) +: acc
        case Child(parent, segment, combiner) =>
          val segmentOpt = segment.asInstanceOf[Segment[_]] match {
            case Segment.Literal(value) => Opt.Match(value)
            case Segment.IntSeg(_)      => Opt.IntOpt
            case Segment.LongSeg(_)     => Opt.LongOpt
            case Segment.Text(_)        => Opt.StringOpt
            case Segment.UUID(_)        => Opt.UUIDOpt
          }
          loop(parent, segmentOpt +: Opt.Combine(combiner) +: acc)
      }

    loop(self, Chunk.empty).materialize.toArray
  }

  /**
   * Renders the path pattern as a string.
   */
  def render: String = {
    import PathPattern.Segment
    import PathPattern.Segment._

    def loop(path: PathPattern[_]): String = path match {
      case PathPattern.Root(method) => method.name + " "

      case PathPattern.Child(parent, segment, _) =>
        loop(parent) + (segment.asInstanceOf[Segment[_]] match {
          case Literal(value) => s"/$value"
          case IntSeg(name)   => s"/{$name}"
          case LongSeg(name)  => s"/{$name}"
          case Text(name)     => s"/{$name}"
          case UUID(name)     => s"/{$name}"
        })
    }

    loop(self)
  }

  /**
   * Converts the path pattern into an HttpCodec that produces the same value.
   */
  def toHttpCodec: HttpCodec[HttpCodecType.Path with HttpCodecType.Method, A]

  override def toString(): String = render
}
object PathPattern          {
  final case class Tree[+A](roots: Map[Method, SegmentSubtree[A]])
  object Tree           {
    def apply[A](pathPattern: PathPattern[_], value: A): Tree[A] = ???
  }
  sealed trait SegmentSubtree[+A]
  object SegmentSubtree {
    final case class Leaf[A](value: A)                                     extends SegmentSubtree[A]
    final case class Node[A](children: Map[Segment[_], SegmentSubtree[A]]) extends SegmentSubtree[A]
  }

  /**
   * Constructs a path pattern from a method and a path literal.
   */
  def apply(method: Method, value: String): PathPattern[Unit] = {
    val path = Path(value)

    path.segments.foldLeft[PathPattern[Unit]](Root(method)) { (pathSpec, segment) =>
      pathSpec./[Unit](Segment.literal(segment))
    }
  }

  /**
   * Constructs a path pattern from a method.
   */
  def fromMethod(method: Method): PathPattern[Unit] = Root(method)

  sealed trait Segment[A] { self =>
    final type Type = A

    def format(value: A): Path

    def toHttpCodec: HttpCodec[HttpCodecType.Path, A]
  }
  object Segment          {
    def int(name: String): Segment[Int] = Segment.IntSeg(name)

    implicit def literal(value: String): Segment[Unit] = Segment.Literal(value)

    def method(method: Method): PathPattern[Unit] = Root(method)

    def long(name: String): Segment[Long] = Segment.LongSeg(name)

    def string(name: String): Segment[String] = Segment.Text(name)

    def uuid(name: String): Segment[java.util.UUID] = Segment.UUID(name)

    private[http] final case class Literal(value: String) extends Segment[Unit]           {
      def format(unit: Unit): Path = Path(s"/$value")

      def toHttpCodec: HttpCodec[HttpCodecType.Path, Unit] = PathCodec.literal(value)
    }
    private[http] final case class IntSeg(name: String)   extends Segment[Int]            {
      def format(value: Int): Path = Path(s"/$value")

      def toHttpCodec: HttpCodec[HttpCodecType.Path, Int] = PathCodec.int(name)
    }
    private[http] final case class LongSeg(name: String)  extends Segment[Long]           {
      def format(value: Long): Path = Path(s"/$value")

      def toHttpCodec: HttpCodec[HttpCodecType.Path, Long] = PathCodec.long(name)
    }
    private[http] final case class Text(name: String)     extends Segment[String]         {
      def format(value: String): Path = Path(s"/$value")

      def toHttpCodec: HttpCodec[HttpCodecType.Path, String] = PathCodec.string(name)
    }
    private[http] final case class UUID(name: String)     extends Segment[java.util.UUID] {
      def format(value: java.util.UUID): Path = Path(s"/$value")

      def toHttpCodec: HttpCodec[HttpCodecType.Path, java.util.UUID] = PathCodec.uuid(name)
    }
  }

  private[http] final case class Root(method0: Method) extends PathPattern[Unit] {
    def toHttpCodec: HttpCodec[HttpCodecType.Path with HttpCodecType.Method, Unit] =
      MethodCodec.method(method)
  }
  private[http] final case class Child[A, B, C](
    parent: PathPattern[A],
    segment: Segment[B],
    combiner: Combiner.WithOut[A, B, C],
  ) extends PathPattern[C] {
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
  }
}
