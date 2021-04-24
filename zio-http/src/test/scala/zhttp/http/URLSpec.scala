package zhttp.http

import zio.test.Assertion._
import zio.test._

object URLSpec extends DefaultRunnableSpec {

  val fromStringSpec = suite("fromString")(
    test("Should Handle invalid url String with restricted chars") {
      assert(URL.fromString("http://mw1.google.com/$[level]/r$[y]_c$[x].jpg"))(isLeft)
    },
    test("Should Handle empty query string") {
      assert(URL.fromString("http://yourdomain.com/list/users").map(_.queryParams))(
        isRight(equalTo(Map.empty[String, List[String]])),
      )
    },
    test("Should Handle query string") {
      assert(
        URL
          .fromString("http://yourdomain.com/list/users?user_id=1&user_id=2&order=ASC&text=zio-http%20is%20awesome%21")
          .map(_.queryParams),
      )(
        isRight(
          equalTo(Map("user_id" -> List("1", "2"), "order" -> List("ASC"), "text" -> List("zio-http is awesome!"))),
        ),
      )
    },
  )

  val asStringSpec = {

    def roundtrip(url: String) =
      assert(URL.fromString(url).map(_.asString))(isRight(equalTo(url)))

    suite("toString")(
      test("relative with pathname only") {
        roundtrip("/users")
      },
      test("relative with query string") {
        roundtrip("/users?user_id=1&user_id=2&order=ASC&text=zio-http%20is%20awesome%21")
      },
      test("absolute with pathname only") {
        roundtrip("http://yourdomain.com/list")
      },
      test("absolute with query string") {
        roundtrip("http://yourdomain.com/list/users?user_id=1&user_id=2&order=ASC&text=zio-http%20is%20awesome%21")
      },
    )
  }

  def spec =
    suite("URL")(fromStringSpec, asStringSpec)
}
