package zhttp.html

import zhttp.html.Attributes.css
import zhttp.html.Elements._
import zio.test.Assertion.equalTo
import zio.test._

case object ViewSpec extends DefaultRunnableSpec {
  def spec = {
    suite("ViewSpec") {
      test("empty") {
        val dom = View.empty
        assert(dom.encode)(equalTo(""))
      } +
        test("text") {
          val dom = View.text("abc")
          assert(dom.encode)(equalTo("abc"))
        } +
        test("element") {
          val dom = View.element("div")
          assert(dom.encode)(equalTo("<div/>"))
        } +
        suite("element with children") {
          test("element with children") {
            val dom = View.element("div", View.element("div"))
            assert(dom.encode)(equalTo("<div><div/></div>"))
          } +
            test("element with multiple children") {
              val dom = View.element("div", View.element("div"), View.element("div"), View.element("div"))
              assert(dom.encode)(equalTo("<div><div/><div/><div/></div>"))
            } +
            test("element with nested children") {
              val dom = View.element("div", View.element("div", View.element("div", View.element("div"))))
              assert(dom.encode)(equalTo("<div><div><div><div/></div></div></div>"))
            } +
            test("element with text") {
              val dom = View.element("div", View.text("abc"))
              assert(dom.encode)(equalTo("<div>abc</div>"))
            }
        } +
        suite("Attribute") {
          test("constant") {
            val dom = View.attribute("href", "https://www.zio-http.com")
            assert(dom.encode)(equalTo("""href="https://www.zio-http.com""""))
          }
        } +
        suite("element with attributes") {
          test("constant") {
            val dom = View.element("a", View.attribute("href", "https://www.zio-http.com"))
            assert(dom.encode)(equalTo("""<a href="https://www.zio-http.com"/>"""))
          } +
            test("multiple constant") {
              val dom = View.element(
                "a",
                View.attribute("href", "https://www.zio-http.com"),
                View.attribute("title", "click me!"),
              )
              assert(dom.encode)(equalTo("""<a href="https://www.zio-http.com" title="click me!"/>"""))
            }
        } +
        test("element with attribute & children") {
          val dom = View.element(
            "a",
            View.attribute("href", "https://www.zio-http.com"),
            View.text("zio-http"),
          )

          assert(dom.encode)(
            equalTo("""<a href="https://www.zio-http.com">zio-http</a>"""),
          )
        }
    } + suite("SyntaxSpec") {
      test("tags") {
        val view     = html(head(), body(div()))
        val expected = """<html><head/><body><div/></body></html>"""
        assert(view.encode)(equalTo(expected.stripMargin))
      } +
        test("tags with attributes") {
          val view     = html(body(div(css := "container" :: Nil, "Hello!")))
          val expected = """<html><body><div class="container">Hello!</div></body></html>"""
          assert(view.encode)(equalTo(expected.stripMargin))
        } +
        test("tags with children") {
          val view     = html(body(div(css := "container" :: Nil, "Hello!", span("World!"))))
          val expected =
            """<html><body><div class="container">Hello!<span>World!</span></div></body></html>"""
          assert(view.encode)(equalTo(expected.stripMargin))
        } +
        test("tags with attributes and children") {
          val view     = html(body(div(css := "container" :: Nil, "Hello!", span("World!"))))
          val expected =
            """<html><body><div class="container">Hello!<span>World!</span></div></body></html>"""
          assert(view.encode)(equalTo(expected.stripMargin))
        } +
        test("tags with attributes and children") {
          val view     = html(body(div(css := "container" :: Nil, "Hello!", span("World!"))))
          val expected =
            """<html><body><div class="container">Hello!<span>World!</span></div></body></html>"""
          assert(view.encode)(equalTo(expected.stripMargin))
        } +
        test("tags with attributes and children") {
          val view     = html(body(div(css := "container" :: Nil, "Hello!", span("World!"))))
          val expected =
            """<html><body><div class="container">Hello!<span>World!</span></div></body></html>"""
          assert(view.encode)(equalTo(expected.stripMargin))
        } +
        test("tags with attributes and children") {
          val view     = div("Hello!", css := "container" :: Nil)
          val expected = """<div class="container">Hello!</div>"""
          assert(view.encode)(equalTo(expected.stripMargin))
        }
    }
  }
}
