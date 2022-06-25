package zhttp.http

import zhttp.internal.HttpGen
import zio.test.Assertion._
import zio.test._

object URLSpec extends DefaultRunnableSpec {
  def spec =
    suite("URL")(
      suite("encode-decode symmetry")(
        testM("auto-gen") {
          check(HttpGen.url) { url =>
            val expected        = url.normalize
            val expectedEncoded = expected.encode
            val actual          = URL.fromString(url.encode).map(_.normalize)
            val actualEncoded   = actual.map(_.encode)

            assertTrue(actualEncoded == Right(expectedEncoded)) &&
            assertTrue(actual == Right(expected))
          }
        },
        testM("manual") {
          val urls = Gen.fromIterable(
            Seq(
              "",
              "/",
              "/users?ord=ASC&txt=scala%20is%20awesome%21&u=1&u=2",
              "/users",
              "/users#the%20hash",
              "http://abc.com",
              "http://abc.com/",
              "http://abc.com/list",
              "http://abc.com/users?ord=ASC&txt=scala%20is%20awesome%21&u=1&u=2",
              "http://abc.com/users?u=1&u=2&ord=ASC&txt=scala%20is%20awesome%21",
              "http://abc.com/users?u=1#the%20hash",
              "http://abc.com/users",
              "http://abc.com/users/?u=1&u=2&ord=ASC&txt=scala%20is%20awesome%21",
              "http://abc.com/users#the%20hash",
              "ws://abc.com/subscriptions",
              "wss://abc.com/subscriptions",
            ),
          )

          checkAll(urls) { url =>
            val decoded = URL.fromString(url)
            val encoded = decoded.map(_.encode)
            assertTrue(encoded == Right(url))
          }
        },
      ),
      suite("fromString")(
        test("should Handle invalid url String with restricted chars") {
          val actual = URL.fromString("http://mw1.google.com/$[level]/r$[y]_c$[x].jpg")
          assert(actual)(isLeft)
        },
      ),
      suite("relative")(
        test("converts an url to a relative url") {
          val actual   = URL.fromString("http://abc.com/users?a=1&b=2").map(_.relative.normalize.encode)
          val expected = Right("/users?a=1&b=2")
          assertTrue(actual == expected)
        },
      ),
      suite("builder")(
        test("creates a URL with all attributes set") {
          val builderUrl = URL.empty
            .setHost("www.abc.com")
            .setPath("/list")
            .setPort(8080)
            .setScheme(Scheme.HTTPS)
            .setQueryParams("?type=builder&query=provided")

          assertTrue(builderUrl.normalize.encode == "https://www.abc.com:8080/list?query=provided&type=builder")
        },
        test("returns relative URL if port, host, and scheme are not set") {
          val actual = URL.empty
            .setPath(Path.decode("/list"))
            .setQueryParams(Map("type" -> List("builder"), "query" -> List("provided")))
            .normalize
            .encode

          assertTrue(actual == "/list?query=provided&type=builder")
        },
      ),
    )
}
