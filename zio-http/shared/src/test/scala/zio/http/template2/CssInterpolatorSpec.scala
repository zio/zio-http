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

import zio.test._

import zio.http.ZIOHttpSpec

object CssInterpolatorSpec extends ZIOHttpSpec {

  def spec = suite("CssInterpolatorSpec")(
    suite("css interpolator")(
      test("should accept valid CSS properties") {
        val result = css"color: red;"
        assertTrue(result.value == "color: red;")
      },
      test("should accept valid CSS rules") {
        val result = css".class { color: blue; }"
        assertTrue(result.value == ".class { color: blue; }")
      },
      test("should accept CSS declarations without semicolon") {
        val result = css"font-size: 14px"
        assertTrue(result.value == "font-size: 14px")
      },
      test("should accept empty CSS") {
        val result = css""
        assertTrue(result.value == "")
      },
      test("should accept CSS with whitespace") {
        val result = css"  margin: 10px  "
        assertTrue(result.value == "  margin: 10px  ")
      },
      test("should handle interpolation") {
        val color  = "green"
        val size   = "16px"
        val result = css"color: $color; font-size: $size;"
        assertTrue(result.value == "color: green; font-size: 16px;")
      },
      test("should handle complex CSS with interpolation") {
        val selector = ".my-class"
        val property = "background-color"
        val value    = "#ff0000"
        val result   = css"$selector { $property: $value; }"
        assertTrue(result.value == ".my-class { background-color: #ff0000; }")
      },
      test("should handle edge cases gracefully") {
        val result1 = css"/* comment */ color: red;"
        assertTrue(result1.value.contains("color: red;"))

        val result2 = css"@media (max-width: 768px) { .mobile { display: none; } }"
        assertTrue(result2.value.contains("@media"))
      },
    ),
    suite("selector interpolator")(
      test("should accept valid CSS class selector") {
        val result = selector".my-class"
        assertTrue(result.render == ".my-class")
      },
      test("should accept valid CSS id selector") {
        val result = selector"#my-id"
        assertTrue(result.render == "#my-id")
      },
      test("should accept valid CSS element selector") {
        val result = selector"div"
        assertTrue(result.render == "div")
      },
      test("should accept complex CSS selectors") {
        val result = selector"div.class > p:first-child"
        assertTrue(result.render == "div.class > p:first-child")
      },
      test("should accept attribute selectors") {
        val result = selector"input[type='text']"
        assertTrue(result.render == "input[type='text']")
      },
      test("should accept pseudo-class selectors") {
        val result = selector"a:hover"
        assertTrue(result.render == "a:hover")
      },
      test("should accept multiple selectors") {
        val result = selector"h1, h2, h3"
        assertTrue(result.render == "h1, h2, h3")
      },
      test("should handle runtime interpolation") {
        val className = "active"
        val element   = "button"
        val result    = selector"$element.$className"
        assertTrue(result.render == "button.active")
      },
      test("should accept descendant selectors") {
        val result = selector"nav ul li a"
        assertTrue(result.render == "nav ul li a")
      },
      test("should accept adjacent sibling selectors") {
        val result = selector"h1 + p"
        assertTrue(result.render == "h1 + p")
      },
      test("should accept general sibling selectors") {
        val result = selector"h1 ~ p"
        assertTrue(result.render == "h1 ~ p")
      },
      test("should fail") {
        typeCheck(
          // language=Scala
          """selector"h1 @> .invalid-selector"""",
        ).map(res => assertTrue(res == Left("Invalid CSS selector syntax: h1 @> .invalid-selector")))

      },
      test("should fail with invalid selector - invalid combinator") {
        typeCheck(
          // language=Scala
          """selector"div @@ p"""",
        ).map(res => assertTrue(res == Left("Invalid CSS selector syntax: div @@ p")))
      },
    ),
  )
}
