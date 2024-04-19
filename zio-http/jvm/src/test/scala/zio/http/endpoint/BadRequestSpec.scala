package zio.http.endpoint

import zio.test._

import zio.schema.codec.JsonCodec
import zio.schema.{DeriveSchema, Schema}

import zio.http._
import zio.http.codec._
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
              h1("Codec Error"),
              p("There was an error en-/decoding the request/response"),
              p("SchemaTransformationFailure", idAttr                                      := "name"),
              p("Expected single value for query parameter age, but got 2 instead", idAttr := "message"),
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
        val expectedBody =
          """{"name":"SchemaTransformationFailure","message":"Expected single value for query parameter age, but got 2 instead"}"""
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
        val expectedBody =
          """{"name":"SchemaTransformationFailure","message":"Expected single value for query parameter age, but got 2 instead"}"""
        for {
          response <- app.runZIO(request)
          body     <- response.body.asString
        } yield assertTrue(body == expectedBody)
      },
      test("should return empty body after calling Endpoint#emptyErrorResponse") {
        val endpoint     = Endpoint(Method.GET / "test")
          .query(QueryCodec.queryInt("age"))
          .out[Unit]
          .emptyErrorResponse
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
        val route        = endpoint.implement(handler((_: Int) => ()))
        val app          = route.toHttpApp
        val request      =
          Request(method = Method.GET, url = url"/test?age=1&age=2")
            .addHeader(Header.Accept(MediaType.application.json))
        val expectedBody =
          """{"name":"SchemaTransformationFailure","message":"Expected single value for query parameter age, but got 2 instead"}"""
        for {
          response <- app.runZIO(request)
          body     <- response.body.asString
        } yield assertTrue(body == expectedBody)
      },
      test("should use custom error codec over default error codec") {
        val endpoint     = Endpoint(Method.GET / "test")
          .query(QueryCodec.queryInt("age"))
          .out[Unit]
          .outCodecError(default)
        val route        = endpoint.implement(handler((_: Int) => ()))
        val app          = route.toHttpApp
        val request      =
          Request(method = Method.GET, url = url"/test?age=1&age=2")
            .addHeader(Header.Accept(MediaType.application.json))
        val expectedBody =
          """{"name2":"SchemaTransformationFailure","message2":"Expected single value for query parameter age, but got 2 instead"}"""
        for {
          response <- app.runZIO(request)
          body     <- response.body.asString
        } yield assertTrue(body == expectedBody)
      },
    )

  private val defaultCodecErrorSchema: Schema[HttpCodecError] =
    Schema[DefaultCodecError2].transformOrFail[HttpCodecError](
      codecError => Right(HttpCodecError.CustomError(codecError.name2, codecError.message2)),
      {
        case HttpCodecError.CustomError(name, message) => Right(DefaultCodecError2(name, message))
        case e: HttpCodecError                         => Right(DefaultCodecError2(e.productPrefix, e.getMessage()))
      },
    )

  private val testHttpContentCodec: HttpContentCodec[HttpCodecError] =
    HttpContentCodec.from(
      MediaType.application.json -> BinaryCodecWithSchema(
        JsonCodec.schemaBasedBinaryCodec(defaultCodecErrorSchema),
        defaultCodecErrorSchema,
      ),
    )

  val default: HttpCodec[HttpCodecType.ResponseType, HttpCodecError] =
    ContentCodec.content(testHttpContentCodec) ++ StatusCodec.BadRequest

  final case class DefaultCodecError2(name2: String, message2: String)

  private object DefaultCodecError2 {
    implicit val schema: Schema[DefaultCodecError2] = DeriveSchema.gen[DefaultCodecError2]
  }

}
