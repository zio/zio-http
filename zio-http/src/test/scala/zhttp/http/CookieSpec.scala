package zhttp.http

import zio.test.Assertion.equalTo
import zio.test.{DefaultRunnableSpec, assert}

import scala.util.{Failure, Success, Try}

object CookieSpec extends DefaultRunnableSpec {
  def spec = suite("Cookies")(
    suite("toCookie")(
      test("should parse the cookie") {
        val cookieHeaderValue = "name=content; Max-Age= 123; Secure; HttpOnly "
        assert(Cookie.fromString(cookieHeaderValue))(
          equalTo(Cookie("name", "content", None, None, None, true, true, Some(123L), None)),
        )
      },
      test("should parse the cookie with empty content") {
        val cookieHeaderValue = "name=; Expires=1816; Max-Age= 123; Secure; HttpOnly; Path=/cookie "
        assert(Cookie.fromString(cookieHeaderValue))(
          equalTo(Cookie("name", "", None, None, Some("/cookie"), true, true, Some(123), None)),
        )
      },
      test("shouldn't parse the cookie with empty content and empty name") {
        val cookieHeaderValue = ""
        val actual            = Try {
          Cookie.fromString(cookieHeaderValue)
        } match {
          case Failure(_)     => "failure"
          case Success(value) => value.toString
        }
        assert(actual)(equalTo("failure"))
      },
    ),
    suite("asString in cookie")(
      test("should convert cookie to string with meta") {
        val cookie = Cookie("name", "content", None, None, Some("/cookie"), false, true, None, None)
        assert(cookie.asString)(equalTo("name=content; Path=/cookie; HttpOnly"))
      },
      test("should convert cookie to string with meta") {
        val cookie =
          Cookie(
            name = "name",
            content = "content",
            expires = None,
            domain = None,
            path = Some("/cookie"),
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
