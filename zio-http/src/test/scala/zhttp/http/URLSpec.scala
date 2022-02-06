package zhttp.http

import zhttp.http.URL6.Fragment
import zhttp.internal.HttpGen
import zio.test.Assertion._
import zio.test._

object URLSpec extends DefaultRunnableSpec {

  val fromStringSpec = suite("fromString")(
    test("Should Handle invalid url String with restricted chars") {
      assert(URL6.fromString("http://mw1.google.com/$[level]/r$[y]_c$[x].jpg"))(isLeft)
    } +
      test("Should Handle empty query string") {
        assert(URL6.fromString("http://yourdomain.com/list/users").map(_.toAbsolute.relative.queryParams))(
          isRight(equalTo(Map.empty[String, List[String]])),
        )
      } +
      test("Should Handle query string") {
        assert(
          URL6
            .fromString(
              "http://yourdomain.com/list/users?user_id=1&user_id=2&order=ASC&text=zio-http%20is%20awesome%21",
            )
            .map(_.toAbsolute.relative.queryParams),
        )(
          isRight(
            equalTo(Map("user_id" -> List("1", "2"), "order" -> List("ASC"), "text" -> List("zio-http is awesome!"))),
          ),
        )
      },
    test("Should handle uri fragment") {
      assert(
        URL6
          .fromString(
            "http://yourdomain.com/list/users?user_id=1&user_id=2&order=ASC&text=zio-http%20is%20awesome%21#the%20hash",
          )
          .map(_.toAbsolute.relative.fragment),
      )(
        isRight(
          isSome(equalTo(Fragment("the%20hash", "the hash"))),
        ),
      )
    },
  )

  val asStringSpec = {

    def roundtrip(url: String) =
      assert(URL6.fromString(url).map(_.encode))(isRight(equalTo(url)))

    suite("asString")(
      testM("using gen") {
        checkAll(HttpGen.url) { case url =>
          val source  = url.encode
          val decoded = URL6.fromString(source).map(_.encode)
          assert(decoded)(isRight(equalTo(source)))
        }
      } +
        test("empty") {
          val actual = URL6.fromString("/").map(_.encode)
          assert(actual)(isRight(equalTo("/")))
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
      val url = URL6
        .fromString("http://yourdomain.com/list/users?user_id=1&user_id=2&order=ASC&text=zio-http%20is%20awesome%21")
        .map(_.toAbsolute.relative)

      val expected =
        URL6.Relative(
          Path("/list/users"),
          Map("user_id" -> List("1", "2"), "order" -> List("ASC"), "text" -> List("zio-http is awesome!")),
        )

      assert(url)(isRight(equalTo(expected)))
    },
  )

  val builderSpec = suite("builder")(
    test("creates a URL with all attributes set") {
      val builderUrl = URL6.empty
        .setHost("www.yourdomain.com")
        .setPath("/list")
        .setPort(8080)
        .setScheme(Scheme.HTTPS)
        .setQueryParams("?type=builder&query=provided")

      assert(builderUrl.encode)(equalTo("https://www.yourdomain.com:8080/list?type=builder&query=provided"))
    },
    test("returns relative URL if port, host, and scheme are not set") {
      val builderUrl = URL6.empty
        .setPath(Path("/list"))
        .setQueryParams(
          Map("type" -> List("builder"), "query" -> List("provided")),
        )

      assert(builderUrl.encode)(equalTo("/list?type=builder&query=provided"))
    },
  )

  def spec =
    suite("URL")(fromStringSpec, asStringSpec, relativeSpec, builderSpec)
}
