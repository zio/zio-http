package zio.http.codec

import zio.Chunk

import zio.http.Path

sealed trait SegmentCodec[A] { self =>
  final type Type = A

  def ??(doc: Doc): SegmentCodec[A]

  def format(value: A): Path

  // Returns number of segments matched, or -1 if not matched:
  def matches(segments: Chunk[String], index: Int): Int
}
object SegmentCodec          {
  def int(name: String): SegmentCodec[Int] = SegmentCodec.IntSeg(name)

  implicit def literal(value: String): SegmentCodec[Unit] = SegmentCodec.Literal(value)

  def long(name: String): SegmentCodec[Long] = SegmentCodec.LongSeg(name)

  def string(name: String): SegmentCodec[String] = SegmentCodec.Text(name)

  def trailing: SegmentCodec[Path] = SegmentCodec.Trailing()

  def uuid(name: String): SegmentCodec[java.util.UUID] = SegmentCodec.UUID(name)

  private[http] final case class Literal(value: String, doc: Doc = Doc.empty) extends SegmentCodec[Unit]           {
    def ??(doc: Doc): Literal = copy(doc = this.doc + doc)

    def format(unit: Unit): Path = Path(s"/$value")

    def matches(segments: Chunk[String], index: Int): Int = {
      if (index < 0 || index >= segments.length) -1
      else if (value == segments(index)) 1
      else -1
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
