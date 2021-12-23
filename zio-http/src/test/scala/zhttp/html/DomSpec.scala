package zhttp.html

import zhttp.html.Dom.Attribute
import zio.test.Assertion.equalTo
import zio.test._

case object DomSpec extends DefaultRunnableSpec {
  def spec = {
    suite("DomSpec") {
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
            val dom = Attribute("href", "https://www.zio-http.com")
            assert(dom.encode)(equalTo("""href="https://www.zio-http.com""""))
          } +
            test("collection") {
              val dom = Attribute("style", "color" -> "red", "height" -> "1024px")
              assert(dom.encode)(equalTo("""style="color:red;height:1024px""""))
            }
        } +
        suite("element with attributes") {
          test("constant") {
            val dom = Dom.element("a", Attribute("href", "https://www.zio-http.com"))
            assert(dom.encode)(equalTo("""<a href="https://www.zio-http.com"/>"""))
          } +
            test("multiple constant") {
              val dom = Dom.element(
                "a",
                Attribute("href", "https://www.zio-http.com"),
                Attribute("title", "click me!"),
              )
              assert(dom.encode)(equalTo("""<a href="https://www.zio-http.com" title="click me!"/>"""))
            } +
            test("collection") {
              val dom = Dom.element("a", Attribute("style", "color" -> "red", "height" -> "1024px"))
              assert(dom.encode)(equalTo("""<a style="color:red;height:1024px"/>"""))
            }
        } +
        test("element with attribute & children") {
          val dom = Dom.element(
            "a",
            Attribute("style", "color" -> "red", "height" -> "1024px"),
            Attribute("href", "https://www.zio-http.com"),
            Dom.text("zio-http"),
          )

          assert(dom.encode)(
            equalTo("""<a style="color:red;height:1024px" href="https://www.zio-http.com">zio-http</a>"""),
          )
        }
    }
  }
}
