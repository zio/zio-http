package zhttp.http

import zio.test.Assertion.{equalTo, isNone, isSome}
import zio.test.{DefaultRunnableSpec, assert}

object CookieSpec extends DefaultRunnableSpec {
  def spec = suite("Cookies")(
    suite("toCookie")(
      test("should parse the cookie") {
        val cookieHeaderValue = "name=content; Expires=1817616; Max-Age= 123; Secure; HttpOnly "
        assert(Cookie.toCookie(cookieHeaderValue))(isSome(equalTo(Cookie("name", "content"))))
      },
      test("shouldn't parse cookie with invalid name") {
        val cookieHeaderValue = "s s=content; Expires=1817616; Max-Age= 123; Secure; HttpOnly "
        assert(Cookie.toCookie(cookieHeaderValue))(isNone)
      },
      test("should parse the cookie with empty content") {
        val cookieHeaderValue = "name=; Expires=1817616; Max-Age= 123; Secure; HttpOnly "
        assert(Cookie.toCookie(cookieHeaderValue))(isSome(equalTo(Cookie("name", ""))))
      },
    ),
    suite("toString in cookie")(
      test("should convert cookie to string with meta") {
        val cookie = Cookie("name", "content", Some(Meta(None, None, Some(Path("/cookie")), false, true, None, None)))
        assert(cookie.toString)(equalTo("name=content; Path=/cookie; HttpOnly"))
      },
      test("should convert cookie to string with meta") {
        val cookie =
          Cookie(
            "name",
            "content",
            Some(Meta(None, None, Some(Path("/cookie")), false, true, Some(0L), Some(SameSite.Lax))),
          )
        assert(cookie.toString)(equalTo("name=content; Max-Age=0; Path=/cookie; HttpOnly; SameSite=Lax"))
      },
      test("should convert cookie to string without meta") {
        val cookie = Cookie("name", "content")
        assert(cookie.toString)(equalTo("name=content"))
      },
      test("should not  convert invalid cookie to string") {
        val cookie = Cookie("na me", "content")
        assert(cookie.toString)(equalTo("invalid cookie: cannot use Separators or control characters"))
      },
    ),
  )
}
