package zio.http.model.headers.values

import zio.Scope
import zio.test.{Spec, TestEnvironment, ZIOSpecDefault, assertTrue}

object ResponseCookieSpec extends ZIOSpecDefault {
  override def spec: Spec[TestEnvironment with Scope, Any] = suite("ResponseCookieSpec suite")(
    test("ResponseCookie handle valid cookie") {
      val result = ResponseCookie.toCookie("foo=bar") match {
        case ResponseCookie.CookieValue(value) =>
          value.name == "foo" && value.content == "bar"
        case _                                 => false
      }
      assertTrue(result)
    },
    test("ResponseCookie handle invalid cookie") {
      val result = ResponseCookie.toCookie("") match {
        case ResponseCookie.CookieValue(_) =>
          false
        case _                             => true
      }
      assertTrue(result)
    },
    test("ResponseCookie render valid cookie") {
      val result = ResponseCookie.toCookie("foo=bar") match {
        case rc: ResponseCookie.CookieValue =>
          ResponseCookie.fromCookie(rc) == "foo=bar"
        case _                              => false
      }
      assertTrue(result)
    },
  )
}
