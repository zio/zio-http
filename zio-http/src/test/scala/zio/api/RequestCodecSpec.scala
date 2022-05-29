package zhttp.api

import zhttp.http.{Headers, Request, URL}
import zio.test.{DefaultRunnableSpec, assertTrue}

object RequestCodecSpec extends DefaultRunnableSpec {

  def parseRequest[A](api: API[A, _, _]): Request => Option[A] =
    api.requestCodec.parseRequest

  def spec =
    suite("RequestParsingSpec")(
      test("parses basic paths") {
        val api     = API.get("users")
        val matcher = parseRequest(api)

        assertTrue(
          matcher(get("/users")).contains(()),
          matcher(get("/posts")).isEmpty,
        )
      },
      test("parses chained paths") {
        val api     = API.get("users" / "comments")
        val matcher = parseRequest(api)

        assertTrue(
          matcher(get("/users/comments")).contains(()),
          matcher(get("/users")).isEmpty,
          matcher(get("/posts/comments")).isEmpty,
        )
      },
      test("parses path arguments") {
        val api     = API.get("users" / int)
        val matcher = parseRequest(api)

        assertTrue(
          matcher(get("/users/12")).contains(12),
          matcher(get("/users/105")).contains(105),
          matcher(get("/users/10oops")).isEmpty,
          matcher(get("/users/hello")).isEmpty,
        )
      },
      test("parses multiple path arguments") {
        val api     = API.get("users" / int / "posts" / string)
        val matcher = parseRequest(api)

        assertTrue(
          matcher(get("/users/12/posts/my-first-post")).contains((12, "my-first-post")),
          matcher(get("/users/12/posts")).isEmpty,
        )
      },
      test("parses query parameters") {
        val api     = API.get("users").query(string("name") ++ int("age"))
        val matcher = parseRequest(api)

        assertTrue(
          matcher(get("/users?name=Kit&age=28")).contains(("Kit", 28)),
          matcher(get("/users?age=28&name=Kit")).contains(("Kit", 28)),
          matcher(get("/users?name=Kit")).isEmpty,
          matcher(get("/users?age=28")).isEmpty,
          matcher(get("/users")).isEmpty,
        )
      },
      test("parses optional query parameters") {
        val api     = API.get("users").query(string("name").? ++ int("age").?)
        val matcher = parseRequest(api)

        assertTrue(
          matcher(get("/users?name=Kit&age=28")).contains((Some("Kit"), Some(28))),
          matcher(get("/users?age=28&name=Kit")).contains((Some("Kit"), Some(28))),
          matcher(get("/users?name=Kit")).contains((Some("Kit"), None)),
          matcher(get("/users?age=28")).contains((None, Some(28))),
          matcher(get("/users")).contains((None, None)),
          matcher(get("/posts?name=Kit&age=28")).isEmpty,
        )
      },
      test("parses headers") {
        val api     = API.get("users").header(Header.string("Accept") ++ Header.string("Content"))
        val matcher = parseRequest(api)

        assertTrue(
          matcher(get("users", Map("Accept" -> "application/json", "Content" -> "text")))
            .contains(("application/json", "text")),
          matcher(get("users", Map("Accept" -> "application/json"))).isEmpty,
          matcher(get("users", Map("Content" -> "text"))).isEmpty,
          matcher(get("users")).isEmpty,
        )
      },
      test("parses optional headers") {
        val api     = API.get("users").header(Header.string("Accept").? ++ Header.string("Content").?)
        val matcher = parseRequest(api)

        assertTrue(
          matcher(get("users", Map("Accept" -> "application/json", "Content" -> "text")))
            .contains((Some("application/json"), Some("text"))),
          matcher(get("users", Map("Accept" -> "application/json"))).contains((Some("application/json"), None)),
          matcher(get("users", Map("Content" -> "text"))).contains((None, Some("text"))),
          matcher(get("users")).contains((None, None)),
          matcher(get("posts", Map("Accept" -> "application/json", "Content" -> "text"))).isEmpty,
        )
      },
      test("big match") {
        val api = API
          .get("users" / int / "comments" / int / "dogs" / string / "cats" / string / "mammals" / string / "eskimos")
          .query(string("name"))

        val matcher = parseRequest(api)

        assertTrue(
          matcher(get("/users/12/comments/14/dogs/scrappy/cats/crumb/mammals/fancy/eskimos?name=kit")).contains(
            (12, 14, "scrappy", "crumb", "fancy", "kit"),
          ),
        )
      },
    )

  def get(url: String, headers: Map[String, String] = Map.empty): Request =
    Request(url = URL.fromString(url).toOption.get, headers = Headers(headers.toList))
}
