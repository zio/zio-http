package zio.http.endpoint.http

import zio.test._

import zio.schema._

import zio.http._
import zio.http.codec._
import zio.http.endpoint._

object HttpGenSpec extends ZIOSpecDefault {
  case class User(name: String, age: Int)

  object User {
    implicit val schema: Schema[User] = DeriveSchema.gen[User]
  }

  val spec = suite("HttpGenSpec")(
    test("Method and Path") {
      val endpoint     = Endpoint(Method.GET / "api" / "foo")
      val httpEndpoint = HttpGen.fromEndpoint(endpoint)
      val rendered     = httpEndpoint.render
      assertTrue(rendered == "GET /api/foo")
    },
    test("POST for Method.ANY") {
      val endpoint     = Endpoint(Method.ANY / "api" / "foo")
      val httpEndpoint = HttpGen.fromEndpoint(endpoint)
      val rendered     = httpEndpoint.render
      assertTrue(rendered == "POST /api/foo")
    },
    test("Path with path parameters") {
      val endpoint     = Endpoint(Method.GET / "api" / "foo" / int("userId"))
      val httpEndpoint = HttpGen.fromEndpoint(endpoint)
      val rendered     = httpEndpoint.render
      val expected     =
        """
          |@userId=<no value>
          |
          |GET /api/foo/{{userId}}""".stripMargin
      assertTrue(rendered == expected)
    },
    test("Path with query parameters") {
      val endpoint     = Endpoint(Method.GET / "api" / "foo").query[Int](HttpCodec.query[Int]("userId"))
      val httpEndpoint = HttpGen.fromEndpoint(endpoint)
      val rendered     = httpEndpoint.render
      val expected     =
        """
          |@userId=<no value>
          |
          |GET /api/foo?userId={{userId}}""".stripMargin
      assertTrue(rendered == expected)
    },
    test("Path with path and query parameters") {
      val endpoint     = Endpoint(Method.GET / "api" / "foo" / int("pageId")).query[Int](HttpCodec.query[Int]("userId"))
      val httpEndpoint = HttpGen.fromEndpoint(endpoint)
      val rendered     = httpEndpoint.render
      val expected     =
        """
          |@pageId=<no value>
          |@userId=<no value>
          |
          |GET /api/foo/{{pageId}}?userId={{userId}}""".stripMargin
      assertTrue(rendered == expected)
    },
    test("Path with path and query parameter with the same name") {
      val endpoint     = Endpoint(Method.GET / "api" / "foo" / int("userId")).query[Int](HttpCodec.query[Int]("userId"))
      val httpEndpoint = HttpGen.fromEndpoint(endpoint)
      val rendered     = httpEndpoint.render
      val expected     =
        """
          |@userId=<no value>
          |
          |GET /api/foo/{{userId}}?userId={{userId}}""".stripMargin
      assertTrue(rendered == expected)
    },
    test("Header") {
      val endpoint     = Endpoint(Method.GET / "api" / "foo").header(HeaderCodec.authorization)
      val httpEndpoint = HttpGen.fromEndpoint(endpoint)
      val rendered     = httpEndpoint.render
      val expected     =
        """
          |@Authorization=<no value>
          |
          |GET /api/foo
          |Authorization: {{Authorization}}""".stripMargin
      assertTrue(rendered == expected)
    },
    test("Header with example") {
      val endpoint     = Endpoint(Method.GET / "api" / "foo")
        .header(HeaderCodec.authorization.examples("default" -> Header.Authorization.Basic("admin", "admin")))
      val httpEndpoint = HttpGen.fromEndpoint(endpoint)
      val rendered     = httpEndpoint.render
      val expected     =
        """
          |@Authorization=Basic YWRtaW46YWRtaW4=
          |
          |GET /api/foo
          |Authorization: {{Authorization}}""".stripMargin
      assertTrue(rendered == expected)
    },
    test("Other header with example") {
      val endpoint     = Endpoint(Method.GET / "api" / "foo")
        .header(HeaderCodec.contentType.examples("default" -> Header.ContentType(MediaType.application.json)))
      val httpEndpoint = HttpGen.fromEndpoint(endpoint)
      val rendered     = httpEndpoint.render
      val expected     =
        """
          |@Content-type=application/json
          |
          |GET /api/foo
          |Content-type: {{Content-type}}""".stripMargin
      assertTrue(rendered == expected)
    },
    test("Header with path parameters") {
      val endpoint     = Endpoint(Method.GET / "api" / "foo" / int("userId")).header(HeaderCodec.authorization)
      val httpEndpoint = HttpGen.fromEndpoint(endpoint)
      val rendered     = httpEndpoint.render
      val expected     =
        """
          |@userId=<no value>
          |@Authorization=<no value>
          |
          |GET /api/foo/{{userId}}
          |Authorization: {{Authorization}}""".stripMargin
      assertTrue(rendered == expected)
    },
    test("Endpoint with doc") {
      val endpoint     = Endpoint(Method.GET / "api" / "foo" / int("userId")) ?? Doc.p("Get user by id")
      val httpEndpoint = HttpGen.fromEndpoint(endpoint)
      val rendered     = httpEndpoint.render
      val expected     =
        """
          |# Get user by id
          |
          |@userId=<no value>
          |
          |GET /api/foo/{{userId}}""".stripMargin
      assertTrue(rendered == expected)
    },
    test("Endpoint with json payload") {
      val endpoint     = Endpoint(Method.POST / "api" / "foo" / int("userId")).in[User]
      val httpEndpoint = HttpGen.fromEndpoint(endpoint)
      val rendered     = httpEndpoint.render
      val expected     =
        """
          |@userId=<no value>
          |# type: String
          |@name=<no value>
          |# type: Int
          |@age=<no value>
          |
          |POST /api/foo/{{userId}}
          |Content-type: application/json
          |
          |{
          |"name": {{name}},
          |"age": {{age}}
          |}""".stripMargin
      assertTrue(rendered == expected)
    },
    test("Multiple endpoints") {
      val endpoint1     = Endpoint(Method.GET / "api" / "foo" / int("userId")) ?? Doc.p("Get user by id")
      val endpoint2     = Endpoint(Method.POST / "api" / "foo" / int("userId")).in[User]
      val httpEndpoint1 = HttpGen.fromEndpoints(endpoint1, endpoint2)
      val rendered      = httpEndpoint1.render
      val expected1     =
        """
          |# Get user by id
          |
          |@userId=<no value>
          |
          |GET /api/foo/{{userId}}""".stripMargin
      val expected2     =
        """
          |@userId=<no value>
          |# type: String
          |@name=<no value>
          |# type: Int
          |@age=<no value>
          |
          |POST /api/foo/{{userId}}
          |Content-type: application/json
          |
          |{
          |"name": {{name}},
          |"age": {{age}}
          |}""".stripMargin
      assertTrue(rendered == expected1 + "\n\n" + expected2)
    },
  )
}
