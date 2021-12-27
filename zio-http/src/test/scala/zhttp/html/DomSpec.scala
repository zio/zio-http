package zhttp.html

import zio.test.Assertion.equalTo
import zio.test.{DefaultRunnableSpec, assert}

object DomSpec extends DefaultRunnableSpec {
  def spec = suite("DomSpec") {
    test("empty") {
      val dom = Dom.empty
      assert(dom.encode)(equalTo(""))
    } +
      test("text") {
        val dom = Dom.text("abc")
        assert(dom.encode)(equalTo("abc"))
      } +
      test("element") {
        val dom = Dom.element("div")
        assert(dom.encode)(equalTo("<div/>"))
      } +
      suite("element with children") {
        test("element with children") {
          val dom = Dom.element("div", Dom.element("div"))
          assert(dom.encode)(equalTo("<div><div/></div>"))
        } +
          test("element with multiple children") {
            val dom = Dom.element("div", Dom.element("div"), Dom.element("div"), Dom.element("div"))
            assert(dom.encode)(equalTo("<div><div/><div/><div/></div>"))
          } +
          test("element with nested children") {
            val dom = Dom.element("div", Dom.element("div", Dom.element("div", Dom.element("div"))))
            assert(dom.encode)(equalTo("<div><div><div><div/></div></div></div>"))
          } +
          test("element with text") {
            val dom = Dom.element("div", Dom.text("abc"))
            assert(dom.encode)(equalTo("<div>abc</div>"))
          }
      } +
      suite("Attribute") {
        test("constant") {
          val dom = Dom.attr("href", "https://www.zio-http.com")
          assert(dom.encode)(equalTo("""href="https://www.zio-http.com""""))
        }
      } +
      suite("element with attributes") {
        test("constant") {
          val dom = Dom.element("a", Dom.attr("href", "https://www.zio-http.com"))
          assert(dom.encode)(equalTo("""<a href="https://www.zio-http.com"/>"""))
        } +
          test("multiple constant") {
            val dom = Dom.element(
              "a",
              Dom.attr("href", "https://www.zio-http.com"),
              Dom.attr("title", "click me!"),
            )
            assert(dom.encode)(equalTo("""<a href="https://www.zio-http.com" title="click me!"/>"""))
          }
      } +
      test("element with attribute & children") {
        val dom = Dom.element(
          "a",
          Dom.attr("href", "https://www.zio-http.com"),
          Dom.text("zio-http"),
        )

        assert(dom.encode)(
          equalTo("""<a href="https://www.zio-http.com">zio-http</a>"""),
        )
      } +
      suite("doctype") {
        test("empty5") {
          val dom = Dom.element("html")
          assert(dom.encode)(equalTo("""<!DOCTYPE html><html/>"""))
        } +
          test("with children") {
            val dom = Dom.element("html", Dom.element("head"))
            assert(dom.encode)(equalTo("""<!DOCTYPE html><html><head/></html>"""))
          } +
          test("with children and text") {
            val dom = Dom.element("html", Dom.element("head"), Dom.text("abc"))
            assert(dom.encode)(equalTo("""<!DOCTYPE html><html><head/>abc</html>"""))
          }
      }
  }
}
