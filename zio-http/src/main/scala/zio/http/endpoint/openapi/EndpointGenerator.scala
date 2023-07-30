package zio.http.endpoint.openapi

import zio.Chunk

import zio.http.endpoint.openapi.StringUtils.pascalCase

private[openapi] final case class EndpointGenerator(
  title: String,
  description: Option[String],
  schemas: Map[String, ApiSchemaType],
  endpoints: Chunk[EndpointInfo],
) {

  def generateCode(packageName: String): String = {
    val objectName         = pascalCase(title)
    val caseClasses        = renderCaseClassDefinitions
    val generatedEndpoints = endpoints.map(_.toEndpointDefinition)
    s"""
package $packageName

import zio.Chunk
import zio.http.Status
import zio.http.codec.{PathCodec, QueryCodec}
import zio.http.endpoint.Endpoint
import zio.http.Method._

object $objectName {

${indentString(caseClasses.mkString("\n\n"), 2)}

${indentString(generatedEndpoints.mkString("\n\n"), 2)}

}
        """.trim
  }

  def indentString(string: String, indent: Int): String =
    string.split("\n").map(" " * indent + _).mkString("\n")

  def renderCaseClassDefinitions: List[String] =
    schemas.map {
      case (name, schema: ApiSchemaType.Object) =>
        renderCaseClassDefinition(name, schema)
      case (name, schema)                       =>
        s"type $name = ${schema.renderType}"
    }.toList

  private def renderCaseClassDefinition(rawName: String, schema: ApiSchemaType.Object): String = {
    val name   = pascalCase(rawName)
    val params = schema.params.map { case (name, schemaType) =>
      s"$name: ${schemaType.renderType}"
    }
    s"""
final case class $name(${params.mkString(", ")})

object $name {
  implicit val schema: zio.schema.Schema[$name] = zio.schema.DeriveSchema.gen[$name]
}
""".trim
  }

}

private[openapi] object EndpointGenerator {

  def fromOpenAPIObject(openAPIObject: OpenAPIObject): EndpointGenerator = {
    val schemas           = parseSchemaTypes(openAPIObject)
    val normalizedSchemas = schemas.map { case (name, schema) => name -> schema.normalize(schemas) }
    val endpoints         = Chunk.fromIterable {
      openAPIObject.paths.flatten { case (pathString, pathItemObject) =>
        EndpointInfo.fromPathItemObject(pathString, pathItemObject)
      }
    }
    EndpointGenerator(
      openAPIObject.info.title,
      openAPIObject.info.description,
      normalizedSchemas,
      endpoints,
    )
  }

  private def parseSchemaTypes(openAPIObject: OpenAPIObject): Map[String, ApiSchemaType] = {
    val schemas = openAPIObject.components.flatMap(_.schemas).getOrElse(Map.empty)
    schemas.map { case (name, value) =>
      name -> parseSchemaType(value, Some(name))
    }
  }

  def parseSchemaType(
    refOrSchemaObject: ReferenceOr[SchemaObject],
    name: Option[String] = None,
  ): ApiSchemaType =
    refOrSchemaObject match {
      case ReferenceOr.Reference(ref) => ApiSchemaType.Ref(ref.$ref)

      case ReferenceOr.Value(schemaObject) =>
        schemaObject.`type`.map {
          case SchemaObjectType.Integer =>
            schemaObject.format match {
              case Some(SchemaObjectFormat.Int32) => ApiSchemaType.TInt
              case Some(SchemaObjectFormat.Int64) => ApiSchemaType.TLong
              case _                              => ApiSchemaType.TInt
            }

          case SchemaObjectType.Number =>
            schemaObject.format match {
              case Some(SchemaObjectFormat.Float)  => ApiSchemaType.TFloat
              case Some(SchemaObjectFormat.Double) => ApiSchemaType.TDouble
              case _                               => ApiSchemaType.TDouble
            }

          case SchemaObjectType.String =>
            ApiSchemaType.TString

          case SchemaObjectType.Boolean =>
            ApiSchemaType.TBoolean

          case SchemaObjectType.Object =>
            val required   = schemaObject.required.map(_.toSet).getOrElse(Set.empty)
            val properties = schemaObject.properties.getOrElse(Map.empty).map { case (name, value) =>
              val isOptional = !required.contains(name)
              name -> parseSchemaType(value).wrapInOptional(isOptional)
            }
            ApiSchemaType.Object(properties)

          case SchemaObjectType.Null =>
            ApiSchemaType.TNull

          case SchemaObjectType.Array =>
            val items = parseSchemaType(schemaObject.items.get)
            ApiSchemaType.Array(items)
        }.orElse {
          schemaObject.allOf.map { allOf =>
            val types = allOf.map(parseSchemaType(_))
            ApiSchemaType.AllOf(types)
          }
        }
          .getOrElse(throw new Exception(s"Could not parse schema $name : $schemaObject"))
    }

}
