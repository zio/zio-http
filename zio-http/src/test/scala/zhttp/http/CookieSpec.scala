package zhttp.http

import zio.random.Random
import zio.test.Assertion.equalTo
import zio.test._

import scala.concurrent.duration.{Duration, DurationInt, SECONDS}
import scala.util.{Failure, Success, Try}

object CookieSpec extends DefaultRunnableSpec {
  def spec = suite("Cookies")(
    suite("toCookie")(
      test("should parse the cookie") {
        val cookieHeaderValue = "name=content; Max-Age=123; Secure; HttpOnly "
        assert(Cookie.fromString(cookieHeaderValue))(
          equalTo(Cookie("name", "content", None, None, None, true, true, Some(123 seconds), None)),
        )
      },
      test("should parse the cookie with empty content") {
        val cookieHeaderValue = "name=; Expires=1816; Max-Age=123; Secure; HttpOnly; Path=/cookie "
        assert(Cookie.fromString(cookieHeaderValue))(
          equalTo(
            Cookie("name", "", None, None, Some(Path("/cookie")), true, true, Some(123 seconds), None),
          ),
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
            maxAge = Some(5 days),
            sameSite = Some(SameSite.Lax),
          )
        assert(cookie.asString)(equalTo("name=content; Max-Age=432000; Path=/cookie; HttpOnly; SameSite=Lax"))
      },
      test("should convert cookie to string without meta") {
        val cookie = Cookie("name", "content")
        assert(cookie.asString)(equalTo("name=content"))
      },
    ),
    suite("encode/decode cookies")(
      testM("encode/decode cookies with ZIO Test Gen") {
        val genCookies: Gen[Random with Sized, Cookie] = for {
          name     <- Gen.anyString
          content  <- Gen.anyString
          expires  <- Gen.anyInstant
          domain   <- Gen.anyString
          path     <- Gen.fromIterable(List(Path("/"), Path(""), Path("/path")))
          secure   <- Gen.boolean
          httpOnly <- Gen.boolean
          maxAge   <- Gen.anyFiniteDuration
          sameSite <- Gen.fromIterable(List(SameSite.None, SameSite.Strict, SameSite.Lax))
        } yield Cookie(
          name,
          content,
          Some(expires),
          Some(domain),
          Some(path),
          secure,
          httpOnly,
          Some(Duration(Duration.fromNanos(maxAge.toNanos).toSeconds, SECONDS)),
          Some(sameSite),
        )

        check(genCookies) { cookie =>
          assert(Cookie.fromString(cookie.asString))(equalTo(cookie))
        }
      },
    ),
  )
}
