package zio.http.endpoint.openapi

import zio.Chunk
import zio.json._
import zio.json.ast.Json
import zio.json.yaml._
import zio.test._

import zio.schema._

import zio.http.Method.GET
import zio.http.codec.PathCodec
import zio.http.endpoint.Endpoint

object EndpointToOpenApiSpec extends ZIOSpecDefault {

  final case class Pet(name: String, tag: Option[String], id: Long)

  object Pet {
    implicit val schema: Schema[Pet] = DeriveSchema.gen[Pet]
  }

  def spec = suite("EndpointToOpenApiSpec")(
    test("Simple Example") {

      val endpoint = Endpoint(GET / "pets" / PathCodec.int("petId") / "visits")
        .out[Pet]

      val endpointInfo = EndpointInfoFromEndpoint.fromEndpoint(endpoint)
      val petIdParam   = ParameterInfo(
        name = "petId",
        in = ParameterLocation.Path,
        required = true,
        schema = ApiSchemaType.TInt,
        description = None,
      )

      val expectedResponses = Chunk(
        ResponseInfo(
          code = 200,
          description = "OK",
          content = Some(
            ContentInfo(
              mediaType = "application/json",
              schema = ApiSchemaType.Object(
                name = Chunk("Pet"),
                params = Map(
                  "name" -> ApiSchemaType.TString,
                  "tag"  -> ApiSchemaType.Optional(ApiSchemaType.TString),
                  "id"   -> ApiSchemaType.TLong,
                ),
              ),
            ),
          ),
        ),
      )
      val expected          = EndpointInfo(
        path = Chunk(PathItem.Static("pets"), PathItem.Param(petIdParam), PathItem.Static("visits")),
        method = GET,
        operationId = "getPetsVisits",
        parameters = Chunk(petIdParam),
        requestBody = None,
        responses = expectedResponses,
        summary = None,
      )
      assertTrue(endpointInfo == expected)
    },
  )

}
