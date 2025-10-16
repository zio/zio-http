package zio.http.datastar

import zio._
import zio.test._

import zio.schema.{DeriveSchema, Schema}

import zio.http._
import zio.http.codec._
import zio.http.endpoint._
import zio.http.template2._

object EndpointRequestSpec extends ZIOSpecDefault {

  case class User(id: Int, name: String)
  object User {
    implicit val schema: Schema[User] = DeriveSchema.gen[User]
  }

  case class CreateUserRequest(name: String, email: String)
  object CreateUserRequest {
    implicit val schema: Schema[CreateUserRequest] = DeriveSchema.gen[CreateUserRequest]
  }

  override def spec = suite("EndpointRequestSpec")(
    suite("basic request creation")(
      test("should create GET request") {
        val request = EndpointRequest.get("/api/users")
        assertTrue(
          request.method == Method.GET,
          request.url == "/api/users",
          request.toActionExpression.value == "@get('/api/users')",
        )
      },
      test("should create POST request") {
        val request = EndpointRequest.post("/api/users")
        assertTrue(
          request.method == Method.POST,
          request.url == "/api/users",
          request.toActionExpression.value == "@post('/api/users')",
        )
      },
      test("should create PUT request") {
        val request = EndpointRequest.put("/api/users/1")
        assertTrue(
          request.method == Method.PUT,
          request.url == "/api/users/1",
          request.toActionExpression.value == "@put('/api/users/1')",
        )
      },
      test("should create PATCH request") {
        val request = EndpointRequest.patch("/api/users/1")
        assertTrue(
          request.method == Method.PATCH,
          request.url == "/api/users/1",
          request.toActionExpression.value == "@patch('/api/users/1')",
        )
      },
      test("should create DELETE request") {
        val request = EndpointRequest.delete("/api/users/1")
        assertTrue(
          request.method == Method.DELETE,
          request.url == "/api/users/1",
          request.toActionExpression.value == "@delete('/api/users/1')",
        )
      },
    ),
    suite("request with headers")(
      test("should add single header") {
        val request = EndpointRequest
          .get("/api/users")
          .withHeader("Authorization", "Bearer token")
        assertTrue(
          request.headers.contains("Authorization"),
          request.headers("Authorization") == "Bearer token",
          request.toFetchExpression.value.contains("headers:"),
        )
      },
      test("should add multiple headers") {
        val request = EndpointRequest
          .get("/api/users")
          .withHeaders(Map("Authorization" -> "Bearer token", "X-Custom" -> "value"))
        assertTrue(
          request.headers.size == 2,
          request.headers("Authorization") == "Bearer token",
          request.headers("X-Custom") == "value",
        )
      },
      test("should render fetch expression with headers") {
        val request = EndpointRequest
          .get("/api/users")
          .withHeader("X-Custom", "test")
        val expr    = request.toFetchExpression.value
        assertTrue(
          expr.contains("@get('/api/users',"),
          expr.contains("headers: {'X-Custom': 'test'}"),
        )
      },
    ),
    suite("request options")(
      test("should set includeHeaders option") {
        val request = EndpointRequest.get("/api/users").withIncludeHeaders(true)
        assertTrue(
          request.includeHeaders == true,
          request.toFetchExpression.value.contains("includeHeaders: true"),
        )
      },
      test("should set onlyIfMissing option") {
        val request = EndpointRequest.get("/api/users").withOnlyIfMissing(true)
        assertTrue(
          request.onlyIfMissing == true,
          request.toFetchExpression.value.contains("onlyIfMissing: true"),
        )
      },
      test("should combine multiple options") {
        val request = EndpointRequest
          .get("/api/users")
          .withIncludeHeaders(true)
          .withOnlyIfMissing(true)
          .withHeader("X-Custom", "value")
        val expr    = request.toFetchExpression.value
        assertTrue(
          expr.contains("headers:"),
          expr.contains("includeHeaders: true"),
          expr.contains("onlyIfMissing: true"),
        )
      },
    ),
    suite("endpoint request builder")(
      test("should create request from simple GET endpoint") {
        val endpoint = Endpoint(RoutePattern.GET / "api" / "users")
        val request  = endpoint.toDatastarRequest.build()
        assertTrue(
          request.method == Method.GET,
          request.url.contains("api"),
          request.url.contains("users"),
        )
      },
      test("should create request from POST endpoint") {
        val endpoint = Endpoint(RoutePattern.POST / "api" / "users")
        val request  = endpoint.toDatastarRequest.build()
        assertTrue(
          request.method == Method.POST,
          request.toActionExpression.value.startsWith("@post("),
        )
      },
      test("should create request from endpoint with path parameter") {
        val endpoint = Endpoint(RoutePattern.GET / "api" / "users" / PathCodec.int("userId"))
        val request  = endpoint.toDatastarRequest.build(42)
        assertTrue(
          request.method == Method.GET,
          request.url.contains("42"),
        )
      },
      test("should create request with signals from endpoint") {
        val endpoint = Endpoint(RoutePattern.GET / "api" / "users" / PathCodec.int("userId"))
        val request  = endpoint.toDatastarRequest.buildWithSignals()
        assertTrue(
          request.method == Method.GET,
          request.url.contains("$"),
        )
      },
      test("should support builder options on endpoint requests") {
        val endpoint = Endpoint(RoutePattern.GET / "api" / "users")
        val request  = endpoint.toDatastarRequest
          .withIncludeHeaders(true)
          .withHeader("X-Custom", "test")
          .build()
        assertTrue(
          request.includeHeaders == true,
          request.headers.contains("X-Custom"),
        )
      },
    ),
    suite("integration with data-on attributes")(
      test("should work with data-on-click") {
        val request = EndpointRequest.get("/api/users")
        val button  = div(dataOn.click := request)("Load Users")
        val html    = button.render
        assertTrue(
          html.contains("data-on-click"),
          html.contains("@get") && html.contains("/api/users"),
        )
      },
      test("should work with data-on-click and endpoint") {
        val endpoint = Endpoint(RoutePattern.GET / "api" / "users")
        val request  = endpoint.toDatastarRequest.build()
        val button   = div(dataOn.click := request)("Load Users")
        val html     = button.render
        assertTrue(
          html.contains("data-on-click"),
          html.contains("@get("),
        )
      },
      test("should work with data-on-submit") {
        val request = EndpointRequest.post("/api/users")
        val form    = div(dataOn.submit.prevent := request)
        val html    = form.render
        assertTrue(
          html.contains("data-on-submit__prevent"),
          html.contains("@post") && html.contains("/api/users"),
        )
      },
      test("should work with event modifiers") {
        val request = EndpointRequest.get("/api/users")
        val button  = div(
          dataOn.click.debounce(300.millis).prevent := request,
        )("Load")
        val html    = button.render
        assertTrue(
          html.contains("data-on-click__debounce.300ms__prevent"),
          html.contains("@get") && html.contains("/api/users"),
        )
      },
    ),
    suite("dataFetch helper")(
      test("should create GET request via dataFetch") {
        val request = dataFetch.get("/api/users")
        assertTrue(
          request.method == Method.GET,
          request.url == "/api/users",
        )
      },
      test("should create POST request via dataFetch") {
        val request = dataFetch.post("/api/users")
        assertTrue(
          request.method == Method.POST,
          request.url == "/api/users",
        )
      },
      test("should work with data-on-click") {
        val button = div(dataOn.click := dataFetch.get("/api/users"))("Load")
        val html   = button.render
        assertTrue(
          html.contains("data-on-click"),
          html.contains("@get") && html.contains("/api/users"),
        )
      },
    ),
    suite("complex endpoint scenarios")(
      test("should handle endpoint with multiple path segments") {
        val endpoint = Endpoint(RoutePattern.GET / "api" / "v1" / "users" / PathCodec.int("id"))
        val request  = endpoint.toDatastarRequest.build(123)
        assertTrue(
          request.url.contains("api"),
          request.url.contains("v1"),
          request.url.contains("users"),
          request.url.contains("123"),
        )
      },
      test("should handle endpoint with string path parameter") {
        val endpoint = Endpoint(RoutePattern.GET / "api" / "users" / PathCodec.string("username"))
        val request  = endpoint.toDatastarRequest.build("john")
        assertTrue(
          request.url.contains("john"),
        )
      },
      test("should handle PUT endpoint with path parameter") {
        val endpoint = Endpoint(RoutePattern.PUT / "api" / "users" / PathCodec.int("id"))
        val request  = endpoint.toDatastarRequest.build(42)
        assertTrue(
          request.method == Method.PUT,
          request.url.contains("42"),
          request.toActionExpression.value.contains("@put("),
        )
      },
      test("should handle DELETE endpoint with path parameter") {
        val endpoint = Endpoint(RoutePattern.DELETE / "api" / "users" / PathCodec.int("id"))
        val request  = endpoint.toDatastarRequest.build(99)
        assertTrue(
          request.method == Method.DELETE,
          request.url.contains("99"),
          request.toActionExpression.value.contains("@delete("),
        )
      },
    ),
    suite("rendering in HTML")(
      test("should render complete div with fetch") {
        val button = div(
          dataOn.click := dataFetch.get("/api/users"),
        )("Load Users")
        val html   = button.render
        assertTrue(
          html.contains("<div"),
          html.contains("data-on-click") && html.contains("@get") && html.contains("/api/users"),
          html.contains("Load Users"),
          html.contains("</div>"),
        )
      },
      test("should render div with POST request") {
        val form = div(
          dataOn.submit.prevent := dataFetch.post("/api/users"),
        )("Create")
        val html = form.render
        assertTrue(
          html.contains("<div"),
          html.contains("data-on-submit__prevent") && html.contains("@post") && html.contains("/api/users"),
          html.contains("</div>"),
        )
      },
      test("should render div with DELETE request") {
        val link = div(
          dataOn.click.prevent := dataFetch.delete("/api/users/1"),
        )("Delete")
        val html = link.render
        assertTrue(
          html.contains("<div"),
          html.contains("data-on-click__prevent") && html.contains("@delete") && html.contains("/api/users/1"),
          html.contains("Delete"),
          html.contains("</div>"),
        )
      },
    ),
  )
}
