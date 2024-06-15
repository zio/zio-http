package zio.http.htmx

import zio.http.template.button
import zio.test.{ZIOSpecDefault, assertTrue}

case object HtmxSpec extends ZIOSpecDefault {
  override def spec = suite("HtmxSpec")(
    test("hx-get attribute") {
      val view     = button(hxGetAttr := "/test", "click")
      val expected = """<button hx-get="/test">click</button>"""
      assertTrue(view.encode == expected.stripMargin)
    },
  )
}
