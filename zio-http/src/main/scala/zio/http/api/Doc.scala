package zio.http.api

import zio.stacktracer.TracingImplicits.disableAutoTrace

import scala.concurrent.duration.span // scalafix:ok;

/**
 * A `Doc` models documentation for an endpoint or input.
 *
 * `Doc` is composed of optional header and footers, and in-between, a list of
 * Doc-level content items.
 *
 * Doc-level content items, in turn, can be headers, paragraphs, description
 * lists, and enumerations.
 *
 * A `Doc` can be converted into plaintext, JSON, and HTML.
 *
 * Credit to ZIO-CLI for this implementation: http://github.com/zio/zio-cli
 */
sealed trait Doc { self =>
  import Doc._

  def +(that: Doc): Doc =
    (self, that) match {
      case (self, that) if self.isEmpty => that
      case (self, that) if that.isEmpty => self
      case _                            => Doc.Sequence(self, that)
    }

  def |(that: Doc): Doc = if (self.isEmpty) that else self

  def getSpan: Span =
    self match {
      case Doc.Header(value, _) => value
      case Doc.Paragraph(value) => value
      case _                    => Doc.Span.empty
    }

  def isEmpty: Boolean =
    self match {
      case Doc.Empty                 => true
      case Doc.DescriptionList(xs)   => xs.forall(_._2.isEmpty)
      case Doc.Sequence(left, right) => left.isEmpty && right.isEmpty
      case Doc.Enumeration(xs)       => xs.forall(_.isEmpty)
      case _                         => false
    }

  def isHeader: Boolean =
    self match {
      case Doc.Header(_, _)      => true
      case Doc.Sequence(left, _) => left.isHeader
      case _                     => false
    }

  def isParagraph: Boolean =
    self match {
      case Doc.Paragraph(_)      => true
      case Doc.Sequence(left, _) => left.isParagraph
      case _                     => false
    }

  def isDescriptionList: Boolean =
    self match {
      case Doc.DescriptionList(_) => true
      case Doc.Sequence(left, _)  => left.isDescriptionList
      case _                      => false
    }

  def isEnumeration: Boolean =
    self match {
      case Doc.Enumeration(_)    => true
      case Doc.Sequence(left, _) => left.isEnumeration
      case _                     => false
    }

  def isSequence: Boolean =
    self match {
      case Doc.Sequence(_, _) => true
      case _                  => false
    }

  def mapDescriptionList(f: (Doc.Span, Doc) => (Doc.Span, Doc)): Doc =
    self match {
      case Doc.DescriptionList(list) => Doc.DescriptionList(list.map(f.tupled))
      case x                         => x
    }

  def toHTMLSnippet: String = {

    val writer = new StringBuilder

    val escape: String => String =
      _.replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")

    def renderSpan: Span => StringBuilder = {
      case Span.Text(value) => writer.append(escape(value))
      case Span.Code(value) => writer.append(s"<pre><code>${escape(value)}</code></pre>")
      case Span.URI(value)  => writer.append(s"""<a href="$value">$value</a>""")
      case Span.Weak(value) => renderSpan(value)

      case Span.Strong(value) =>
        writer.append("<b>")
        renderSpan(value)
        writer.append("</b>")

      case Span.Error(value) =>
        writer.append(s"""<span style="color:red">""")
        renderSpan(value)
        writer.append("</span>")

      case Span.Sequence(left, right) =>
        renderSpan(left)
        renderSpan(right)
    }

    def render: Doc => StringBuilder = {
      case Doc.Empty => writer

      case Doc.Header(value, level) =>
        writer.append(s"<h$level>")
        renderSpan(value)
        writer.append(s"</h$level>")

      case Doc.Paragraph(value) =>
        writer.append(s"<p>")
        renderSpan(value)
        writer.append(s"</p>")

      case Doc.DescriptionList(definitions) =>
        definitions.foldLeft(writer) { case (_, (span, helpDoc)) =>
          renderSpan(span)
          render(helpDoc)
        }

      case Doc.Enumeration(elements) =>
        writer.append("<ul>")
        elements.foreach { hd =>
          writer.append("<li>")
          render(hd)
          writer.append("</li>")
        }
        writer.append("</ul>")

      case Doc.Sequence(left, right) =>
        render(left)
        writer.append("<br/>")
        render(right)
    }

    render(this)

    writer.toString()
  }

