package zhttp.http

import zio.test.Assertion.equalTo
import zio.test.{DefaultRunnableSpec, assert}

object CookieSpec extends DefaultRunnableSpec {
  def spec = suite("Cookies")(
    suite("toCookie")(
      test("should parse the cookie") {
        val cookieHeaderValue = "name=content; Expires=1817616; Max-Age= 123; Secure; HttpOnly "
        assert(Cookie.fromString(cookieHeaderValue))(equalTo(Cookie("name", "content")))
      },
      test("should parse the cookie with empty content") {
        val cookieHeaderValue = "name=; Expires=1817616; Max-Age= 123; Secure; HttpOnly "
        assert(Cookie.fromString(cookieHeaderValue))(equalTo(Cookie("name", "")))
      },
    ),
    suite("asString in cookie")(
      test("should convert cookie to string with meta") {
        val cookie = Cookie("name", "content", None, None, Some(Path("/cookie")), false, true, None, None)
        assert(cookie.asString)(equalTo("name=content; Path=/cookie; HttpOnly"))
      },
      test("should convert cookie to string with meta") {
        val cookie =
          Cookie(
            name = "name",
            content = "content",
            expires = None,
            domain = None,
            path = Some(Path("/cookie")),
            httpOnly = true,
            maxAge = Some(0L),
            sameSite = Some(SameSite.Lax),
          )
        assert(cookie.asString)(equalTo("name=content; Max-Age=0; Path=/cookie; HttpOnly; SameSite=Lax"))
      },
      test("should convert cookie to string without meta") {
        val cookie = Cookie("name", "content")
        assert(cookie.asString)(equalTo("name=content"))
      },
    ),
  )
}
