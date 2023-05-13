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

package zio.http.html

import zio.test.Assertion.equalTo
import zio.test._

case object HtmlSpec extends ZIOSpecDefault {
  def spec = {
    suite("HtmlSpec")(
      test("tags") {
        val view     = html(head(), body(div()))
        val expected = """<html><head></head><body><div></div></body></html>"""
        assert(view.encode)(equalTo(expected.stripMargin))
      },
      test("tags with attributes") {
        val view     = html(body(div(css := "container" :: Nil, "Hello!")))
        val expected = """<html><body><div class="container">Hello!</div></body></html>"""
        assert(view.encode)(equalTo(expected.stripMargin))
      },
      test("tags with children") {
        val view     = html(body(div(css := "container" :: Nil, "Hello!", span("World!"))))
        val expected =
          """<html><body><div class="container">Hello!<span>World!</span></div></body></html>"""
        assert(view.encode)(equalTo(expected.stripMargin))
      },
      test("tags with attributes and children") {
        val view     = html(body(div(css := "container" :: Nil, "Hello!", span("World!"))))
        val expected =
          """<html><body><div class="container">Hello!<span>World!</span></div></body></html>"""
        assert(view.encode)(equalTo(expected.stripMargin))
      },
      test("tags with attributes and children") {
        val view     = html(body(div(css := "container" :: Nil, "Hello!", span("World!"))))
        val expected =
          """<html><body><div class="container">Hello!<span>World!</span></div></body></html>"""
        assert(view.encode)(equalTo(expected.stripMargin))
      },
      test("tags with attributes and children") {
        val view     = html(body(div(css := "container" :: Nil, "Hello!", span("World!"))))
        val expected =
          """<html><body><div class="container">Hello!<span>World!</span></div></body></html>"""
        assert(view.encode)(equalTo(expected.stripMargin))
      },
      test("tags with attributes and children") {
        val view     = div("Hello!", css := "container" :: Nil)
        val expected = """<div class="container">Hello!</div>"""
        assert(view.encode)(equalTo(expected.stripMargin))
      },
      test("classAttr(s) attributes") {
        val view1     = div("Hello!", classAttr := "container")
        val view2     = div("Hello!", classAttrs := "container" :: Nil)
        val view3     = div("Hello!", classAttrs("container"))
        val expected1 = """<div class="container">Hello!</div>"""
        assert(view1.encode)(equalTo(expected1.stripMargin)) &&
        assert(view2.encode)(equalTo(expected1.stripMargin)) &&
        assert(view3.encode)(equalTo(expected1.stripMargin))

        val view4     = div("Hello!", classAttrs("container1", "container2"))
        val view5     = div("Hello!", classAttrs := "container1" :: "container2" :: Nil)
        val expected2 = """<div class="container1 container2">Hello!</div>"""
        assert(view4.encode)(equalTo(expected2.stripMargin)) &&
        assert(view5.encode)(equalTo(expected2.stripMargin))
      },
      test("styleAttr(s) attributes") {
        val view1     = div("Hello!", styleAttr := "color:red;text-align:right")
        val view2     = div("Hello!", styleAttrs := "color" -> "red" :: "text-align" -> "right" :: Nil)
        val view3     = div("Hello!", styleAttrs("color" -> "red", "text-align" -> "right"))
        val expected1 = """<div style="color:red;text-align:right">Hello!</div>"""
        assert(view1.encode)(equalTo(expected1.stripMargin)) &&
        assert(view2.encode)(equalTo(expected1.stripMargin)) &&
        assert(view3.encode)(equalTo(expected1.stripMargin))
      },
      suite("implicit conversions")(
        test("from unit") {
          val view: Html = {}
          assert(view.encode)(equalTo(""))
        },
        test("from option") {
          val some: Html = Some(div("ok"))
          val none: Html = None
          assert(some.encode)(equalTo("""<div>ok</div>""")) &&
          assert(none.encode)(equalTo(""))
        },
      ),
    )
  }
}
