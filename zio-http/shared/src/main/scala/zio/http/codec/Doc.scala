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

import zio.Chunk
import zio.json.ast.Json

import zio.schema.Schema

import zio.http.codec.Doc.Span.CodeStyle
import zio.http.endpoint.openapi.OpenAPI.Extensions
import zio.http.template

/**
 * A `Doc` models documentation for an endpoint or input. It is a tree of
 * headers, paragraphs, lists and description lists, with optional tags and
 * OpenAPI extensions, that renders to CommonMark, HTML or plain text. Attach
 * one to an [[HttpCodec]] with `??`.
 *
 * {{{
 * import zio.http.codec._
 *
 * val doc: Doc = Doc.h1("Users") + Doc.p("Lists all users.")
 * val markdown: String = doc.toCommonMark
 * // "# Users\n\nLists all users.\n\n"
 *
 * val page: HttpCodec[HttpCodecType.Query, Int] = HttpCodec.query[Int]("page") ?? Doc.p("The page number")
 * }}}
 */
sealed trait Doc { self =>
  import Doc._

  /**
   * Returns a doc with this doc followed by `that`. An empty side is dropped,
   * and the result has the tags of both sides.
   */
  def +(that: Doc): Doc =
    (self, that) match {
      case (self, that) if self.isEmpty           => that
      case (self, that) if that.isEmpty           => self
      case _ if tags.isEmpty && that.tags.isEmpty => Doc.Sequence(self, that)
      case _                                      => Doc.Sequence(self, that).tag(self.tags ++ that.tags)
    }

  /**
   * Returns `true` for `Doc.empty`, for `fromCommonMark("")`, and for lists,
   * sequences and description lists that contain only empty docs. A header, a
   * paragraph or a tagged doc is never empty, even with empty text, and a doc
   * with extensions added by `ext` is never empty.
   */
  def isEmpty: Boolean =
    self match {
      case Doc.Empty                 => true
      case Doc.DescriptionList(xs)   => xs.forall(_._2.isEmpty)
      case Doc.Sequence(left, right) => left.isEmpty && right.isEmpty
      case Doc.Listing(xs, _)        => xs.forall(_.isEmpty)
      case Doc.Raw(value, _)         => value.isEmpty
      case Doc.Extended(doc, exts)   => doc.isEmpty && exts.isEmpty
      case _                         => false
    }

  private[zio] def flattened: Chunk[Doc] =
    self match {
      case Doc.Empty                 => Chunk.empty
      case Doc.Sequence(left, right) => left.flattened ++ right.flattened
      case x                         => Chunk(x)
    }

  /** Returns this doc with `tags` added after its existing tags. */
  def tag(tags: Seq[String]): Doc = self match {
    case Tagged(doc, existingTags) => Tagged(doc, existingTags ++ tags)
    case _                         => Tagged(self, tags.toList)
  }

  /** Returns this doc with `tag` added after its existing tags. */
  def tag(tag: String): Doc =
    self match {
      case Tagged(doc, tags) => Tagged(doc, tags :+ tag)
      case _                 => Tagged(self, List(tag))
    }

  /** Returns this doc with `tag` and `tags` added after its existing tags. */
  def tag(tag: String, tags: String*): Doc = self.tag(tag +: tags)

  /** Returns the tags of this doc, in the order they were added. */
  def tags: List[String] = self match {
    // `Extended` only wraps a doc, so unwrapping it cannot duplicate tags.
    case Extended(doc, _)  =>
      doc.tags
    // A `Tagged` node owns its tags; nested tags are only appended when they are
    // not already present, so wrappers that both carry the same tag (for
    // example after repeated `.tag`/`.ext` calls) do not duplicate it. We
    // deliberately do not recurse into `Sequence`: `+` hoists all tags into a
    // single outer `Tagged(Sequence(...), ...)`, so recursing there would
    // report the same tags twice.
    case Tagged(doc, tags) =>
      doc.tags.filterNot(tags.contains) ++ tags
    case _                 =>
      Nil
  }

  /**
   * Adds an extension to this documentation.
   *
   * The name is the bare extension name, without the OpenAPI `x-` prefix:
   * `ext("foo", value)` is emitted as `x-foo`. Passing a name that already
   * includes the prefix produces `x-x-foo`.
   */
  def ext(name: String, value: Json): Doc =
    self match {
      case Extended(doc, exts) => Extended(doc, exts.updated(name, value))
      case _                   => Extended(self, Map((name, value)))
    }

