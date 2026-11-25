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

package zio.http.template2

import scala.language.experimental.macros

/**
 * Provides the `css` and `selector` string interpolators. When an interpolated
 * string has no arguments, its text is checked at compile time.
 */
private[template2] trait CssInterpolator {

  /**
   * Adds the `css` and `selector` interpolators to string literals.
   */
  implicit class CssStringContext(val sc: StringContext) {

    /**
     * Creates a [[Css]] value from an interpolated string, inserting the
     * arguments as the `s` interpolator does. When the string has no arguments,
     * the macro checks its text at compile time and stops compilation with an
     * error when the check fails. The check is a pattern test, not a CSS
     * parser: text that contains a `:` passes, and `css"50%"` fails with the
     * error `Invalid CSS syntax: 50%`.
     *
     * {{{
     * val heading: Css = css"h1 { color: navy; }"
     * style(heading).render
     * // <style>h1 { color: navy; }</style>
     * }}}
     *
     * @see
     *   [[https://ziohttp.com/reference/template/ Template DSL]]
     */
    def css(args: Any*): Css = macro CssInterpolatorMacros.cssImpl

    /**
     * Creates a [[CssSelector]] (a `CssSelector.Raw`) from an interpolated
     * string, inserting the arguments as the `s` interpolator does. When the
     * string has no arguments, the macro checks at compile time that the text
     * uses only letters, digits, whitespace and a fixed set of other
     * characters, and stops compilation with an error otherwise:
     * `selector"div!"` fails with the error
     * `Invalid CSS selector syntax: div!`. On Scala 3 that set has no caret, so
     * an attribute test with the CSS starts-with operator fails there; Scala 2
     * accepts it.
     *
     * {{{
     * val links: CssSelector = selector"nav > a.active"
     * links.render
     * // nav > a.active
     * }}}
     *
     * @see
     *   [[https://ziohttp.com/reference/template/ Template DSL]]
     */
    def selector(args: Any*): CssSelector = macro CssInterpolatorMacros.selectorImpl
  }
}
