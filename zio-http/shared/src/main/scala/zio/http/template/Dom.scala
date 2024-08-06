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
import zio.http.codec.{BinaryCodecWithSchema, HttpContentCodec}
import zio.http.internal.OutputEncoder

/**
 * Light weight DOM implementation that can be rendered as a html string.
 *
 * @see
 *   <a
 *   href="https://html.spec.whatwg.org/multipage/syntax.html#void-elements">Void
 *   elements</a> only have a start tag; end tags must not be specified for void
 *   elements.
 */
sealed trait Dom { self =>
  def encode: CharSequence =
    encode(EncodingState.NoIndentation)

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

object Dom {
  implicit val schema: Schema[Dom] =
    Schema[String].transform(Dom.raw, _.encode.toString)

  implicit val htmlCodec: HttpContentCodec[Dom] = {
    HttpContentCodec(
      ListMap(
        MediaType.text.`html` ->
          BinaryCodecWithSchema.fromBinaryCodec(zio.http.codec.internal.TextBinaryCodec.fromSchema(Schema[Dom])),
      ),
    )
  }

  def attr(name: CharSequence, value: CharSequence): Dom = Dom.Attribute(name, value)

  def booleanAttr(name: CharSequence, value: Option[Boolean] = None): Dom = Dom.BooleanAttribute(name, value)

  def element(name: CharSequence, children: Dom*): Dom = Dom.Element(name, children)

  def empty: Dom = Empty

  def text(data: CharSequence): Dom = Dom.Text(data)

  def raw(raw: CharSequence): Dom = Dom.Raw(raw)

  private[zio] final case class Element(name: CharSequence, children: Seq[Dom]) extends Dom

  private[zio] final case class Text(data: CharSequence) extends Dom

  private[zio] final case class Raw(raw: CharSequence) extends Dom

  private[zio] final case class Attribute(name: CharSequence, value: CharSequence) extends Dom

  private[zio] final case class BooleanAttribute(name: CharSequence, value: Option[Boolean] = None) extends Dom

  private[zio] object Empty extends Dom
}