  /**
   * Returns the OpenAPI extensions added with `ext`, by name without the `x-`
   * prefix. When a name is added twice, the later value wins.
   */
  def extensions: Map[String, Json] = self match {
    case Extended(doc, exts)   => doc.extensions ++ exts
    case Tagged(doc, _)        => doc.extensions
    case Sequence(left, right) => left.extensions ++ right.extensions
    case _                     => Map.empty
  }

  /**
   * Renders this doc as CommonMark (Markdown) text, for example `# Users` for
   * `Doc.h1("Users")`. The tags are rendered as a bullet list at the end.
   */
  def toCommonMark: String = {
    val writer = new StringBuilder

    def renderSpan(span: Span, indent: Int): String = {
      def render(s: String): String = "  " * indent + s

      span match {
        case Span.Text(value)                   => render(value)
        case Span.Code(value, CodeStyle.Block)  => render(s"```${value.trim}\n```")
        case Span.Code(value, CodeStyle.Inline) => render(s"`$value`")
        case Span.Link(value, text)             => render(s"[${text.getOrElse(value)}]($value)")
        case Span.Bold(value)                   =>
          s"${render("**")}${renderSpan(value, indent).trim}${render("**")}"
        case Span.Italic(value)                 =>
          s"${render("*")}${renderSpan(value, indent).trim}${render("*")}"
        case Span.Error(value)                  =>
          s"${render(s"""<span style="color: red">""")}${render(value)}${render("</span>")}"
        case Span.Sequence(left, right)         =>
          val l = renderSpan(left, indent)
          val r = renderSpan(right, indent)
          s"$l$r"
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
          if (writer.nonEmpty && writer.last != '\n') append("\n")
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
                render(doc, indent + 1)
                writer.append("\n")
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

        case Doc.Raw(value, RawDocType.CommonMark) =>
          writer.append(value)

        case Doc.Raw(_, docType) =>
          throw new IllegalArgumentException(s"Unsupported raw doc type: $docType")

        case Doc.Tagged(doc, _) =>
          render(doc, indent)

        case Doc.Extended(doc, _) =>
          render(doc, indent)

      }
    }

    render(this)
    val tags = self.tags
    if (tags.nonEmpty) {
      // Add all tags to the end of the document as an unordered list
      render(Doc.unorderedListing(tags.map(Doc.p): _*))
    }
    writer.toString()
  }

  /**
   * Renders this doc as HTML. The tags are rendered as a bullet list at the
   * end. It throws an `IllegalArgumentException` for a header level outside 1
   * to 6, and for a doc created with `fromCommonMark`.
   */
  def toHtml: template.Html = {
    import template._

    val html: Html = self match {
      case Doc.Empty                                =>
        Html.Empty
      case Header(value, level)                     =>
        level match {
          case 1 => h1(value)
          case 2 => h2(value)
          case 3 => h3(value)
          case 4 => h4(value)
          case 5 => h5(value)
          case 6 => h6(value)
          case _ => throw new IllegalArgumentException(s"Invalid header level: $level")
        }
      case Paragraph(value)                         =>
        p(value.toHtml)
      case DescriptionList(definitions)             =>
        dl(
          definitions.flatMap { case (span, helpDoc) =>
            Seq(
              dt(span.toHtml),
              dd(helpDoc.toHtml),
            )
          },
        )
      case Sequence(left, right)                    =>
        left.toHtml ++ right.toHtml
      case Listing(elements, _) if elements.isEmpty =>
        Html.Empty
      case Listing(elements, listingType)           =>
        val elementsHtml =
          elements.map { doc =>
            li(doc.toHtml)
          }
        listingType match {
          case ListingType.Unordered => ul(elementsHtml)
          case ListingType.Ordered   => ol(elementsHtml)
        }

      case Raw(value, RawDocType.Html) =>
        Html.fromString(value)
      case Raw(_, docType)             =>
        throw new IllegalArgumentException(s"Unsupported raw doc type: $docType")
      case Tagged(doc, _)              =>
        doc.toHtml
      case Extended(doc, _)            =>
        doc.toHtml
    }
    html ++ (if (tags.nonEmpty) Doc.unorderedListing(self.tags.map(Doc.p): _*).toHtml else Html.Empty)
  }

  /** Renders this doc as an HTML string, indented by two spaces per level. */
  def toHtmlSnippet: String =
    toHtml.encode(2).toString

  /**
   * Renders this doc as plain text for a terminal, wrapped at `columnWidth`
   * columns. Headers are upper-cased. When `color` is `true`, bold, colors and
   * underlines are written as ANSI escape codes. It throws an
   * `IllegalArgumentException` for a doc created with `fromCommonMark`.
   */
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

        case Doc.Raw(value, RawDocType.Plain) =>
          writer.append(value): Unit

        case Doc.Raw(_, docType) =>
          throw new IllegalArgumentException(s"Unsupported raw doc type: $docType")

        case Tagged(doc, _) =>
          renderHelpDoc(doc)

        case Extended(doc, _) =>
          renderHelpDoc(doc)
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

        case Span.Code(value, _) =>
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

    val tags = self.tags
    if (tags.nonEmpty) {
      writer.append("\n")
      renderHelpDoc(Doc.unorderedListing(tags.map(Doc.p): _*))
    }
    writer.toString() + (if (color) Console.RESET else "")
  }

}

/** Constructors of [[Doc]] values and their parts. */
object Doc {

  /**
   * A `Schema` of `Doc` as a CommonMark string: encoding uses `toCommonMark`,
   * decoding uses `fromCommonMark`.
   */
  implicit val schemaDocSchema: Schema[Doc] =
    Schema[String].transform(
      fromCommonMark,
      _.toCommonMark,
    )

  /**
   * Creates a doc from CommonMark text, kept as it is. `toCommonMark` returns
   * the text unchanged; `toHtml` and `toPlaintext` throw an
   * `IllegalArgumentException` for this doc.
   */
  def fromCommonMark(commonMark: String): Doc =
    Doc.Raw(commonMark, RawDocType.CommonMark)

  private sealed trait RawDocType
  private object RawDocType {
    case object Plain      extends RawDocType
    case object CommonMark extends RawDocType
    case object Html       extends RawDocType
  }

  /** The empty doc, which renders as nothing. */
  case object Empty                                                extends Doc
  private final case class Raw(value: String, docType: RawDocType) extends Doc

  /** A header with the text `value` at `level`, 1 being the top level. */
  final case class Header(value: String, level: Int) extends Doc

  /** A paragraph of text. */
  final case class Paragraph(value: Span) extends Doc

  /** A list of terms, each with its definition. */
  final case class DescriptionList(definitions: List[(Span, Doc)]) extends Doc

  /** The doc `left` followed by the doc `right`. */
  final case class Sequence(left: Doc, right: Doc) extends Doc

  /** A bullet list or a numbered list of docs. */
  final case class Listing(elements: List[Doc], listingType: ListingType) extends Doc

  /** The doc `doc` with the tags `tgs`. */
  final case class Tagged(doc: Doc, tgs: List[String]) extends Doc

  /**
   * The doc `doc` with the OpenAPI extensions `exts`, by name without the `x-`
   * prefix.
   */
  final case class Extended(doc: Doc, exts: Map[String, Json]) extends Doc

  /** The kind of a [[Listing]]: bullet or numbered. */
  sealed trait ListingType

  /** The kinds of [[Listing]]. */
  object ListingType {

    /** A bullet list. */
    case object Unordered extends ListingType

    /** A numbered list. */
    case object Ordered extends ListingType
  }

  /**
   * Returns the docs of `bs` one after the other, or `Doc.empty` when `bs` is
   * empty.
   */
  def blocks(bs: Iterable[Doc]): Doc =
    if (bs.isEmpty) Doc.Empty else blocks(bs.head, bs.tail.toSeq: _*)

  /** Returns `helpDoc` followed by the docs of `helpDocs0`, joined with `+`. */
  def blocks(helpDoc: Doc, helpDocs0: Doc*): Doc =
    helpDocs0.foldLeft(helpDoc)(_ + _)

  /** Returns a list of terms, each with its definition. */
  def descriptionList(definitions: (Span, Doc)*): Doc = Doc.DescriptionList(definitions.toList)

  /** The empty doc. */
  val empty: Doc = Empty

  /** Returns a numbered list of `elements`. */
  def orderedListing(elements: Doc*): Doc =
    Doc.Listing(elements.toList, ListingType.Ordered)

  /** Returns a bullet list of `elements`. */
  def unorderedListing(elements: Doc*): Doc =
    Doc.Listing(elements.toList, ListingType.Unordered)

  /** Returns a level-1 header. */
  def h1(t: String): Doc = Header(t, 1)

  /** Returns a level-2 header. */
  def h2(t: String): Doc = Header(t, 2)

  /** Returns a level-3 header. */
  def h3(t: String): Doc = Header(t, 3)

  /** Returns a level-4 header. */
  def h4(t: String): Doc = Header(t, 4)

  /** Returns a level-5 header. */
  def h5(t: String): Doc = Header(t, 5)

  /** Returns a level-6 header. */
  def h6(t: String): Doc = Header(t, 6)

  /** Returns a paragraph of the plain text `t`. */
  def p(t: String): Doc = Doc.Paragraph(Span.text(t))

  /** Returns a paragraph of `span`. */
  def p(span: Span): Doc = Doc.Paragraph(span)

  /** Inline text inside a doc: plain, code, bold, italic, error or link. */
  sealed trait Span { self =>

    /** Returns this span followed by `that`. An empty side is dropped. */
    final def +(that: Span): Span =
      if (self.isEmpty) that
      else if (that.isEmpty) self
      else Span.Sequence(self, that)

    /** Returns `true` when `size` is 0. */
    final def isEmpty: Boolean = self.size == 0

    /**
     * Returns the number of characters of text in this span; for a link, the
     * length of its URI.
     */
    final def size: Int =
      self match {
        case Span.Text(value)           => value.length
        case Span.Code(value, _)        => value.length
        case Span.Error(value)          => value.length
        case Span.Bold(value)           => value.size
        case Span.Italic(value)         => value.size
        case Span.Link(value, _)        => value.toString.length
        case Span.Sequence(left, right) => left.size + right.size
      }

    /**
     * Renders this span as HTML, for example a `code` element for code and an
     * `a` element for a link.
     */
    def toHtml: template.Html = {
      import template._

      self match {
        case Span.Text(value)                   => value
        case Span.Code(value, CodeStyle.Block)  => pre(code(value))
        case Span.Code(value, CodeStyle.Inline) => code(value)
        case Span.Error(value)                  => span(styleAttr := "color: red", value)
        case Span.Bold(value)                   => b(value.toHtml)
        case Span.Italic(value)                 => i(value.toHtml)
        case Span.Link(value, text)             =>
          a(href := value.toASCIIString, Html.fromString(text.getOrElse(value.toASCIIString)))
        case Span.Sequence(left, right)         => left.toHtml ++ right.toHtml
      }
    }
  }

  /** The kinds of [[Span]] and their constructors. */
  object Span {

    /** Plain text. */
    final case class Text(value: String) extends Span

    /** Code, inline or as a block. */
    final case class Code(value: String, codeStyle: CodeStyle) extends Span

    /** Text shown as an error, in red. */
    final case class Error(value: String) extends Span

    /** Bold text. */
    final case class Bold(value: Span) extends Span

    /** Italic text. */
    final case class Italic(value: Span) extends Span

    /**
     * A link to `value`, shown as `text` when it is present and as the URI
     * otherwise.
     */
    final case class Link(value: java.net.URI, text: Option[String]) extends Span

    /** The span `left` followed by the span `right`. */
    final case class Sequence(left: Span, right: Span) extends Span

    /** How a [[Code]] span is shown: inline or as a block. */
    sealed trait CodeStyle

    /** The styles of a [[Code]] span. */
    object CodeStyle {

      /** Code inside a line of text. */
      case object Inline extends CodeStyle

      /** Code as a separate block. */
      case object Block extends CodeStyle
    }

    /** Returns inline code. */
    def code(t: String): Span = Span.Code(t, CodeStyle.Inline)

    /** Returns a block of code. */
    def codeBlock(t: String): Span = Span.Code(t, CodeStyle.Block)

    /** Returns the empty span, an empty text. */
    def empty: Span = Span.text("")

    /** Returns text shown as an error. */
    def error(t: String): Span = Span.Error(t)

    /** Returns `span` in bold. */
    def bold(span: Span): Span = Span.Bold(span)

    /** Returns the text `t` in bold. */
    def bold(t: String): Span = Span.Bold(text(t))

    /** Returns `span` in italics. */
    def italic(span: Span): Span = Span.Italic(span)

    /** Returns the text `t` in italics. */
    def italic(t: String): Span = Span.Italic(text(t))

    /** Returns plain text. */
    def text(t: String): Span = Span.Text(t)

    /** Returns a link to `uri`, shown as the URI. */
    def link(uri: java.net.URI): Span = Span.Link(uri, None)

    /**
     * Returns a link to `uri`, shown as `text`, or as the URI when `text` is
     * empty.
     */
    def link(uri: java.net.URI, text: String): Span = Span.Link(uri, Some(text).filter(_.nonEmpty))

    /** Returns `span` followed by `spans0`, joined with `+`. */
    def spans(span: Span, spans0: Span*): Span = spans(span :: spans0.toList)

    /**
     * Returns the spans of `spans` joined with `+`, or the empty span when
     * there are none.
     */
    def spans(spans: Iterable[Span]): Span = spans.toList.foldLeft(empty)(_ + _)

  }
}

private[codec] class DocWriter(stringBuilder: StringBuilder, startOffset: Int, columnWidth: Int) { self =>
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

  override def toString: String = stringBuilder.toString()

  def unindent(): Unit = self.marginStack = self.marginStack.drop(1)
}
private[codec] object DocWriter                                                                  {
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
