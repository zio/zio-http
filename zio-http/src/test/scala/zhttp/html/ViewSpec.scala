package zhttp.html

import zhttp.html.Attributes.css
import zhttp.html.Elements._
import zio.test.Assertion.equalTo
import zio.test._

case object ViewSpec extends DefaultRunnableSpec {
  def spec = {
    suite("ViewSpec") {
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