  def toPlaintext(columnWidth: Int = 100, color: Boolean = true): String = {
    val _ = color

    val writer     = DocWriter(0, columnWidth)
    var uppercase  = false
    var styles     = List.empty[String]
    var lastStyle  = Console.RESET
    var printedSep = 0

    def setStyle(style: String): Unit = styles = style :: styles

    def currentStyle(): String = styles.headOption.getOrElse(Console.RESET)

    def resetStyle(): Unit = styles = styles.drop(1)

    def renderText(text: String): Unit =
      renderSpan(Span.text(text))

    def renderNewline(): Unit =
      if (printedSep < 2) {
        printedSep += 1
        val _ = writer.append("\n")
      }

    def clearSep() = printedSep = 0

    def renderHelpDoc(helpDoc: Doc): Unit =
      helpDoc match {
        case Empty                =>
        case Doc.Header(value, _) =>
          writer.unindent()
          renderNewline()
          uppercase = true
          setStyle(Console.BOLD)
          renderSpan(value)
          resetStyle()
          uppercase = false
          renderNewline()
          writer.indent(2)

        case Doc.Paragraph(value) =>
          renderSpan(value)
          renderNewline()

        case Doc.DescriptionList(definitions) =>
          definitions.zipWithIndex.foreach { case ((span, helpDoc), _) =>
            setStyle(Console.BOLD)
            renderSpan(span)
            resetStyle()
            renderNewline()
            writer.indent(2)
            renderHelpDoc(helpDoc)
            writer.unindent()
            renderNewline()
          }

        case Doc.Enumeration(elements) =>
          elements.zipWithIndex.foreach { case (helpDoc, _) =>
            renderText("- ")
            renderHelpDoc(helpDoc)
          }
          writer.unindent()

        case Doc.Sequence(left, right) =>
          renderHelpDoc(left)
          renderNewline()
          renderHelpDoc(right)
      }

    def renderSpan(span: Span): Unit = {
      clearSep()
      val _ = span match {
        case Span.Text(value) =>
          if (color && (lastStyle != currentStyle())) {
            writer.append(currentStyle())
            lastStyle = currentStyle()
          }

          writer.append(if (uppercase) value.toUpperCase() else value)

        case Span.Code(value) =>
          setStyle(Console.WHITE)
          writer.append(value)
          resetStyle()

        case Span.Error(value) =>
          setStyle(Console.RED)
          renderSpan(value)
          resetStyle()

        case Span.Weak(value) =>
          setStyle(Console.BOLD)
          renderSpan(value)
          resetStyle()

        case Span.Strong(value) =>
          setStyle(Console.BOLD)
          renderSpan(value)
          resetStyle()

        case Span.URI(value) =>
          setStyle(Console.UNDERLINED)
          renderSpan(Span.text(value.toASCIIString()))
          resetStyle()

        case Span.Sequence(left, right) =>
          renderSpan(left)
          renderSpan(right)
      }
    }

    renderHelpDoc(this)

    writer.toString() + (if (color) Console.RESET else "")
  }

  def toCommonMark: String = {
    val writer = new StringBuilder

    def renderSpan: Span => StringBuilder = {
      case Span.Text(value) => writer.append(value)
      case Span.Code(value) => writer.append(s"```$value```")
      case Span.URI(value)  => writer.append(s"[$value]($value)")
      case Span.Weak(value) =>
        writer.append("""<span style="font-weight:lighter">""")
        renderSpan(value)
        writer.append("</span>")

      case Span.Strong(value) =>
        writer.append("**")
        renderSpan(value)
        writer.append("**")

      case Span.Error(value) =>
        writer.append(s"""<span style="color:red">""")
        renderSpan(value)
        writer.append("</span>")

      case Span.Sequence(left, right) =>
        renderSpan(left)
        renderSpan(right)
    }

    def render: Doc => StringBuilder = {
      case Doc.Empty => writer

      case Doc.Header(value, level) =>
        writer.append(s"${"#" * level} ")
        renderSpan(value)
        writer.append("\n\n")

      case Doc.Paragraph(value) =>
        renderSpan(value)
        writer.append("\n\n")

      case Doc.DescriptionList(definitions) =>
        definitions.foldLeft(writer) { case (_, (span, helpDoc)) =>
          renderSpan(span)
          writer.append(":\n")
          render(helpDoc)
        }

      case Doc.Enumeration(elements) =>
        elements.foldLeft(writer) { case (_, helpDoc) =>
          writer.append("- ")
          render(helpDoc)
        }

      case Doc.Sequence(left, right) =>
        render(left)
        render(right)

    }

    render(this)
    writer.toString()
  }
}
object Doc       {
  case object Empty                                                extends Doc
  final case class Header(value: Span, level: Int)                 extends Doc
  final case class Paragraph(value: Span)                          extends Doc
  final case class DescriptionList(definitions: List[(Span, Doc)]) extends Doc
  final case class Enumeration(elements: List[Doc])                extends Doc { self =>
    def flatten: Enumeration =
      Enumeration(
        self.elements.flatMap {
          case Enumeration(elements) => elements
          case other                 => List(other)
        },
      )
  }

  final case class Sequence(left: Doc, right: Doc) extends Doc

  def blocks(bs: Iterable[Doc]): Doc =
    if (bs.isEmpty) Doc.Empty else blocks(bs.head, bs.tail.toSeq: _*)

  def blocks(helpDoc: Doc, helpDocs0: Doc*): Doc =
    helpDocs0.foldLeft(helpDoc)(_ + _)

  def descriptionList(definitions: (Span, Doc)*): Doc = Doc.DescriptionList(definitions.toList)

