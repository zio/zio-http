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

import scala.language.implicitConversions

import zio.Chunk

import zio.http.Path

sealed trait SegmentCodec[A] { self =>
  private var _hashCode: Int  = 0
  private var _render: String = ""

  final type Type = A

  override def equals(that: Any): Boolean = that match {
    case that: SegmentCodec[_] => (this.getClass == that.getClass) && (this.render == that.render)
    case _                     => false
  }

  def format(value: A): Path

  override val hashCode: Int = {
    if (_hashCode == 0) _hashCode = (this.getClass.getName(), render).hashCode
    _hashCode
  }

  final def isEmpty: Boolean = self.asInstanceOf[SegmentCodec[_]] match {
    case SegmentCodec.Empty => true
    case _                  => false
  }

  final def ~[B](that: SegmentCodec[B])(implicit combiner: Combiner[A, B]): SegmentCodec[combiner.Out] =
    SegmentCodec.Combined(self, that, combiner)

  // Returns number of segments matched, or -1 if not matched:
  def matches(segments: Chunk[String], index: Int): Int

  // Returns the range of the first subsegment matched, or null if not matched:
  def submatch(sub: String, from: Int): Range

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
          b appendAll value
        case SegmentCodec.IntSeg(name)             =>
          b appendAll prefix
          b appendAll name
          b appendAll suffix
        case SegmentCodec.LongSeg(name)            =>
          b appendAll prefix
          b appendAll name
          b appendAll suffix
        case SegmentCodec.Text(name)               =>
          b appendAll prefix
          b appendAll name
          b appendAll suffix
        case SegmentCodec.BoolSeg(name)            =>
          b appendAll prefix
          b appendAll name
          b appendAll suffix
        case SegmentCodec.UUID(name)               =>
          b appendAll prefix
          b appendAll name
          b appendAll suffix
        case SegmentCodec.Combined(left, right, _) =>
          loop(left)
          loop(right)
        case _: SegmentCodec.Trailing.type         =>
          b appendAll "..."
      }
    }
    if (self ne SegmentCodec.Empty) b append '/'
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

    def submatch(sub: String, from: Int): Range = null
  }

  private[http] final case class Literal(value: String) extends SegmentCodec[Unit] {

    def format(unit: Unit): Path = Path(s"/$value")

    def matches(segments: Chunk[String], index: Int): Int = {
      if (index < 0 || index >= segments.length) -1
      else if (value == segments(index)) 1
      else -1
    }

    // TODO: Optimize
    def submatch(sub: String, from: Int): Range = {
      val s = sub.indexOf(value.drop(from))
      if (s == -1) null
      else Range.inclusive(s + from, s + from + value.length - 1)
    }
  }
  private[http] final case class BoolSeg(name: String) extends SegmentCodec[Boolean] {

    def format(value: Boolean): Path = Path(s"/$value")

    def matches(segments: Chunk[String], index: Int): Int =
      if (index < 0 || index >= segments.length) -1
      else {
        val segment = segments(index)

        if (segment == "true" || segment == "false") 1 else -1
      }

    def submatch(sub: String, from: Int): Range = {
      // TODO: Optimize
      sub.drop(from).indexOf("true") match {
        case -1 =>
          sub.drop(from).indexOf("false") match {
            case -1 => null
            case s  => Range.inclusive(s + from, s + from + 4)
          }
        case s  => Range.inclusive(s + from, s + from + 3)
      }
    }
  }
  private[http] final case class IntSeg(name: String) extends SegmentCodec[Int] {

    def format(value: Int): Path = Path(s"/$value")

    def matches(segments: Chunk[String], index: Int): Int = {
      if (index < 0 || index >= segments.length) -1
      else {
        val SegmentCodec = segments(index)
        var i            = 0
        var defined      = true
        if (SegmentCodec.length > 1 && SegmentCodec.charAt(0) == '-') i += 1
        while (i < SegmentCodec.length) {
          if (!SegmentCodec.charAt(i).isDigit) {
            defined = false
            i = SegmentCodec.length
          }
          i += 1
        }
        if (defined && i >= 1) 1 else -1
      }
    }

    def submatch(sub: String, from: Int): Range =
      submatchNumber(sub, from)
  }

  private[http] final case class LongSeg(name: String) extends SegmentCodec[Long] {

    def format(value: Long): Path = Path(s"/$value")

    def matches(segments: Chunk[String], index: Int): Int = {
      if (index < 0 || index >= segments.length) -1
      else {
        val SegmentCodec = segments(index)
        var i            = 0
        var defined      = true
        if (SegmentCodec.length > 1 && SegmentCodec.charAt(0) == '-') i += 1
        while (i < SegmentCodec.length) {
          if (!SegmentCodec.charAt(i).isDigit) {
            defined = false
            i = SegmentCodec.length
          }
          i += 1
        }
        if (defined && i >= 1) 1 else -1
      }
    }

    def submatch(sub: String, from: Int): Range = submatchNumber(sub, from)
  }
  private[http] final case class Text(name: String) extends SegmentCodec[String] {

    def format(value: String): Path = Path(s"/$value")

    def matches(segments: Chunk[String], index: Int): Int =
      if (index < 0 || index >= segments.length) -1
      else 1

    def submatch(sub: String, from: Int): Range = Range.inclusive(from, sub.length - 1)
  }
  private[http] final case class UUID(name: String) extends SegmentCodec[java.util.UUID] {

    def format(value: java.util.UUID): Path = Path(s"/$value")

    def matches(segments: Chunk[String], index: Int): Int = {
      if (index < 0 || index >= segments.length) -1
      else {
        val SegmentCodec = segments(index)

        var i       = 0
        var defined = true
        var group   = 0
        var count   = 0
        while (i < SegmentCodec.length) {
          val char = SegmentCodec.charAt(i)
          if ((char >= 48 && char <= 57) || (char >= 65 && char <= 70) || (char >= 97 && char <= 102))
            count += 1
          else if (char == 45) {
            if (
              group > 4 || (group == 0 && count != 8) || ((group == 1 || group == 2 || group == 3) && count != 4) || (group == 4 && count != 12)
            ) {
              defined = false
              i = SegmentCodec.length
            }
            count = 0
            group += 1
          } else {
            defined = false
            i = SegmentCodec.length
          }
          i += 1
        }
        if (defined && i == 36) 1 else -1
      }
    }

    def submatch(sub: String, from: Int): Range = ???
  }

  private[http] final case class Combined[A, B, C](
    left: SegmentCodec[A],
    right: SegmentCodec[B],
    combiner: Combiner.WithOut[A, B, C],
  ) extends SegmentCodec[C] {
    override def format(value: C): Path = {
      val (l, r) = combiner.separate(value)
      val lf     = left.format(l)
      val rf     = right.format(r)
      lf ++ rf
    }

    override def matches(segments: Chunk[String], index: Int): Int = {
      if (index < 0 || index >= segments.length) -1
      else {
        val SegmentCodec = segments(index)
        val s            = SegmentCodec.length
        val r1           = left.submatch(SegmentCodec, 0)
        val r2           = right.submatch(SegmentCodec, 0)
        if ((r1 eq null) || (r2 eq null)) -1
        else {
          if (r1.start == 0 && r2.end == s - 1 && r1.end >= r2.end - 1) 1
          else -1
        }
      }
    }

    override def submatch(sub: String, from: Int): Range = {
      val r1 = left.submatch(sub, from)
      val r2 = right.submatch(sub, from)
      if (r1.end >= r2.end && r1.start < r2.start) Range.inclusive(r1.start, r2.end)
      else null
    }
  }

  case object Trailing extends SegmentCodec[Path] { self =>
    def format(value: Path): Path = value

    def matches(segments: Chunk[String], index: Int): Int =
      (segments.length - index).max(0)

    override def submatch(sub: String, from: Int): Range = Range.inclusive(from, sub.length - 1)
  }

  private def submatchNumber(sub: String, from: Int): Range = {
    if (sub.isEmpty) return null
    var i     = from
    var first = -1
    var last  = -1
    val size  = sub.length
    while (i < size && last == -1) {
      val c = sub.charAt(i)
      if (first == -1) {
        if (c == '-' && i + 1 < size && sub.charAt(i + 1).isDigit) {
          first = i
          i += 1
        } else if (c.isDigit) {
          first = i
        }
      } else if (!c.isDigit) {
        last = i - 1
      }
      i += 1
    }
    if (first == -1) null
    else if (last == -1) Range.inclusive(first, size - 1)
    else Range.inclusive(first, last)
  }
}
