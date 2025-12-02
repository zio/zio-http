package zio.http.datastar

import zio.test._

import zio.schema.{DeriveSchema, Schema}

import zio.http._
import zio.http.codec._
import zio.http.endpoint._

object InvocationExtensionsSpec extends ZIOSpecDefault {

  case class User(name: String, age: Int)
  object User { implicit val schema: Schema[User] = DeriveSchema.gen[User] }

  override def spec = suite("InvocationExtensionsSpec")(
    test("GET request with path param") {
      val endpoint   = Endpoint(Method.GET / "users" / int("id"))
      val invocation = endpoint(123)
      val expression = invocation.datastarRequest
      assertTrue(expression == "$$get('/users/123')")
    },
    test("POST request with body") {
      val endpoint   = Endpoint(Method.POST / "users").in[User]
      val invocation = endpoint(User("Alice", 30))
      val expression = invocation.datastarRequest

      val expectedBody = """{"name":"Alice","age":30}"""
      val expected     = s"$$$$post('/users', {body: '$expectedBody', contentType: 'application/json'})"

      assertTrue(expression == expected)
    },
    test("POST request with query param") {
      val endpoint   = Endpoint(Method.POST / "search").query(HttpCodec.query[String]("q"))
      val invocation = endpoint("foo")
      val expression = invocation.datastarRequest
      assertTrue(expression == "$$post('/search?q=foo')")
    },
  )
}
