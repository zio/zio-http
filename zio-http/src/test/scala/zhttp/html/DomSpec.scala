package zhttp.html

import zio.random.Random
import zio.test.{DefaultRunnableSpec, Gen, assertTrue, check, checkAll}

object DomSpec extends DefaultRunnableSpec {
  def spec = suite("DomSpec") {
    test("empty") {
      val dom = Dom.empty
      assertTrue(dom.encode == "")
    } +
      test("text") {
        val dom = Dom.text("abc")
        assertTrue(dom.encode == "abc")
      } +
      test("element") {
        val dom = Dom.element("div")
        assertTrue(dom.encode == "<div></div>")
      } +
      suite("element with children") {
        test("element with children") {
          val dom = Dom.element("div", Dom.element("div"))
          assertTrue(dom.encode == "<div><div></div></div>")
        } +
          test("element with multiple children") {
            val dom = Dom.element("div", Dom.element("div"), Dom.element("div"), Dom.element("div"))
            assertTrue(dom.encode == "<div><div></div><div></div><div></div></div>")
          } +
          test("element with nested children") {
            val dom = Dom.element("div", Dom.element("div", Dom.element("div", Dom.element("div"))))
            assertTrue(dom.encode == "<div><div><div><div></div></div></div></div>")
          } +
          test("element with text") {
            val dom = Dom.element("div", Dom.text("abc"))
            assertTrue(dom.encode == "<div>abc</div>")
          } +
          test("void element with children") {
            val dom = Dom.element("br", Dom.text("Hello"))
            assertTrue(dom.encode == "<br>Hello</br>")
          }
      } +
      suite("Attribute") {
        test("constant") {
          val dom = Dom.attr("href", "https://www.zio-http.com")
          assertTrue(dom.encode == """href="https://www.zio-http.com"""")
        }
      } +
      suite("element with attributes") {
        test("constant") {
          val dom = Dom.element("a", Dom.attr("href", "https://www.zio-http.com"))
          assertTrue(dom.encode == """<a href="https://www.zio-http.com"></a>""")
        } +
          test("multiple constant") {
            val dom = Dom.element(
              "a",
              Dom.attr("href", "https://www.zio-http.com"),
              Dom.attr("title", "click me!"),
            )
            assertTrue(dom.encode == """<a href="https://www.zio-http.com" title="click me!"></a>""")
          }
      } +
      test("element with attribute & children") {
        val dom = Dom.element(
          "a",
          Dom.attr("href", "https://www.zio-http.com"),
          Dom.text("zio-http"),
        )

        assertTrue(dom.encode == """<a href="https://www.zio-http.com">zio-http</a>""")
      } +
      suite("Self Closing") {
        val voidTagGen: Gen[Any, CharSequence] = Gen.fromIterable(Element.voidElementNames)
        val tagGen: Gen[Random, String]        =
          Gen.stringBounded(1, 5)(Gen.alphaChar).filterNot(Element.voidElementNames.contains)

        testM("void") {
          checkAll(voidTagGen) { name =>
            val dom = Dom.element(name)
            assertTrue(dom.encode == s"<${name}/>")
          }
        } +
          testM("not void") {
            check(tagGen) { name =>
              val dom = Dom.element(name)
              assertTrue(dom.encode == s"<${name}></${name}>")
            }
          }
      }
  }
}
