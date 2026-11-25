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

import scala.language.implicitConversions

/**
 * A fragment of HTML: no node, one [[Dom]] node, or a sequence of nodes. The element helpers of the `zio.http.template` package, such as `div` and `p`, take `Html` children, and the implicit conversions in the companion object turn a `CharSequence`, a [[Dom]], a `Seq[Dom]`, an `Option[Dom]` or `()` into `Html`. `encode` renders the fragment as a string, and `++` joins two fragments.
 *
 * Text is escaped when it is rendered: `&`, `<`, `>`, `"` and `'` become character references, except in text inside a `script` or `style` element.
 *
 * {{{
 * val page: Html = div(id := "greeting", p("Hello, Ada & Bob"))
 *
 * page.encode
 * // <div id="greeting"><p>Hello, Ada &amp; Bob</p></div>
 * }}}
 *
 * @see [[https://ziohttp.com/reference/template/ Template DSL]]
 */
sealed trait Html { self =>

  /** Renders this fragment as an HTML string, without line breaks or indentation. */
  def encode: CharSequence =
    encode(EncodingState.NoIndentation)

  /**
   * Renders this fragment as an HTML string with line breaks: each child node of an element goes on its own line, indented by `spaces` spaces per level of nesting. An element whose only child is a text node stays on one line, and the nodes at the top of the fragment are separated by line breaks without indentation.
   */
  def encode(spaces: Int): CharSequence =
    encode(EncodingState.Indentation(0, spaces))

  private[template] def encode(state: EncodingState): CharSequence = {
    self match {
      case Html.Empty                        => ""
      case Html.Single(element)              => element.encode(state)
      case Html.Multiple(elements: Seq[Dom]) => elements.map(_.encode(state)).mkString(state.nextElemSeparator)
    }
  }

  /** Returns a fragment with the nodes of this fragment followed by the nodes of `that`. */
  def ++(that: Html): Html =
    (self, that) match {
      case (l, Html.Empty)                      => l
      case (Html.Empty, r)                      => r
      case (Html.Single(l), Html.Single(r))     => Html.Multiple(Seq(l, r))
      case (Html.Multiple(l), Html.Single(r))   => Html.Multiple(l :+ r)
      case (Html.Single(l), Html.Multiple(r))   => Html.Multiple(l +: r)
      case (Html.Multiple(l), Html.Multiple(r)) => Html.Multiple(l ++ r)
    }
}

/** Implicit conversions to [[Html]], and the `raw` constructor. */
object Html {

  /** Converts a `CharSequence` into a fragment with one text node, which is escaped when rendered, except inside a `script` or `style` element. */
  implicit def fromString(string: CharSequence): Html = Html.Single(Dom.text(string))

  /** Converts a sequence of [[Dom]] nodes into a fragment that holds them in order. */
  implicit def fromSeq(elements: Seq[Dom]): Html = Html.Multiple(elements)

  /** Converts one [[Dom]] node into a fragment that holds it. */
  implicit def fromDomElement(element: Dom): Html = Html.Single(element)

  /** Converts an `Option[Dom]` into a fragment: the node for `Some`, and an empty fragment for `None`. */
  implicit def fromOption(maybeElement: Option[Dom]): Html =
    maybeElement.fold(Html.Empty: Html)(Html.Single.apply)

  /** Converts `()` into an empty fragment, so a `Unit` child adds nothing to an element. */
  implicit def fromUnit(unit: Unit): Html = {
    val _ = unit
    Html.Empty
  }
  /** Returns a fragment whose content is written to the output as is, without escaping. */
  def raw(content: CharSequence): Html    = Html.Single(Dom.raw(content))

  /** A fragment with one node. */
  private[zio] case class Single(element: Dom) extends Html

  /** A fragment with a sequence of nodes. */
  private[zio] final case class Multiple(children: Seq[Dom]) extends Html

  /** A fragment with no nodes. */
  private[zio] case object Empty extends Html
}
