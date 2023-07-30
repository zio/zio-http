package zio.http.endpoint.openapi

import zio.json._
import zio.json.yaml._

object Codegen {
  def fromJsonSchema(packageName: String, jsonString: String): Either[String, String] =
    jsonString.fromJson[OpenAPIObject].map { openApiObject =>
      EndpointGenerator.fromOpenAPIObject(openApiObject).generateCode(packageName)
    }

  def fromYamlSchema(packageName: String, yamlString: String): Either[String, String] =
    yamlString.fromYaml[OpenAPIObject].map { openApiObject =>
      EndpointGenerator.fromOpenAPIObject(openApiObject).generateCode(packageName)
    }
}
