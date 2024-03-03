package zio.http.endpoint

import zio.test._

import zio.schema.{DeriveSchema, Schema}

import zio.http._
import zio.http.codec.HttpCodecError.CustomError
import zio.http.codec.{ContentCodec, QueryCodec}
import zio.http.template._

object BadRequestSpec extends ZIOSpecDefault {

  override def spec =
    suite("BadRequestSpec")(
      test("should return html rendered error message by default for html accept header") {
        val endpoint     = Endpoint(Method.GET / "test")
          .query(QueryCodec.queryInt("age"))
          .out[Unit]
        val route        = endpoint.implement(handler((_: Int) => ()))
        val app          = route.toHttpApp
        val request      =
          Request(method = Method.GET, url = url"/test?age=1&age=2").addHeader(Header.Accept(MediaType.text.`html`))
        val expectedBody =
          html(
            body(
              h1("Bad Request"),
              p("There was an error decoding the request"),
              p("Expected single value for query parameter age, but got 2 instead"),
            ),
          )
        for {
          response <- app.runZIO(request)
          body     <- response.body.asString
        } yield assertTrue(body == expectedBody.encode.toString)
      },
      test("should return json rendered error message by default for json accept header") {
        val endpoint     = Endpoint(Method.GET / "test")
          .query(QueryCodec.queryInt("age"))
          .out[Unit]
        val route        = endpoint.implement(handler((_: Int) => ()))
        val app          = route.toHttpApp
        val request      =
          Request(method = Method.GET, url = url"/test?age=1&age=2")
            .addHeader(Header.Accept(MediaType.application.json))
        val expectedBody = """{"message":"Expected single value for query parameter age, but got 2 instead"}"""
        for {
          response <- app.runZIO(request)
          body     <- response.body.asString
        } yield assertTrue(body == expectedBody)
      },
      test("should return json rendered error message by default as fallback for unsupported accept header") {
        val endpoint     = Endpoint(Method.GET / "test")
          .query(QueryCodec.queryInt("age"))
          .out[Unit]
        val route        = endpoint.implement(handler((_: Int) => ()))
        val app          = route.toHttpApp
        val request      =
          Request(method = Method.GET, url = url"/test?age=1&age=2")
            .addHeader(Header.Accept(MediaType.application.`atf`))
        val expectedBody = """{"message":"Expected single value for query parameter age, but got 2 instead"}"""
        for {
          response <- app.runZIO(request)
          body     <- response.body.asString
        } yield assertTrue(body == expectedBody)
      },
      test("should return empty body after calling Endpoint#codecErrorEmptyResponse") {
        val endpoint     = Endpoint(Method.GET / "test")
          .query(QueryCodec.queryInt("age"))
          .out[Unit]
          .codecErrorEmptyResponse
        val route        = endpoint.implement(handler((_: Int) => ()))
        val app          = route.toHttpApp
        val request      =
          Request(method = Method.GET, url = url"/test?age=1&age=2")
            .addHeader(Header.Accept(MediaType.application.`atf`))
        val expectedBody = ""
        for {
          response <- app.runZIO(request)
          body     <- response.body.asString
        } yield assertTrue(body == expectedBody)
      },
      test("should return custom error message") {
        val endpoint     = Endpoint(Method.GET / "test")
          .query(QueryCodec.queryInt("age"))
          .out[Unit]
          .codecErrorAs((err, _) => CustomError(err.getMessage()))(ContentCodec.content[CustomError])
        val route        = endpoint.implement(handler((_: Int) => ()))
        val app          = route.toHttpApp
        val request      =
          Request(method = Method.GET, url = url"/test?age=1&age=2")
            .addHeader(Header.Accept(MediaType.application.json))
        val expectedBody = """{"error":"Expected single value for query parameter age, but got 2 instead"}"""
        for {
          response <- app.runZIO(request)
          body     <- response.body.asString
        } yield assertTrue(body == expectedBody)
      },
    )

  final case class CustomError(error: String)
  implicit val schema: Schema[CustomError] = DeriveSchema.gen[CustomError]

}
