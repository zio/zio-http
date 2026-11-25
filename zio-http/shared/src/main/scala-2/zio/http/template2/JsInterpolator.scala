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
 * Provides the `js` string interpolator. On Scala 2, when an interpolated
 * string has no arguments, its text is checked at compile time.
 */
private[template2] trait JsInterpolator {

  /**
   * Adds the `js` interpolator to string literals.
   */
  implicit class JSStringContext(val sc: StringContext) {

    /**
     * Creates a [[Js]] value from an interpolated string, inserting the
     * arguments as the `s` interpolator does. On Scala 2, when the string has
     * no arguments, the macro checks its text at compile time with a few
     * patterns and stops compilation with an error when none of them matches.
     * The check is not a JavaScript parser: text that contains a JavaScript
     * keyword such as `if` or `in`, even inside a word, passes, and `js"#x"`
     * fails with the error `Invalid JavaScript syntax: #x`. On Scala 3 the
     * macro does not report this error, so `js"#x"` compiles.
     *
     * {{{
     * val onSave: Js = js"saveDraft()"
     * button(Dom.attr("onclick") := onSave, "Save").render
     * // <button onclick="saveDraft()">Save</button>
     * }}}
     *
     * @see
     *   [[https://ziohttp.com/reference/template/ Template DSL]]
     */
    def js(args: Any*): Js = macro JSInterpolatorMacros.jsImpl
  }
}
