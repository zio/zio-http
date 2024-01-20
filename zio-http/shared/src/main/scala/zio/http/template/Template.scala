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
 * A ZIO Http styled general purpose templates
 */
object Template {

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
          styles := Seq("margin" -> "auto", "padding" -> "2em 4em", "max-width" -> "80%"),
          h1(heading),
          element,
        ),
      ),
    )
  }
}
