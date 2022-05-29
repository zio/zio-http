package zhttp.api

import zio.test.{DefaultRunnableSpec, assertTrue}

import java.util.UUID

object RequestGeneratorSpec extends DefaultRunnableSpec {

  def generateRequest[A](api: API[A, _, _])(params: A): (String, Map[String, String]) = {
    val state = new ClientInterpreter.RequestState
    ClientInterpreter.parseUrl(api.requestCodec, state)(params)
    state.result
  }

  def spec =
    suite("RequestGeneratorSpec")(
      test("generates basic paths") {
        val api                = API.get("users")
        val (request, headers) = generateRequest(api)(())

        assertTrue(
          request == "/users",
          headers.isEmpty,
        )
      },
      test("parses chained paths") {
        val api = API.get("users" / "comments")

        val (request, headers) = generateRequest(api)(())

        assertTrue(
          request == "/users/comments",
          headers.isEmpty,
        )
      },
      test("parses path arguments") {
        val api = API.get("users" / int)

        val (request, headers) = generateRequest(api)(1)

        assertTrue(
          request == "/users/1",
          headers.isEmpty,
        )
      },
      test("parses multiple path arguments") {
        val api: API[(String, UUID), Unit, Unit] = API.get("users" / string / "posts" / uuid)

        val id                 = UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11")
        val (request, headers) = generateRequest(api)(("foo", id))

        assertTrue(
          request == s"/users/foo/posts/$id",
          headers.isEmpty,
        )
      },
      test("parses query parameters") {
        val api = API.get("users").query(string("name") ++ int("age"))

        val (request, headers) = generateRequest(api)(("kit", 31))

        assertTrue(
          request == "/users?name=kit&age=31",
          headers.isEmpty,
        )
      },
      test("parses optional query parameters") {
        val api = API.get("users").query(string("name").? ++ int("age").?)

        val (request, _)  = generateRequest(api)((Some("kit"), Some(31)))
        val (request2, _) = generateRequest(api)((None, Some(31)))
        val (request3, _) = generateRequest(api)((Some("kit"), None))
        val (request4, _) = generateRequest(api)((None, None))

        assertTrue(
          request == "/users?name=kit&age=31",
          request2 == "/users?age=31",
          request3 == "/users?name=kit",
          request4 == "/users",
        )

      },
      test("parses headers") {
        val api = API.get("users").header(Header.string("Accept") ++ Header.string("Content"))

        val (request, headers) = generateRequest(api)(("application/json", "cool"))

        assertTrue(
          request == "/users",
          headers == Map("Accept" -> "application/json", "Content" -> "cool"),
        )
      },
      test("parses optional headers") {
        val api = API.get("users").header(Header.string("Accept").? ++ Header.string("Content").?)

        val (request, headers1) = generateRequest(api)((Some("application/json"), Some("cool")))
        val (_, headers2)       = generateRequest(api)((None, Some("cool")))
        val (_, headers3)       = generateRequest(api)((Some("application/json"), None))
        val (_, headers4)       = generateRequest(api)((None, None))

        assertTrue(
          request == "/users",
          headers1 == Map("Accept" -> "application/json", "Content" -> "cool"),
          headers2 == Map("Content" -> "cool"),
          headers3 == Map("Accept" -> "application/json"),
          headers4.isEmpty,
        )
      },
    )

}
