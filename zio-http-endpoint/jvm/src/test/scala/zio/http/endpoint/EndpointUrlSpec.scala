package zio.http.endpoint

import zio.test._

import zio.http.Method.GET
import zio.http._

object EndpointUrlSpec extends ZIOHttpSpec {
  def spec =
    suite("EndpointUrlSpec")(
      test("absolute base path with path params") {
        val endpoint = Endpoint(GET / "users" / int("userId") / "posts" / string("postId"))
        val userId   = 42
        val postId   = "abc"
        val result   = endpoint.url("https://api.example.com", (userId, postId))
        val expected = s"https://api.example.com/users/$userId/posts/$postId"
        assertTrue(result.toOption.get.encode == expected)
      },
      test("relative url with path params") {
        val endpoint = Endpoint(GET / "users" / int("userId") / "posts" / string("postId"))
        val result   = endpoint.urlRelative((100, "zzz"))
        assertTrue(result.toOption.get.encode == "/users/100/posts/zzz")
      },
      test("urlFromRequest uses https host and port from absolute request URL") {
        val endpoint = Endpoint(GET / "users" / int("userId") / "posts" / string("postId"))
        val request  = Request.get(URL.decode("https://example.org:8443/anything").toOption.get)
        val result   = endpoint.urlFromRequest((1, "abc"))(request)
        assertTrue(result.toOption.get.encode == "https://example.org:8443/users/1/posts/abc")
      },
      test("urlFromRequest fills defaults for relative request (http://localhost)") {
        val endpoint = Endpoint(GET / "health")
        val request  = Request.get(URL.decode("/").toOption.get)
        val result   = endpoint.urlFromRequest(())(request)
        assertTrue(result.toOption.get.encode == "http://localhost/health")
      },
    )
}
