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

import zio.test.{assertTrue, check, checkAll}

import zio.http.ZIOHttpSpec
import zio.http.template.HtmlGen.{tagGen, voidTagGen}

object DomSpec extends ZIOHttpSpec {
  def spec = suite("DomSpec")(
    test("empty") {
      val dom = Dom.empty
      assertTrue(dom.encode == "")
    },
    test("text") {
      val dom = Dom.text("abc")
      assertTrue(dom.encode == "abc")
    },
    test("element") {
      val dom = Dom.element("div")
      assertTrue(dom.encode == "<div></div>")
    },
    suite("element with children")(
      test("element with children") {
        val dom = Dom.element("div", Dom.element("div"))
        assertTrue(dom.encode == "<div><div></div></div>")
      },
      test("element with multiple children") {
        val dom = Dom.element("div", Dom.element("div"), Dom.element("div"), Dom.element("div"))
        assertTrue(dom.encode == "<div><div></div><div></div><div></div></div>")
      },
      test("element with nested children") {
        val dom = Dom.element("div", Dom.element("div", Dom.element("div", Dom.element("div"))))
        assertTrue(dom.encode == "<div><div><div><div></div></div></div></div>")
      },
      test("element with text") {
        val dom = Dom.element("div", Dom.text("abc"))
        assertTrue(dom.encode == "<div>abc</div>")
      },
      test("void element with children") {
        val dom = Dom.element("br", Dom.text("Hello"))
        assertTrue(dom.encode == "<br>Hello</br>")
      },
    ),
    suite("Attribute") {
      test("constant") {
        val dom = Dom.attr("href", "https://www.zio-http.com")
        assertTrue(dom.encode == """href="https://www.zio-http.com"""")
      }
    },
    suite("element with attributes")(
      test("constant") {
        val dom = Dom.element("a", Dom.attr("href", "https://www.zio-http.com"))
        assertTrue(dom.encode == """<a href="https://www.zio-http.com"></a>""")
      },
      test("multiple constant") {
        val dom = Dom.element(
          "a",
          Dom.attr("href", "https://www.zio-http.com"),
          Dom.attr("title", "click me!"),
        )
        assertTrue(dom.encode == """<a href="https://www.zio-http.com" title="click me!"></a>""")
      },
    ),
    test("element with attribute & children") {
      val dom = Dom.element(
        "a",
        Dom.attr("href", "https://www.zio-http.com"),
        Dom.text("zio-http"),
      )

      assertTrue(dom.encode == """<a href="https://www.zio-http.com">zio-http</a>""")
    },
    test("xss protection for text nodes") {
      val dom = Dom.element(
        "a", 
        Dom.attr("href", "http://www.zio-http.com"), 
        Dom.text("""<script type="text/javascript">alert("xss")</script>""")
      )
      assertTrue(dom.encode == """<a href="http://www.zio-http.com">&lt;script type=&quot;text/javascript&quot;&gt;alert(&quot;xss&quot;)&lt;/script&gt;</a>""")
    },
    test("xss protection for attributes") {
      val dom = Dom.element(
        "a", 
        Dom.attr("href", """<script type="text/javascript">alert("xss")</script>"""), 
        Dom.text("my link")
      )
      assertTrue(dom.encode == """<a href="&lt;script type=&quot;text/javascript&quot;&gt;alert(&quot;xss&quot;)&lt;/script&gt;">my link</a>""")
    },
    test("raw output") {
      val dom = Dom.element(
        "a", 
        Dom.attr("href", "http://www.zio-http.com"), 
        Dom.raw("""<script type="text/javascript">alert("xss")</script>""")
      )
      assertTrue(dom.encode == """<a href="http://www.zio-http.com"><script type="text/javascript">alert("xss")</script></a>""")
    },    
    suite("Self Closing")(
      test("void") {
        checkAll(voidTagGen) { name =>
          val dom = Dom.element(name)
          assertTrue(dom.encode == s"<${name}/>")
        }
      },
      test("not void") {
        check(tagGen) { name =>
          val dom = Dom.element(name)
          assertTrue(dom.encode == s"<${name}></${name}>")
        }
      },
    ),
  )

}
