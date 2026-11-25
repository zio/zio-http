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

package zio.http.template

import scala.collection.immutable.ListMap

import zio.schema.Schema

import zio.http.MediaType
import zio.http.codec.{BinaryCodecWithSchema, HttpContentCodec, TextBinaryCodec}
import zio.http.internal.OutputEncoder

/**
 * A node of an HTML document: an element, a text node, a raw node, an attribute, a boolean attribute or the empty node. Build nodes with the constructors in the companion object, or with the element and attribute helpers of the `zio.http.template` package, and render them with `encode`.
 *
 * When a node is rendered, text and attribute values are escaped: `&`, `<`, `>`, `"` and `'` become character references. Text inside a `script` or `style` element is written as is, and so is the content of a `Dom.raw` node.
 *
 * The elements `area`, `base`, `br`, `col`, `embed`, `hr`, `img`, `input`, `link`, `meta`, `param`, `source`, `track` and `wbr` are void elements: when they have no child content they render as one self-closing tag, which ends in `/>`. With child content they render with an end tag, like other elements.
 *
 * {{{
 * val link: Dom =
 *   Dom.element("a", Dom.attr("href", "/menu?day=mon&meal=lunch"), Dom.text("Tea & cake"))
 *
 * link.encode
 * // <a href="/menu?day=mon&amp;meal=lunch">Tea &amp; cake</a>
 * Dom.element("hr").encode
 * // <hr/>
 * }}}
 *
 * @see [[https://ziohttp.com/reference/template/ Template DSL]]
 * @see [[https://html.spec.whatwg.org/multipage/syntax.html#void-elements Void elements]] in the HTML standard
 */
sealed trait Dom { self =>

  /** Renders this node as an HTML string, without line breaks or indentation. */
  def encode: CharSequence =
    encode(EncodingState.NoIndentation)

  /**
   * Renders this node as an HTML string with line breaks: each child node of an element goes on its own line, indented by `spaces` spaces per level of nesting. An element whose only child is a text node stays on one line.
   */
  def encode(spaces: Int): CharSequence =
    encode(EncodingState.Indentation(0, spaces))

  private[template] def encode(state: EncodingState, encodeHtml: Boolean = true): CharSequence = self match {
    case Dom.Element(name, children) =>
      val encode     = if (name == "script" || name == "style") false else encodeHtml
      val attributes = children.collect {
        case self: Dom.Attribute        => self.encode
        case self: Dom.BooleanAttribute => self.encode
      }

      val innerState = state.inner
      val elements   = children.collect {
        case self: Dom.Element => self
        case self: Dom.Text    => self
        case self: Dom.Raw     => self
      }

      val noElements   = elements.isEmpty
      val noAttributes = attributes.isEmpty
      val isVoid       = Element.isVoid(name)

      def inner: CharSequence =
        elements match {
          case Seq(singleText: Dom.Text) => singleText.encode(innerState, encode)
          case _                         =>
            s"${innerState.nextElemSeparator}${elements.map(_.encode(innerState, encode)).mkString(innerState.nextElemSeparator)}${state.nextElemSeparator}"
        }

      if (noElements && noAttributes && isVoid) s"<$name/>"
      else if (noElements && isVoid)
        s"<$name ${attributes.mkString(" ")}/>"
      else if (noAttributes)
        s"<$name>$inner</$name>"
      else
        s"<$name ${attributes.mkString(" ")}>$inner</$name>"

    case Dom.Text(data) if encodeHtml => OutputEncoder.encodeHtml(data.toString)
    case Dom.Text(data)               => data
    case Dom.Attribute(name, value)   => s"""$name="${OutputEncoder.encodeHtml(value.toString)}""""
    case Dom.Empty                    => ""
    case Dom.Raw(raw)                 => raw

    case Dom.BooleanAttribute(name, None)        => s"$name"
    case Dom.BooleanAttribute(name, Some(value)) => s"""$name="${value}""""
  }
}

/** Constructors for [[Dom]] nodes, and the `Schema` and `HttpContentCodec` of [[Dom]]. */
object Dom {

  /**
   * A `Schema` that represents a [[Dom]] as a string: encoding renders the node with `encode`, and decoding gives a node that renders as the decoded string, unchanged.
   */
  implicit val schema: Schema[Dom] =
    Schema[String].transform(Dom.raw, _.encode.toString)

  /**
   * The `HttpContentCodec` of [[Dom]], with the single media type `text/html`. It writes the rendered node as the body, and reads a body with `schema`.
   */
  implicit val htmlCodec: HttpContentCodec[Dom] = {
    HttpContentCodec(
      ListMap(
        MediaType.text.`html` ->
          BinaryCodecWithSchema.fromBinaryCodec(TextBinaryCodec.fromSchema(Schema[Dom])),
      ),
    )
  }

  /**
   * Creates an attribute node. As a child of an element it becomes an attribute of that element, rendered as `name="value"` with the value escaped.
   */
  def attr(name: CharSequence, value: CharSequence): Dom = Dom.Attribute(name, value)

  /**
   * Creates a boolean attribute node. With `None` it renders as the bare name, such as `disabled`. With `Some(b)` it renders as `name="true"` or `name="false"`, so `Some(false)` still writes the attribute.
   */
  def booleanAttr(name: CharSequence, value: Option[Boolean] = None): Dom = Dom.BooleanAttribute(name, value)

  /**
   * Creates an element with the tag `name` and the given children. Attribute nodes among the children become the element's attributes, in order. Element, text and raw nodes become its content, in order, and empty nodes are left out.
   */
  def element(name: CharSequence, children: Dom*): Dom = Dom.Element(name, children)

  /** Returns the empty node, which renders as an empty string and is left out when it is a child of an element. */
  def empty: Dom = Empty

  /** Creates a text node. Its text is escaped when rendered, except inside a `script` or `style` element. */
  def text(data: CharSequence): Dom = Dom.Text(data)

  /** Creates a node whose content is written to the output as is, without escaping. */
  def raw(raw: CharSequence): Dom = Dom.Raw(raw)

  private[zio] final case class Element(name: CharSequence, children: Seq[Dom]) extends Dom

  private[zio] final case class Text(data: CharSequence) extends Dom

  private[zio] final case class Raw(raw: CharSequence) extends Dom

  private[zio] final case class Attribute(name: CharSequence, value: CharSequence) extends Dom

  private[zio] final case class BooleanAttribute(name: CharSequence, value: Option[Boolean] = None) extends Dom

  private[zio] object Empty extends Dom
}
