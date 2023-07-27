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

  def ??(doc: Doc): SegmentCodec[A]

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
    case SegmentCodec.Empty(_) => true
    case _                     => false
  }

  // Returns number of segments matched, or -1 if not matched:
  def matches(segments: Chunk[String], index: Int): Int

  final def nonEmpty: Boolean = !isEmpty

  final def render: String = {
    if (_render == "") _render = self.asInstanceOf[SegmentCodec[_]] match {
      case SegmentCodec.Empty(_)          => s""
      case SegmentCodec.Literal(value, _) => s"/$value"
      case SegmentCodec.IntSeg(name, _)   => s"/{$name}"
      case SegmentCodec.LongSeg(name, _)  => s"/{$name}"
      case SegmentCodec.Text(name, _)     => s"/{$name}"
      case SegmentCodec.BoolSeg(name, _)  => s"/{$name}"
      case SegmentCodec.UUID(name, _)     => s"/{$name}"
      case SegmentCodec.Trailing(_)       => s"/..."
    }
    _render
  }
}
object SegmentCodec          {
  def bool(name: String): SegmentCodec[Boolean] = SegmentCodec.BoolSeg(name)

  val empty: SegmentCodec[Unit] = SegmentCodec.Empty()

  def int(name: String): SegmentCodec[Int] = SegmentCodec.IntSeg(name)

  implicit def literal(value: String): SegmentCodec[Unit] =
    SegmentCodec.Literal(value)

  def long(name: String): SegmentCodec[Long] = SegmentCodec.LongSeg(name)

  def string(name: String): SegmentCodec[String] = SegmentCodec.Text(name)

  def trailing: SegmentCodec[Path] = SegmentCodec.Trailing()

  def uuid(name: String): SegmentCodec[java.util.UUID] = SegmentCodec.UUID(name)

  private[http] final case class Empty(doc: Doc = Doc.empty) extends SegmentCodec[Unit] {
    def ??(doc: Doc): Empty = copy(doc = this.doc + doc)

    def format(unit: Unit): Path = Path(s"")

    def matches(segments: Chunk[String], index: Int): Int = 0
  }

  private[http] final case class Literal(value: String, doc: Doc = Doc.empty) extends SegmentCodec[Unit]           {
    def ??(doc: Doc): Literal = copy(doc = this.doc + doc)

    def format(unit: Unit): Path = Path(s"/$value")

    def matches(segments: Chunk[String], index: Int): Int = {
      if (index < 0 || index >= segments.length) -1
      else if (value == segments(index)) 1
      else -1
    }
  }
  private[http] final case class BoolSeg(name: String, doc: Doc = Doc.empty)  extends SegmentCodec[Boolean]        {
    def ??(doc: Doc): BoolSeg = copy(doc = this.doc + doc)

    def format(value: Boolean): Path = Path(s"/$value")

    def matches(segments: Chunk[String], index: Int): Int =
      if (index < 0 || index >= segments.length) -1
      else {
        val segment = segments(index)

        if (segment == "true" || segment == "false") 1 else -1
      }
  }
  private[http] final case class IntSeg(name: String, doc: Doc = Doc.empty)   extends SegmentCodec[Int]            {
    def ??(doc: Doc): IntSeg = copy(doc = this.doc + doc)

    def format(value: Int): Path = Path(s"/$value")

    def matches(segments: Chunk[String], index: Int): Int = {
      if (index < 0 || index >= segments.length) -1
      else {
        val SegmentCodec = segments(index)
        var i            = 0
        var defined      = true
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
  }
  private[http] final case class LongSeg(name: String, doc: Doc = Doc.empty)  extends SegmentCodec[Long]           {
    def ??(doc: Doc): LongSeg = copy(doc = this.doc + doc)

    def format(value: Long): Path = Path(s"/$value")

    def matches(segments: Chunk[String], index: Int): Int = {
      if (index < 0 || index >= segments.length) -1
      else {
        val SegmentCodec = segments(index)
        var i            = 0
        var defined      = true
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
  }
  private[http] final case class Text(name: String, doc: Doc = Doc.empty)     extends SegmentCodec[String]         {
    def ??(doc: Doc): Text = copy(doc = this.doc + doc)

    def format(value: String): Path = Path(s"/$value")

    def matches(segments: Chunk[String], index: Int): Int =
      if (index < 0 || index >= segments.length) -1
      else 1
  }
  private[http] final case class UUID(name: String, doc: Doc = Doc.empty)     extends SegmentCodec[java.util.UUID] {
    def ??(doc: Doc): UUID = copy(doc = this.doc + doc)

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
  }

  final case class Trailing(doc: Doc = Doc.empty) extends SegmentCodec[Path] { self =>
    def ??(doc: Doc): SegmentCodec[Path] = copy(doc = this.doc + doc)

    def format(value: Path): Path = value

    def matches(segments: Chunk[String], index: Int): Int =
      (segments.length - index).max(0)
  }
}
