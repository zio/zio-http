package zhttp.html

import zhttp.internal.HttpGen
import zio.test.{DefaultRunnableSpec, assertTrue, checkAll}

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
      suite("doctype") {
        test("empty5") {
          val dom = Dom.element("html")
          assertTrue(dom.encode == """<!DOCTYPE html><html></html>""")
        } +
          test("with children") {
            val dom = Dom.element("html", Dom.element("head"))
            assertTrue(dom.encode == """<!DOCTYPE html><html><head></head></html>""")
          } +
          test("with children and text") {
            val dom = Dom.element("html", Dom.element("head"), Dom.text("abc"))
            assertTrue(dom.encode == """<!DOCTYPE html><html><head></head>abc</html>""")
          }
      } + suite("VoidElements") {
        testM("void") {
          checkAll(HttpGen.voidElement) { elem =>
            assertTrue(elem.encode == s"<${elem.name}/>")
          }
        } +
          testM("not void") {
            checkAll(HttpGen.notVoidElement) { elem =>
              assertTrue(elem.encode == s"<${elem.name}></${elem.name}>") ||
              assertTrue(elem.encode == s"<!DOCTYPE ${elem.name}><${elem.name}></${elem.name}>")
            }
          }
      }
  }
}
