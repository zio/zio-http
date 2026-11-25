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

/**
 * A page template with a plain monospace style. `Handler.template` responds
 * with a page built by `container`, and `Response.fromThrowable` uses it for
 * the error body when its `ErrorResponseConfig` asks for an error body in the
 * HTML format.
 */
object Template {

  /**
   * Wraps `element` in a complete `html` page with the heading `heading`. The
   * page's `title` is `ZIO Http - ` followed by `heading`, and a `style`
   * element gives `body` a monospace font, a font size of 16px and the
   * background color `#edede0`. The body holds a `div` with the style
   * `margin: auto; padding: 2em 4em; max-width: 80%`, which contains an `h1`
   * element with `heading` and then `element`.
   *
   * The result has no doctype declaration; `Response.html` puts one in front of
   * the rendered page.
   *
   * {{{
   * val page: Html = Template.container("Users")(ul(li("Ada"), li("Grace")))
   * val rendered: String = page.encode.toString
   *
   * rendered.startsWith("<html><head><title>ZIO Http - Users</title>")
   * // true
   * rendered.endsWith("<ul><li>Ada</li><li>Grace</li></ul></div></body></html>")
   * // true
   * }}}
   *
   * @see
   *   [[https://ziohttp.com/reference/template/ Template DSL]]
   */
  def container(heading: CharSequence)(element: Html): Html = {
    html(
      head(
        title(s"ZIO Http - ${heading}"),
        style("""
                | body {
                |   font-family: monospace;
                |   font-size: 16px;
                |   background-color: #edede0;
                | }
                |""".stripMargin),
      ),
      body(
        div(
          styles := "margin: auto; padding: 2em 4em; max-width: 80%",
          h1(heading),
          element,
        ),
      ),
    )
  }
}
