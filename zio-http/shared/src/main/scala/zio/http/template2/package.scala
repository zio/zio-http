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

package zio.http

import scala.language.implicitConversions

import zio.http.template2.{Dom, Modifier}

/**
 * The template2 HTML DSL: the HTML element and attribute values (from `HtmlElements` and `HtmlAttributes`), the `css`, `selector` and `js` string interpolators, and implicit conversions from strings, options and collections to [[Dom]] and [[Modifier]]. Import it with `import zio.http.template2._`.
 *
 * @see [[https://ziohttp.com/reference/template/ Template DSL]]
 */
package object template2
    extends HtmlElements
    with HtmlAttributes
    with LowPriorityTemplateImplicits
    with CssInterpolator
    with JsInterpolator {

  /**
   * Converts a collection of nodes to one [[Modifier]], a `Dom.Fragment` of the nodes, so a collection such as `items.map(li(_))` can be passed to an element as children.
   */
  implicit def itrToModifier(seq: Iterable[Dom]): Modifier    = Dom.Fragment(seq)

  /**
   * Converts an `Option[Modifier]` to the modifier it holds, or to `Dom.Empty` when it is `None`.
   */
  implicit def optToModifier(opt: Option[Modifier]): Modifier = opt match {
    case Some(mod) => mod
    case None      => Dom.Empty
  }

  /**
   * Converts a `String` to a text node (`Dom.Text`) where a [[Modifier]] is expected, so a string can be passed to an element as a child. A `String` where a [[Dom]] is expected becomes a text node through `stringToDom`. The text is rendered with `&`, `<`, `>`, `"` and `'` replaced by character references, except inside `script` and `style` elements.
   *
   * {{{
   * import zio.http.template2._
   *
   * val title: Dom = "Welcome"
   * val line       = p("Hello, ", b("Ada"))
   * line.render
   * // <p>Hello, <b>Ada</b></p>
   * }}}
   *
   * @see [[https://ziohttp.com/reference/template/ Template DSL]]
   */
  implicit def stringToModifier(s: String): Modifier          = Dom.Text(s)

}

private[http] trait LowPriorityTemplateImplicits {

  /**
   * Converts an `Option[String]` to a text node (`Dom.Text`), or to `Dom.Empty` when it is `None`.
   */
  implicit def optStrToDom(opt: Option[String]): Dom = opt match {
    case Some(str) => Dom.Text(str)
    case None      => Dom.Empty
  }

  /**
   * Converts a `String` to a text node (`Dom.Text`) where a [[Dom]] is expected.
   */
  implicit def stringToDom(s: String): Dom           = Dom.Text(s)
}
