package zhttp.http

import zhttp.http.URL.Fragment
import zhttp.internal.HttpGen
import zio.test.Assertion._
import zio.test._

object URLSpec extends DefaultRunnableSpec {

  val fromStringSpec = suite("fromString")(
    test("Should Handle invalid url String with restricted chars") {
      assert(URL.fromString("http://mw1.google.com/$[level]/r$[y]_c$[x].jpg"))(isLeft)
    } +
      test("Should Handle empty query string") {
        assert(URL.fromString("http://yourdomain.com/list/users").map(_.queryParams))(
          isRight(equalTo(Map.empty[String, List[String]])),
        )
      } +
      test("Should Handle query string") {
        assert(
          URL
            .fromString(
              "http://yourdomain.com/list/users?user_id=1&user_id=2&order=ASC&text=zio-http%20is%20awesome%21",
            )
            .map(_.queryParams),
        )(
          isRight(
            equalTo(Map("user_id" -> List("1", "2"), "order" -> List("ASC"), "text" -> List("zio-http is awesome!"))),
          ),
        )
      },
    test("Should handle uri fragment") {
      assert(
        URL
          .fromString(
            "http://yourdomain.com/list/users?user_id=1&user_id=2&order=ASC&text=zio-http%20is%20awesome%21#the%20hash",
          )
          .map(_.fragment),
      )(
        isRight(
          isSome(equalTo(Fragment("the%20hash", "the hash"))),
        ),
      )
    },
  )

  val asStringSpec = {

    def roundtrip(url: String) =
      assert(URL.fromString(url).map(_.encode))(isRight(equalTo(url)))

    suite("asString")(
      testM("using gen") {
        check(HttpGen.url) { case url =>
          val source  = url.encode
          val decoded = URL.fromString(source)
          assert(decoded.map(_.isEqual(url)))(isRight(equalTo(true)))
        }
      } +
        test("empty") {
          val actual = URL.fromString("/").map(_.encode)
          assert(actual)(isRight(equalTo("/")))
        } +
        test("ws scheme") {
          roundtrip("ws://yourdomain.com/subscriptions")
        } +
        test("wss scheme") {
          roundtrip("wss://yourdomain.com/subscriptions")
        } +
        test("relative with pathname only") {
          roundtrip("/users")
        } +
        test("relative with query string") {
          roundtrip("/users?user_id=1&user_id=2&order=ASC&text=zio-http%20is%20awesome%21")
        } +
        test("absolute with pathname only") {
          roundtrip("http://yourdomain.com/list")
        } +
        test("absolute with query string") {
          roundtrip("http://yourdomain.com/list/users?user_id=1&user_id=2&order=ASC&text=zio-http%20is%20awesome%21")
        },
      test("absolute with fragment") {
        roundtrip("http://yourdomain.com/list/users#the%20hash")
      },
      test("relative with fragment") {
        roundtrip("/list/users#the%20hash")
      },
    )
  }

  val relativeSpec = suite("relative")(
    test("converts an url to a relative url") {
      val url = URL
        .fromString("http://yourdomain.com/list/users?user_id=1&user_id=2&order=ASC&text=zio-http%20is%20awesome%21")
        .map(_.relative)

      val expected =
        URL(
          Path.decode("/list/users"),
          URL.Location.Relative,
          Map("user_id" -> List("1", "2"), "order" -> List("ASC"), "text" -> List("zio-http is awesome!")),
        )

      assert(url)(isRight(equalTo(expected)))
    },
  )

  val builderSpec = suite("builder")(
    test("creates a URL with all attributes set") {
      val builderUrl = URL.empty
        .setHost("www.yourdomain.com")
        .setPath("/list")
        .setPort(8080)
        .setScheme(Scheme.HTTPS)
        .setQueryParams("?type=builder&query=provided")

      assert(builderUrl.encode)(equalTo("https://www.yourdomain.com:8080/list?type=builder&query=provided"))
    },
    test("returns relative URL if port, host, and scheme are not set") {
      val builderUrl = URL.empty
        .setPath(Path.decode("/list"))
        .setQueryParams(
          Map("type" -> List("builder"), "query" -> List("provided")),
        )

      assert(builderUrl.encode)(equalTo("/list?type=builder&query=provided"))
    },
  )

  def spec =
    suite("URL")(fromStringSpec, asStringSpec, relativeSpec, builderSpec)
}
