package zio.http.model.headers.values

import zio.Scope
import zio.http.model.Cookie.Type.RequestType
import zio.http.model
import zio.test.{Spec, TestEnvironment, ZIOSpecDefault, assertTrue}

object RequestCookieSpec extends ZIOSpecDefault {

  override def spec: Spec[TestEnvironment with Scope, Any] = suite("RequestCookie suite")(
    test("RequestCookie handle valid cookie") {
      val result = RequestCookie.toCookie("foo=bar") match {
        case RequestCookie.CookieValue(value) =>
          value == List(model.Cookie(name = "foo", content = "bar", target = RequestType))
        case _                                => false
      }
      assertTrue(result)
    },
    test("RequestCookie handle invalid cookie") {
      val result = RequestCookie.toCookie("") match {
        case RequestCookie.CookieValue(_) =>
          false
        case _                            => true
      }
      assertTrue(result)
    },
    test("RequestCookie handle multiple cookies") {
      val result = RequestCookie.toCookie("foo=bar; foo2=bar2") match {
        case RequestCookie.CookieValue(value) =>
          value == List(
            model.Cookie(name = "foo", content = "bar", target = RequestType),
            model.Cookie(name = "foo2", content = "bar2", target = RequestType),
          )
        case _                                => false
      }
      assertTrue(result)
    },
    test("RequestCookie render valid cookie") {
      val result = RequestCookie.toCookie("foo=bar") match {
        case rc: RequestCookie.CookieValue =>
          RequestCookie.fromCookie(rc) == "foo=bar"
        case _                             => false
      }
      assertTrue(result)
    },
    test("RequestCookie render invalid cookie") {
      val result = RequestCookie.toCookie("") match {
        case _: RequestCookie.CookieValue =>
          false
        case _                             => true
      }
      assertTrue(result)
    },
  )
}