  val empty: Doc = Empty

  def enumeration(elements: Doc*): Doc =
    Doc.Enumeration(elements.toList).flatten

  def h1(t: String): Doc  = h1(Span.text(t))
  def h1(span: Span): Doc = Doc.Header(span, 1)

  def h2(t: String): Doc  = h2(Span.text(t))
  def h2(span: Span): Doc = Doc.Header(span, 2)

  def h3(t: String): Doc  = h3(Span.text(t))
  def h3(span: Span): Doc = Doc.Header(span, 3)

  def p(t: String): Doc  = Doc.Paragraph(Span.text(t))
  def p(span: Span): Doc = Doc.Paragraph(span)

  sealed trait Span { self =>
    final def +(that: Span): Span = Span.Sequence(self, that)

    final def isEmpty: Boolean = self.size == 0

    final def size: Int =
      self match {
        case Span.Text(value)           => value.length
        case Span.Code(value)           => value.length
        case Span.Error(value)          => value.size
        case Span.Weak(value)           => value.size
        case Span.Strong(value)         => value.size
        case Span.URI(value)            => value.toString.length
        case Span.Sequence(left, right) => left.size + right.size
      }
  }
  object Span       {
    final case class Text(value: String)               extends Span
    final case class Code(value: String)               extends Span
    final case class Error(value: Span)                extends Span
    final case class Weak(value: Span)                 extends Span
    final case class Strong(value: Span)               extends Span
    final case class URI(value: java.net.URI)          extends Span
    final case class Sequence(left: Span, right: Span) extends Span

    def code(t: String): Span = Span.Code(t)

    def empty: Span = Span.text("")

    def error(span: Span): Span = Span.Error(span)

    def error(t: String): Span = Span.Error(text(t))

    def space: Span = text(" ")

    def spans(span: Span, spans0: Span*): Span = spans(span :: spans0.toList)

    def spans(spans: Iterable[Span]): Span =
      spans.toList.foldLeft(text("")) { case (span, s) =>
        Span.Sequence(span, s)
      }

    def strong(span: Span): Span = Span.Strong(span)

    def strong(t: String): Span = Span.Strong(text(t))

    def text(t: String): Span = Span.Text(t)

    def uri(uri: java.net.URI): Span = Span.URI(uri)

    def weak(span: Span): Span = Span.Weak(span)

    def weak(t: String): Span = Span.Weak(text(t))
  }
}

private[api] class DocWriter(stringBuilder: StringBuilder, startOffset: Int, columnWidth: Int) { self =>
  private var marginStack: List[Int] = List(self.startOffset)

  def append(s: String): DocWriter = {
    if (s.isEmpty) self
    else
      DocWriter.splitNewlines(s) match {
        case None         =>
          if (self.currentColumn + s.length > self.columnWidth) {
            val remainder = self.columnWidth - self.currentColumn

            val lastSpace = {
              val lastSpace = s.take(remainder + 1).lastIndexOf(' ')

              if (lastSpace == -1) remainder else lastSpace
            }

            val before = s.take(lastSpace)
            val after  = s.drop(lastSpace).dropWhile(_ == ' ')

            append(before)
            append("\n")
            append(after)
          } else {
            val padding = self.currentMargin - self.currentColumn
            if (padding > 0) {
              self.stringBuilder.append(DocWriter.margin(padding))
              self.currentColumn += padding
            }
            self.stringBuilder.append(s)
            self.currentColumn += s.length
          }
        case Some(pieces) =>
          pieces.zipWithIndex.foreach { case (piece, _) =>
            append(piece)

            self.stringBuilder.append("\n")
            self.currentColumn = 0
          }
      }

    this
  }

  def currentMargin: Int = self.marginStack.sum

  var currentColumn: Int = self.startOffset

  def indent(adjust: Int): Unit = self.marginStack = adjust :: self.marginStack

  override def toString(): String = stringBuilder.toString()

  def unindent(): Unit = self.marginStack = self.marginStack.drop(1)
}
private[api] object DocWriter                                                                  {
  private def margin(n: Int): String                  = if (n <= 0) "" else List.fill(n)(" ").mkString
  def splitNewlines(s: String): Option[Array[String]] = {
    val count = s.count(_ == '\n')

    if (count == 0) None
    else
      Some {
        val size = if (count == s.length) count else count + 1

        val array = Array.ofDim[String](size)

        var i = 0
        var j = 0
        while (i < s.length) {
          val search   = s.indexOf('\n', i)
          val endIndex = if (search == -1) s.length else search

          array(j) = s.substring(i, endIndex)

          i = endIndex + 1
          j = j + 1
        }
        if (j < array.length) array(j) = ""

        array
      }
  }

  def apply(startOffset: Int, columnWidth: Int): DocWriter = {
    val builder = new StringBuilder
    builder.append(margin(startOffset))
    new DocWriter(builder, startOffset, if (columnWidth <= 0) startOffset + 1 else columnWidth)
  }
}
