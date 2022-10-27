package zio.http.api

import zio.stacktracer.TracingImplicits.disableAutoTrace

/**
 * A `Doc` models documentation for an endpoint or input.
 */
sealed trait Doc { self =>
  import Doc._

  def +(that: Doc): Doc =
    (self, that) match {
      case (self, that) if self.isEmpty => that
      case (self, that) if that.isEmpty => self
      case _                            => Doc.Sequence(self, that)
    }

  def isEmpty: Boolean =
    self match {
      case Doc.Empty                 => true
      case Doc.DescriptionList(xs)   => xs.forall(_._2.isEmpty)
      case Doc.Sequence(left, right) => left.isEmpty && right.isEmpty
      case Doc.Listing(xs, _)        => xs.forall(_.isEmpty)
      case _                         => false
    }

  def toCommonMark: String = {
    val writer = new StringBuilder

    def renderSpan(span: Span, indent: Int): String = {
      def render(s: String): String = "  " * indent + s

      span match {
        case Span.Text(value)           => render(value)
        case Span.Code(value)           => render(s"```${value.trim}```")
        case Span.Link(value, text)     => render(s"[${text.getOrElse(value)}]($value)")
        case Span.Bold(value)           =>
          s"${render("**")}${renderSpan(value, indent).trim}${render("**")}"
        case Span.Italic(value)         =>
          s"${render("*")}${renderSpan(value, indent).trim}${render("*")}"
        case Span.Error(value)          =>
          s"${render(s"""<span style="color:red">""")}${render(value)}${render("</span>")}"
        case Span.Sequence(left, right) =>
          renderSpan(left, indent)
          renderSpan(right, indent)
      }
    }

    def render(doc: Doc, indent: Int = 0): Unit = {
      def append(s: String): Unit = {
        writer.append("  " * indent).append(s)
        ()
      }

      doc match {

        case Doc.Empty => ()

        case Doc.Header(value, level) =>
          append(s"${"#" * level} $value\n\n")

        case Doc.Paragraph(value) =>
          writer.append(renderSpan(value, indent))
          writer.append("\n\n")
          ()

        case Doc.DescriptionList(definitions) =>
          definitions.foreach { case (span, helpDoc) =>
            writer.append(renderSpan(span, indent))
            append(":\n")
            render(helpDoc, indent)
          }

        case Doc.Listing(elements, listingType) =>
          elements.zipWithIndex.foreach { case (doc, i) =>
            if (listingType == ListingType.Ordered) append(s"${i + 1}. ") else append("- ")
            doc match {
              case Listing(_, _)         =>
                writer.append("\n")
                render(doc, indent + 1)
              case Sequence(left, right) =>
                render(left, indent)
                writer.deleteCharAt(writer.length - 1)
                render(right, indent + 1)
              case _                     =>
                render(doc, indent)
            }
            writer.deleteCharAt(writer.length - 1)
          }

        case Doc.Sequence(left, right) =>
          render(left, indent)
          render(right, indent)

      }
    }

    render(this)
    writer.toString()
  }

  def toHtmlSnippet: String = {
    val writer = new StringBuilder

    def renderSpan(span: Span, indent: Int): String = {
      def render(s: String): String = ("  " * indent) + s

      span match {
        case Span.Text(value)           => render(value.replace("\n", "<br/>"))
        case Span.Code(value)           => render(s"<code>${value.trim.replace("\n", "<br/>")}</code>")
        case Span.Link(value, text)     => s"""<a href="${value}">${text.getOrElse(value)}</a>"""
        case Span.Bold(value)           =>
          s"${"<b>"}${renderSpan(value, indent).trim}${"</b>"}"
        case Span.Italic(value)         =>
          s"${"<i>"}${renderSpan(value, indent).trim}${"</i>"}"
        case Span.Error(value)          =>
          s"${s"""<span style="color:red">"""}${render(value).replace("\n", "<br/>")}${"</span>"}"
        case Span.Sequence(left, right) =>
          renderSpan(left, indent)
          renderSpan(right, indent)
      }
    }

    def render(doc: Doc, indent: Int = 0): Unit = {
      def append(s: String, indent: Int = indent): Unit = {
        writer.append("  " * indent).append(s)
        ()
      }
      def newLine(): Unit                               = append("\n", 0)

      doc match {

        case Doc.Empty => ()

        case Doc.Header(value, level) =>
          append(s"<h$level>$value</h$level>\n\n")

        case Doc.Paragraph(value) =>
          newLine()
          append(s"<p>")
          newLine()
          append(renderSpan(value, 0), indent + 1)
          newLine()
          append("</p>")
          newLine()

        case Doc.DescriptionList(definitions) =>
          append("<dl>")
          definitions.foreach { case (span, helpDoc) =>
            newLine()
            append("<dt>", indent + 1)
            newLine()
            append(renderSpan(span, indent + 2))
            newLine()
            append("</dt>", indent + 1)
            newLine()
            append("<dd>", indent + 1)
            render(helpDoc, indent + 2)
            append("</dd>", indent + 1)
            newLine()
          }
          append("</dl>")
          newLine()
          newLine()

        case Doc.Listing(elements, listingType) =>
          if (listingType == ListingType.Ordered) append("<ol>") else append("<ul>")
          newLine()
          elements.foreach { doc =>
            append("<li>", indent + 1)
            render(doc, indent + 2)
            append("</li>", indent + 1)
            newLine()
          }
          if (listingType == ListingType.Ordered) append("</ol>") else append("</ul>")
          newLine()

        case Doc.Sequence(left, right) =>
          render(left, indent)
          render(right, indent)

      }
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
          renderSpan(Span.text(value))
          resetStyle()
          uppercase = false
          renderNewline()
          renderNewline()
          writer.indent(2)

        case Doc.Paragraph(value) =>
          renderSpan(value)
          renderNewline()
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

        case Doc.Listing(elements, listingType) =>
          elements.zipWithIndex.foreach { case (helpDoc, i) =>
            if (listingType == ListingType.Ordered) renderText(s"${i + 1}. ") else renderText("- ")
            helpDoc match {
              case Doc.Listing(_, _)     =>
                writer.indent(2)
                renderHelpDoc(helpDoc)
                writer.unindent()
              case Sequence(left, right) =>
                renderHelpDoc(left)
                writer.deleteLastChar()
                writer.indent(2)
                renderHelpDoc(right)
                writer.unindent()
              case _                     =>
                renderHelpDoc(helpDoc)
                writer.deleteLastChar()
            }
          }
          writer.unindent()

        case Doc.Sequence(left, right) =>
          renderHelpDoc(left)
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
          renderSpan(Span.text(value))
          resetStyle()

        case Span.Italic(value) =>
          setStyle(Console.BOLD)
          renderSpan(value)
          resetStyle()

        case Span.Bold(value) =>
          setStyle(Console.BOLD)
          renderSpan(value)
          resetStyle()

        case Span.Link(value, text) =>
          setStyle(Console.UNDERLINED)
          renderSpan(Span.text(text.map(t => s"[$t](${value.toASCIIString})").getOrElse(value.toASCIIString)))
          resetStyle()

        case Span.Sequence(left, right) =>
          renderSpan(left)
          renderSpan(right)
      }
    }

    renderHelpDoc(this)

    writer.toString() + (if (color) Console.RESET else "")
  }

}
object Doc {
  private[api] case object Empty                                                       extends Doc
  private[api] final case class Header(value: String, level: Int)                      extends Doc
  private[api] final case class Paragraph(value: Span)                                 extends Doc
  private[api] final case class DescriptionList(definitions: List[(Span, Doc)])        extends Doc
  private[api] final case class Sequence(left: Doc, right: Doc)                        extends Doc
  private[api] final case class Listing(elements: List[Doc], listingType: ListingType) extends Doc
  private[api] sealed trait ListingType
  private[api] object ListingType {
    private[api] case object Unordered extends ListingType
    private[api] case object Ordered   extends ListingType
  }

  def blocks(bs: Iterable[Doc]): Doc =
    if (bs.isEmpty) Doc.Empty else blocks(bs.head, bs.tail.toSeq: _*)

  def blocks(helpDoc: Doc, helpDocs0: Doc*): Doc =
    helpDocs0.foldLeft(helpDoc)(_ + _)

  def descriptionList(definitions: (Span, Doc)*): Doc = Doc.DescriptionList(definitions.toList)

  val empty: Doc = Empty

  def orderedListing(elements: Doc*): Doc =
    Doc.Listing(elements.toList, ListingType.Ordered)

  def unorderedListing(elements: Doc*): Doc =
    Doc.Listing(elements.toList, ListingType.Unordered)

  def h1(t: String): Doc = Header(t, 1)
  def h2(t: String): Doc = Header(t, 2)
  def h3(t: String): Doc = Header(t, 3)
  def h4(t: String): Doc = Header(t, 4)
  def h5(t: String): Doc = Header(t, 5)

  def p(t: String): Doc  = Doc.Paragraph(Span.text(t))
  def p(span: Span): Doc = Doc.Paragraph(span)

  sealed trait Span { self =>
    final def +(that: Span): Span = Span.Sequence(self, that)

    final def isEmpty: Boolean = self.size == 0

    final def size: Int =
      self match {
        case Span.Text(value)           => value.length
        case Span.Code(value)           => value.length
        case Span.Error(value)          => value.length
        case Span.Bold(value)           => value.size
        case Span.Italic(value)         => value.size
        case Span.Link(value, _)        => value.toString.length
        case Span.Sequence(left, right) => left.size + right.size
      }
  }
  object Span       {
    private[api] final case class Text(value: String)                             extends Span
    private[api] final case class Code(value: String)                             extends Span
    private[api] final case class Error(value: String)                            extends Span
    private[api] final case class Bold(value: Span)                               extends Span
    private[api] final case class Italic(value: Span)                             extends Span
    private[api] final case class Link(value: java.net.URI, text: Option[String]) extends Span
    private[api] final case class Sequence(left: Span, right: Span)               extends Span

    def code(t: String): Span                       = Span.Code(t)
    def empty: Span                                 = Span.text("")
    def error(t: String): Span                      = Span.Error(t)
    def bold(span: Span): Span                      = Span.Bold(span)
    def bold(t: String): Span                       = Span.Bold(text(t))
    def italic(span: Span): Span                    = Span.Italic(span)
    def italic(t: String): Span                     = Span.Italic(text(t))
    def text(t: String): Span                       = Span.Text(t)
    def link(uri: java.net.URI): Span               = Span.Link(uri, None)
    def link(uri: java.net.URI, text: String): Span = Span.Link(uri, Some(text))
    def spans(span: Span, spans0: Span*): Span      = spans(span :: spans0.toList)
    def spans(spans: Iterable[Span]): Span          = spans.toList.foldLeft(empty)(_ + _)

  }
}

private[api] class DocWriter(stringBuilder: StringBuilder, startOffset: Int, columnWidth: Int) { self =>
  private var marginStack: List[Int] = List(self.startOffset)

  def deleteLastChar(): Unit = stringBuilder.deleteCharAt(stringBuilder.length - 1)

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
