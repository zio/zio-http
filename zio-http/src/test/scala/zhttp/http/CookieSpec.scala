package zhttp.http

import zio.test.Assertion.equalTo
import zio.test.{DefaultRunnableSpec, assert}

object CookieSpec extends DefaultRunnableSpec {
  def spec = suite("Cookies")(
    suite("toCookie")(
      test("should parse the cookie") {
        val cookieHeaderValue = "name=content; Expires=1817616; Max-Age= 123; Secure; HttpOnly "
        assert(Cookie.toCookie(cookieHeaderValue))(equalTo(Cookie("name", "content")))
      },
      test("should parse the cookie with empty content") {
        val cookieHeaderValue = "name=; Expires=1817616; Max-Age= 123; Secure; HttpOnly "
        assert(Cookie.toCookie(cookieHeaderValue))(equalTo(Cookie("name", "")))
      },
    ),
    suite("fromCookie")(
      test("should convert cookie to string with meta") {
        val cookie = Cookie("name", "content", Some(Meta(None, None, Some(Path("/cookie")), false, true, None, None)))
        assert(cookie.fromCookie)(equalTo("name=content; Path=/cookie; HttpOnly=true; SameSite=Lax"))
      },
      test("should convert cookie to string without meta") {
        val cookie = Cookie("name", "content")
        assert(cookie.fromCookie)(equalTo("name=content"))
      },
    ),
  )
}
