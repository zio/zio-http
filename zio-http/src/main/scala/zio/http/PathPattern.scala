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
  import PathPattern.Segment

  final def /[B](segment: PathPattern.Segment[B])(implicit combiner: Combiner[A, B]): PathPattern[combiner.Out] =
    PathPattern.Child[A, B, combiner.Out](this, segment, combiner)

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

  def toHttpCodec: HttpCodec[HttpCodecType.Path with HttpCodecType.Method, A]

  override def toString(): String = render
}
object PathPattern          {
  def apply(method: Method, value: String): PathPattern[Unit] = {
    val path = Path(value)

    path.segments.foldLeft[PathPattern[Unit]](Root(method)) { (pathSpec, segment) =>
      pathSpec./[Unit](Segment.literal(segment))
    }
  }
  def fromMethod(method: Method): PathPattern[Unit]           = Root(method)

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

  private[http] final case class Root(method: Method) extends PathPattern[Unit] {
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
}
