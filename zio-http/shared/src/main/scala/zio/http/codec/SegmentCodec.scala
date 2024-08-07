/*
 * Copyright 2023 ZIO HTTP contributors.
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

import scala.annotation.implicitNotFound
import scala.language.implicitConversions

import zio.Chunk

import zio.http.Path
import zio.http.codec.Combiner.WithOut
import zio.http.codec.PathCodec.MetaData
import zio.http.codec.SegmentCodec._

sealed trait SegmentCodec[A] { self =>
  private var _hashCode: Int  = 0
  private var _render: String = ""

  final type Type = A

  override def equals(that: Any): Boolean = that match {
    case that: SegmentCodec[_] => (this.getClass == that.getClass) && (this.render == that.render)
    case _                     => false
  }

  final def example(name: String, example: A): PathCodec[A] =
    PathCodec.segment(self).annotate(MetaData.Examples(Map(name -> example)))

  final def examples(examples: (String, A)*): PathCodec[A] =
    PathCodec.segment(self).annotate(MetaData.Examples(examples.toMap))

  def format(value: A): Path

  override val hashCode: Int = {
    if (_hashCode == 0) _hashCode = (this.getClass.getName(), render).hashCode
    _hashCode
  }

  final def isEmpty: Boolean = self.asInstanceOf[SegmentCodec[_]] match {
    case SegmentCodec.Empty => true
    case _                  => false
  }

  final def ??(doc: Doc): PathCodec[A] = PathCodec.Segment(self).??(doc)

  final def ~[B](
    that: SegmentCodec[B],
  )(implicit combiner: Combiner[A, B], combinable: Combinable[B, SegmentCodec[B]]): SegmentCodec[combiner.Out] =
    combinable.combine(self, that)

  final def ~(that: String)(implicit combiner: Combiner[A, Unit]): SegmentCodec[combiner.Out] =
    self.~(SegmentCodec.literal(that))(combiner, Combinable.combinableLiteral)

  // Returns number of segments matched, or -1 if not matched:
  def matches(segments: Chunk[String], index: Int): Int

  // Returns the last index of the subsegment matched, or -1 if not matched
  def inSegmentUntil(segment: String, from: Int): Int

  final def nonEmpty: Boolean = !isEmpty

  final def render: String = {
    if (_render == "") _render = render("{", "}")
    _render
  }

  final def render(prefix: String, suffix: String): String = {
    val b = new StringBuilder

    def loop(s: SegmentCodec[_]): Unit = {
      s match {
        case _: SegmentCodec.Empty.type            => ()
        case SegmentCodec.Literal(value)           =>
          b.appendAll(value)
        case SegmentCodec.IntSeg(name)             =>
          b.appendAll(prefix)
          b.appendAll(name)
          b.appendAll(suffix)
        case SegmentCodec.LongSeg(name)            =>
          b.appendAll(prefix)
          b.appendAll(name)
          b.appendAll(suffix)
        case SegmentCodec.Text(name)               =>
          b.appendAll(prefix)
          b.appendAll(name)
          b.appendAll(suffix)
        case SegmentCodec.BoolSeg(name)            =>
          b.appendAll(prefix)
          b.appendAll(name)
          b.appendAll(suffix)
        case SegmentCodec.UUID(name)               =>
          b.appendAll(prefix)
          b.appendAll(name)
          b.appendAll(suffix)
        case SegmentCodec.Combined(left, right, _) =>
          loop(left)
          loop(right)
        case _: SegmentCodec.Trailing.type         =>
          b.appendAll("...")
      }
    }
    if (self ne SegmentCodec.Empty) b.append('/')
    loop(self.asInstanceOf[SegmentCodec[_]])
    b.result()
  }

  final def transform[A2](f: A => A2)(g: A2 => A): PathCodec[A2] =
    PathCodec.Segment(self).transform(f)(g)

  final def transformOrFail[A2](f: A => Either[String, A2])(g: A2 => Either[String, A]): PathCodec[A2] =
    PathCodec.Segment(self).transformOrFail(f)(g)

  final def transformOrFailLeft[A2](f: A => Either[String, A2])(g: A2 => A): PathCodec[A2] =
    PathCodec.Segment(self).transformOrFailLeft(f)(g)

  final def transformOrFailRight[A2](f: A => A2)(g: A2 => Either[String, A]): PathCodec[A2] =
    PathCodec.Segment(self).transformOrFailRight(f)(g)
}
object SegmentCodec          {

  @implicitNotFound("Segments of type ${B} cannot be appended to a multi-value segment")
  sealed trait Combinable[B, S <: SegmentCodec[B]] {
    def combine[A](self: SegmentCodec[A], that: SegmentCodec[B])(implicit
      combiner: Combiner[A, B],
    ): SegmentCodec[combiner.Out]
  }
  private[codec] object Combinable                 {

    implicit val combinableString: Combinable[String, SegmentCodec[String]] =
      new Combinable[String, SegmentCodec[String]] {
        override def combine[A](self: SegmentCodec[A], that: SegmentCodec[String])(implicit
          combiner: Combiner[A, String],
        ): SegmentCodec[combiner.Out] = {
          self match {
            case SegmentCodec.Empty                => that.asInstanceOf[SegmentCodec[combiner.Out]]
            case SegmentCodec.Text(name)           =>
              throw new IllegalArgumentException(
                "Cannot combine two string segments. Their names are " + name + " and " + that
                  .asInstanceOf[SegmentCodec.Text]
                  .name,
              )
            case c: SegmentCodec.Combined[_, _, _] =>
              val last = c.flattened.last
              last match {
                case text: SegmentCodec.Text =>
                  throw new IllegalArgumentException(
                    "Cannot combine two string segments. Their names are" + text.name + " and " + that
                      .asInstanceOf[Text]
                      .name,
                  )
                case _                       =>
                  SegmentCodec.Combined(self, that, combiner.asInstanceOf[WithOut[A, String, combiner.Out]])
              }
            case _                                 =>
              SegmentCodec.Combined(self, that, combiner.asInstanceOf[Combiner.WithOut[A, String, combiner.Out]])
          }
        }
      }
    implicit val combinableInt: Combinable[Int, SegmentCodec[Int]]          =
      new Combinable[Int, SegmentCodec[Int]] {
        override def combine[A](self: SegmentCodec[A], that: SegmentCodec[Int])(implicit
          combiner: Combiner[A, Int],
        ): SegmentCodec[combiner.Out] = {
          self match {
            case SegmentCodec.Empty                => that.asInstanceOf[SegmentCodec[combiner.Out]]
            case SegmentCodec.IntSeg(name)         =>
              throw new IllegalArgumentException(
                "Cannot combine two numeric segments. Their names are " + name + " and " + that
                  .asInstanceOf[SegmentCodec.IntSeg]
                  .name,
              )
            case SegmentCodec.LongSeg(name)        =>
              throw new IllegalArgumentException(
                "Cannot combine two numeric segments. Their names are " + name + " and " + that
                  .asInstanceOf[SegmentCodec.IntSeg]
                  .name,
              )
            case c: SegmentCodec.Combined[_, _, _] =>
              val last = c.flattened.last
              if (last.isInstanceOf[SegmentCodec.IntSeg] || last.isInstanceOf[SegmentCodec.LongSeg]) {
                val lastName =
                  last match {
                    case SegmentCodec.IntSeg(name)  => name
                    case SegmentCodec.LongSeg(name) => name
                    case _                          => ""
                  }
                throw new IllegalArgumentException(
                  "Cannot combine two numeric segments. Their names are " + lastName + " and " + that
                    .asInstanceOf[SegmentCodec.IntSeg]
                    .name,
                )
              } else {
                SegmentCodec.Combined(self, that, combiner.asInstanceOf[Combiner.WithOut[A, Int, combiner.Out]])
              }
            case _                                 =>
              SegmentCodec.Combined(self, that, combiner.asInstanceOf[Combiner.WithOut[A, Int, combiner.Out]])
          }
        }
      }
    implicit val combinableLong: Combinable[Long, SegmentCodec[Long]]       =
      new Combinable[Long, SegmentCodec[Long]] {
        override def combine[A](self: SegmentCodec[A], that: SegmentCodec[Long])(implicit
          combiner: Combiner[A, Long],
        ): SegmentCodec[combiner.Out] = {
          self match {
            case SegmentCodec.Empty                => that.asInstanceOf[SegmentCodec[combiner.Out]]
            case SegmentCodec.IntSeg(name)         =>
              throw new IllegalArgumentException(
                "Cannot combine two numeric segments. Their names are " + name + " and " + that
                  .asInstanceOf[SegmentCodec.LongSeg]
                  .name,
              )
            case SegmentCodec.LongSeg(name)        =>
              throw new IllegalArgumentException(
                "Cannot combine two numeric segments. Their names are " + name + " and " + that
                  .asInstanceOf[SegmentCodec.LongSeg]
                  .name,
              )
            case c: SegmentCodec.Combined[_, _, _] =>
              val last = c.flattened.last
              if (last.isInstanceOf[SegmentCodec.IntSeg] || last.isInstanceOf[SegmentCodec.LongSeg]) {
                val lastName =
                  last match {
                    case SegmentCodec.IntSeg(name)  => name
                    case SegmentCodec.LongSeg(name) => name
                    case _                          => ""
                  }
                throw new IllegalArgumentException(
                  "Cannot combine two numeric segments. Their names are " + lastName + " and " + that
                    .asInstanceOf[SegmentCodec.LongSeg]
                    .name,
                )
              } else {
                SegmentCodec.Combined(self, that, combiner.asInstanceOf[Combiner.WithOut[A, Long, combiner.Out]])
              }
            case _                                 =>
              SegmentCodec.Combined(self, that, combiner.asInstanceOf[Combiner.WithOut[A, Long, combiner.Out]])
          }
        }
      }
    implicit val combinableBool: Combinable[Boolean, SegmentCodec[Boolean]] =
      new Combinable[Boolean, SegmentCodec[Boolean]] {
        override def combine[A](self: SegmentCodec[A], that: SegmentCodec[Boolean])(implicit
          combiner: Combiner[A, Boolean],
        ): SegmentCodec[combiner.Out] = {
          self match {
            case SegmentCodec.Empty => that.asInstanceOf[SegmentCodec[combiner.Out]]
            case _                  =>
              SegmentCodec.Combined(self, that, combiner.asInstanceOf[Combiner.WithOut[A, Boolean, combiner.Out]])
          }
        }
      }
    implicit val combinableUUID: Combinable[UUID, SegmentCodec[UUID]]       =
      new Combinable[UUID, SegmentCodec[UUID]] {
        override def combine[A](self: SegmentCodec[A], that: SegmentCodec[UUID])(implicit
          combiner: Combiner[A, UUID],
        ): SegmentCodec[combiner.Out] = {
          self match {
            case SegmentCodec.Empty => that.asInstanceOf[SegmentCodec[combiner.Out]]
            case _                  =>
              SegmentCodec.Combined(self, that, combiner.asInstanceOf[Combiner.WithOut[A, UUID, combiner.Out]])
          }
        }
      }
    implicit val combinableLiteral: Combinable[Unit, SegmentCodec[Unit]]    =
      new Combinable[Unit, SegmentCodec[Unit]] {
        override def combine[A](self: SegmentCodec[A], that: SegmentCodec[Unit])(implicit
          combiner: Combiner[A, Unit],
        ): SegmentCodec[combiner.Out] = {
          self match {
            case SegmentCodec.Empty          => that.asInstanceOf[SegmentCodec[combiner.Out]]
            case SegmentCodec.Literal(value) =>
              SegmentCodec
                .Literal(value + that.asInstanceOf[SegmentCodec.Literal].value)
                .asInstanceOf[SegmentCodec[combiner.Out]]
            case SegmentCodec.Combined(l, r, c) if r.isInstanceOf[SegmentCodec.Literal] =>
              SegmentCodec
                .Combined(
                  l.asInstanceOf[SegmentCodec[Any]],
                  SegmentCodec
                    .Literal(r.asInstanceOf[SegmentCodec.Literal].value + that.asInstanceOf[SegmentCodec.Literal].value)
                    .asInstanceOf[SegmentCodec[Any]],
                  c.asInstanceOf[Combiner.WithOut[Any, Any, Any]],
                )
                .asInstanceOf[SegmentCodec[combiner.Out]]
            case _                                                                      =>
              SegmentCodec.Combined(self, that, combiner.asInstanceOf[Combiner.WithOut[A, Unit, combiner.Out]])
          }
        }
      }
  }

  def bool(name: String): SegmentCodec[Boolean] = SegmentCodec.BoolSeg(name)

  val empty: SegmentCodec[Unit] = SegmentCodec.Empty

  def int(name: String): SegmentCodec[Int] = SegmentCodec.IntSeg(name)

  implicit def literal(value: String): SegmentCodec[Unit] =
    SegmentCodec.Literal(value)

  def long(name: String): SegmentCodec[Long] = SegmentCodec.LongSeg(name)

  def string(name: String): SegmentCodec[String] = SegmentCodec.Text(name)

  def trailing: SegmentCodec[Path] = SegmentCodec.Trailing

  def uuid(name: String): SegmentCodec[java.util.UUID] = SegmentCodec.UUID(name)

  private[http] case object Empty extends SegmentCodec[Unit] { self =>

    def format(unit: Unit): Path = Path(s"")

    def matches(segments: Chunk[String], index: Int): Int = 0

    override def inSegmentUntil(segment: String, from: Int): Int = from

  }

  private[http] final case class Literal(value: String) extends SegmentCodec[Unit] {

    def format(unit: Unit): Path = Path(s"/$value")

    def matches(segments: Chunk[String], index: Int): Int = {
      if (index < 0 || index >= segments.length) -1
      else if (value == segments(index)) 1
      else -1
    }

    override def inSegmentUntil(segment: String, from: Int): Int =
      if (segment.startsWith(value, from)) from + value.length
      else -1

  }

  private[http] final case class BoolSeg(name: String) extends SegmentCodec[Boolean] {

    def format(value: Boolean): Path = Path(s"/$value")

    def matches(segments: Chunk[String], index: Int): Int =
      if (index < 0 || index >= segments.length) -1
      else {
        val segment = segments(index)

        if (segment == "true" || segment == "false") 1 else -1
      }

    override def inSegmentUntil(segment: String, from: Int): Int =
      if (segment.startsWith("true", from)) from + 4
      else if (segment.startsWith("false", from)) from + 5
      else -1

  }

  private[http] final case class IntSeg(name: String) extends SegmentCodec[Int] {

    def format(value: Int): Path = Path(s"/$value")

    def matches(segments: Chunk[String], index: Int): Int =
      if (index < 0 || index >= segments.length) -1
      else {
        val lastIndex = inSegmentUntil(segments(index), 0)
        if (lastIndex == -1 || lastIndex + 1 != segments(index).length) -1
        else 1
      }

    override def inSegmentUntil(segment: String, from: Int): Int =
      if (segment.isEmpty || from >= segment.length) {
        -1
      } else {
        var i          = from
        val isNegative = segment.charAt(i) == '-'
        // 10 digits is the maximum for an Int
        val maxDigits  = if (isNegative) 11 else 10
        if (segment.length > 1 && isNegative) i += 1
        while (i + 1 < segment.length && i - from < maxDigits && segment.charAt(i).isDigit) i += 1
        i
      }

  }

  private[http] final case class LongSeg(name: String) extends SegmentCodec[Long] {

    def format(value: Long): Path = Path(s"/$value")

    def matches(segments: Chunk[String], index: Int): Int = {
      if (index < 0 || index >= segments.length) -1
      else {
        val lastIndex = inSegmentUntil(segments(index), 0)
        if (lastIndex == -1 || lastIndex + 1 != segments(index).length) -1
        else 1
      }
    }

    override def inSegmentUntil(segment: String, from: Int): Int = {
      if (segment.isEmpty || from >= segment.length) {
        -1
      } else {
        var i          = from
        val isNegative = segment.charAt(i) == '-'
        // 19 digits is the maximum for a Long
        val maxDigits  = if (isNegative) 20 else 19
        if (segment.length > 1 && isNegative) i += 1
        while (i + 1 < segment.length && i - from < maxDigits && segment.charAt(i).isDigit) i += 1
        i
      }
    }

  }
  private[http] final case class Text(name: String) extends SegmentCodec[String] {

    def format(value: String): Path = Path(s"/$value")

    def matches(segments: Chunk[String], index: Int): Int =
      if (index < 0 || index >= segments.length) -1
      else 1

    override def inSegmentUntil(segment: String, from: Int): Int =
      segment.length

  }
  private[http] final case class UUID(name: String) extends SegmentCodec[java.util.UUID] {

    def format(value: java.util.UUID): Path = Path(s"/$value")

    def matches(segments: Chunk[String], index: Int): Int = {
      if (index < 0 || index >= segments.length) -1
      else if (isValidUUID(segments(index))) 1
      else -1
    }

    private def isValidUUID(segment: String): Boolean = {
      if (segment.length != 36) false
      else {
        val uuidPattern = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
        segment.matches(uuidPattern)
      }
    }

    override def inSegmentUntil(segment: String, from: Int): Int =
      UUID.inUUIDUntil(segment, from)
  }

  private[http] object UUID {
    def inUUIDUntil(segment: String, from: Int): Int = {
      var i       = from
      var defined = true
      var group   = 0
      var count   = 0
      if (segment.length + from < 36) return -1
      val until   = from + 36
      while (i < until && defined) {
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
      if (defined && until == i) i else -1
    }

  }

  private[http] final case class Combined[A, B, C](
    left: SegmentCodec[A],
    right: SegmentCodec[B],
    combiner: Combiner.WithOut[A, B, C],
  ) extends SegmentCodec[C] { self =>
    val flattened: List[SegmentCodec[_]] = {
      def loop(s: SegmentCodec[_]): List[SegmentCodec[_]] = s match {
        case SegmentCodec.Combined(l, r, _) => loop(l) ++ loop(r)
        case _                              => List(s)
      }
      loop(self)
    }
    override def format(value: C): Path  = {
      val (l, r) = combiner.separate(value)
      val lf     = left.format(l)
      val rf     = right.format(r)
      lf ++ rf
    }

    override def matches(segments: Chunk[String], index: Int): Int =
      if (index < 0 || index >= segments.length) -1
      else {
        val segment = segments(index)
        val length  = segment.length
        var from    = 0
        var i       = 0
        while (i < flattened.length) {
          if (from >= length) return -1
          val codec = flattened(i)
          val s     = codec.inSegmentUntil(segment, from)
          if (s == -1) return -1
          from = s
          i += 1
        }
        1
      }

    override def inSegmentUntil(segment: String, from: Int): Int = {
      var i = from
      var j = 0
      while (j < flattened.length) {
        val codec = flattened(j)
        val s     = codec.inSegmentUntil(segment, i)
        if (s == -1) return -1
        i = s
        j += 1
      }
      i
    }

  }

  case object Trailing extends SegmentCodec[Path] { self =>
    def format(value: Path): Path = value

    def matches(segments: Chunk[String], index: Int): Int =
      (segments.length - index).max(0)

    override def inSegmentUntil(segment: String, from: Int): Int =
      segment.length
  }

}
