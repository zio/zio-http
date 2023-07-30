package zio.http.endpoint.openapi

import zio.json._
import zio.json.ast.Json
import zio.json.yaml._
import zio.test._

object OpenAPICodecSpec extends ZIOSpecDefault {
  def spec = suite("OpenAPICodec")(
    roundTripYamlAndJsonSpec("petstore"),
    roundTripYamlAndJsonSpec("petstore-expanded"),
  )

  def roundTripJsonSpec(name: String) = test(s"Round-trips $name.json OpenAPI spec") {
    val jsonString    = scala.io.Source.fromResource(s"openapi/$name.json").mkString
    val openApiObject = jsonString.fromJson[OpenAPIObject].toOption.get
    assertTrue(openApiObject.toJsonPretty == jsonString)
  }

  def roundTripYamlSpec(name: String) = test(s"Round-trips $name.yaml OpenAPI spec") {
    val yamlString   = scala.io.Source.fromResource(s"openapi/$name.yaml").mkString
    val yamlJsonAst  = yamlString.fromYaml[Json].toOption.get
    val originalYaml = JsonOps(yamlJsonAst).toYaml().toOption.get

    val openApiObject = yamlString.fromYaml[OpenAPIObject].toOption.get
    val finalYaml     = openApiObject.toYaml().toOption.get
    assertTrue(originalYaml == finalYaml)
  }

  def roundTripYamlAndJsonSpec(name: String) = suite(s"Round-trips $name") {
    Seq(
      roundTripYamlSpec(name),
      roundTripJsonSpec(name),
    )
  }
}
