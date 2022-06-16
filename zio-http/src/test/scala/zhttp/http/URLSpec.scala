package zhttp.http

import zhttp.http.URL.Fragment
import zhttp.internal.HttpGen
import zio.test.Assertion._
import zio.test._

object URLSpec extends DefaultRunnableSpec {
  def spec =
    suite("URL")(
      suite("fromString")(
        test("should Handle invalid url String with restricted chars") {
          val actual = URL.fromString("http://mw1.google.com/$[level]/r$[y]_c$[x].jpg")
          assert(actual)(isLeft)
        },
        test("should Handle empty query string") {
          val actual = URL.fromString("http://abc.com/users").map(_.queryParams)
          assert(actual)(isRight(equalTo(Map.empty[String, List[String]])))
        },
        test("should Handle query string") {
          val actual = URL
            .fromString("http://abc.com/users?u=1&u=2&ord=ASC&txt=zio-http%20is%20awesome%21")
            .map(_.queryParams)

          val expected = Map(
            "u"   -> List("1", "2"),
            "ord" -> List("ASC"),
            "txt" -> List("zio-http is awesome!"),
          )

          assert(actual)(isRight(equalTo(expected)))
        },
        test("should handle uri fragment") {
          val actual = URL
            .fromString(
              "http://abc.com/users?u=1&u=2&ord=ASC&txt=zio-http%20is%20awesome%21#the%20hash",
            )
            .map(_.fragment)

          val expected = Fragment("the%20hash", "the hash")
          assert(actual)(isRight(isSome(equalTo(expected))))
        },
      ),
      suite("asString")(
        testM("using auto gen") {
          check(HttpGen.url) { url =>
            val expected        = url.normalize
            val expectedEncoded = expected.encode
            val actual          = URL.fromString(url.encode).map(_.normalize)
            val actualEncoded   = actual.map(_.encode)

            assertTrue(actualEncoded == Right(expectedEncoded)) &&
            assertTrue(actual == Right(expected))
          }
        },
        testM("using manual gen") {
          val urls = Gen.fromIterable(
            Seq(
              "ws://abc.com/subscriptions",
              "wss://abc.com/subscriptions",
              "/users",
              "/users?ord=ASC&txt=zio-http%20is%20awesome%21&u=1&u=2",
              "http://abc.com/list",
              "http://abc.com/users?ord=ASC&txt=zio-http%20is%20awesome%21&u=1&u=2",
              "http://abc.com/users#the%20hash",
              "/users#the%20hash",
              "/",
              "",
            ),
          )

          checkAll(urls) { url =>
            val expected = url
            val actual   = URL.fromString(expected).map(_.encode)
            assert(actual)(isRight(equalTo(expected)))
          }
        },
      ),
      suite("relative")(
        test("converts an url to a relative url") {
          val url = URL
            .fromString("http://abc.com/users?u=1&u=2&ord=ASC&txt=zio-http%20is%20awesome%21")
            .map(_.relative.normalize)

          val expected =
            URL(
              Path.root / "users",
              URL.Location.Relative,
              Map(
                "u"   -> List("1", "2"),
                "ord" -> List("ASC"),
                "txt" -> List("zio-http is awesome!"),
              ),
            ).normalize

          assert(url)(isRight(equalTo(expected)))
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
