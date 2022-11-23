package zio.http.model.headers.values

import zio.Scope
import zio.http.model.Cookie.Type.RequestType
import zio.http.model
import zio.test.{Spec, TestEnvironment, ZIOSpecDefault, assertTrue}

object RequestCookieSpec extends ZIOSpecDefault {

  override def spec: Spec[TestEnvironment with Scope, Any] = suite("Cookie suite")(
    test("Cookie handle valid cookie") {
      val result = RequestCookie.toCookie("foo=bar") match {
        case RequestCookie.CookieValue(value) =>
          value == List(model.Cookie(name = "foo", content = "bar", target = RequestType))
        case _                                => false
      }
      assertTrue(result)
    },
  )
}
