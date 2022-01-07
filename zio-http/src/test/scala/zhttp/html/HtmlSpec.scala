package zhttp.html

import zio.test._

case object HtmlSpec extends DefaultRunnableSpec {
  def spec = {
    suite("HtmlSpec")(
      test("tags") {
        val view     = html(head(), body(div()))
        val expected = """<html><head></head><body><div></div></body></html>"""
        assertTrue(view.encode == expected.stripMargin)
      } +
        test("tags with attributes") {
          val view     = html(body(div(css := "container" :: Nil, "Hello!")))
          val expected = """<html><body><div class="container">Hello!</div></body></html>"""
          assertTrue(view.encode == expected.stripMargin)
        } +
        test("tags with children") {
          val view     = html(body(div(css := "container" :: Nil, "Hello!", span("World!"))))
          val expected =
            """<html><body><div class="container">Hello!<span>World!</span></div></body></html>"""
          assertTrue(view.encode == expected.stripMargin)
        } +
        test("tags with attributes and children") {
          val view     = html(body(div(css := "container" :: Nil, "Hello!", span("World!"))))
          val expected =
            """<html><body><div class="container">Hello!<span>World!</span></div></body></html>"""
          assertTrue(view.encode == expected.stripMargin)
        } +
        test("tags with attributes and children") {
          val view     = html(body(div(css := "container" :: Nil, "Hello!", span("World!"))))
          val expected =
            """<html><body><div class="container">Hello!<span>World!</span></div></body></html>"""
          assertTrue(view.encode == expected.stripMargin)
        } +
        test("tags with attributes and children") {
          val view     = html(body(div(css := "container" :: Nil, "Hello!", span("World!"))))
          val expected =
            """<html><body><div class="container">Hello!<span>World!</span></div></body></html>"""
          assertTrue(view.encode == expected.stripMargin)
        } +
        test("tags with attributes and children") {
          val view     = div("Hello!", css := "container" :: Nil)
          val expected = """<div class="container">Hello!</div>"""
          assertTrue(view.encode == expected.stripMargin)
        },
    )
  }
}
