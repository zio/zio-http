package zio.http.gen.openapi

import scala.annotation.tailrec

import zio.Chunk

import zio.http.Method
import zio.http.endpoint.openapi.OpenAPI.ReferenceOr
import zio.http.endpoint.openapi.{JsonSchema, OpenAPI}
import zio.http.gen.scala.Code.ScalaType
import zio.http.gen.scala.{Code, CodeGen}

object EndpointGen {

  private object Inline {
    val RequestBodyType  = "RequestBody"
    val ResponseBodyType = "ResponseBody"
    val Null             = "Unit"
  }

  private val DataImports =
    List(
      Code.Import("zio.schema._"),
    )

  private val RequestBodyRef = "#/components/requestBodies/(.*)".r
  private val ParameterRef   = "#/components/parameters/(.*)".r
  private val SchemaRef      = "#/components/schemas/(.*)".r
  private val ResponseRef    = "#/components/responses/(.*)".r

  def fromOpenAPI(openAPI: OpenAPI): Code.Files =
    EndpointGen().fromOpenAPI(openAPI)

}

final case class EndpointGen() {
  import EndpointGen._

  private var anonymousTypes: Map[String, Code.Object] = Map.empty[String, Code.Object]

  def fromOpenAPI(openAPI: OpenAPI): Code.Files =
    Code.Files {
      openAPI.paths.map { case (path, pathItem) =>
        val pathSegments = path.name.tail.replace('-', '_').split('/').toList
        val packageName  = pathSegments.init.mkString(".").replace("{", "").replace("}", "")
        val className    = pathSegments.last.replace("{", "").replace("}", "").capitalize
        val params       = List(
          pathItem.delete,
          pathItem.get,
          pathItem.head,
          pathItem.options,
          pathItem.post,
          pathItem.put,
          pathItem.patch,
          pathItem.trace,
        ).flatten
          .flatMap(_.parameters)
          .map {
            case OpenAPI.ReferenceOr.Or(param: OpenAPI.Parameter)       =>
              param
            case OpenAPI.ReferenceOr.Reference(ParameterRef(key), _, _) =>
              resolveParameterRef(openAPI, key)
            case other => throw new Exception(s"Unexpected parameter definition: $other")
          }
          .map(p => p.name -> p)
          .toMap
        val segments     = pathSegments.map {
          case s if s.startsWith("{") && s.endsWith("}") =>
            val name  = s.tail.init
            val param = params.getOrElse(
              name,
              throw new Exception(
                s"Path parameter $name not found in parameters: ${params.keys.mkString(", ")}",
              ),
            )
            parameterToPathCodec(openAPI, param)
          case s                                         => Code.PathSegmentCode(s, Code.CodecType.Literal)
        }

        Code.File(
          packageName.split('.').toList :+ s"$className.scala",
          pkgPath = packageName.split('.').toList,
          imports = List(Code.Import.FromBase("component._")),
          objects = List(
            Code.Object(
              className,
              schema = false,
              endpoints = List(
                pathItem.delete.map(op => fieldName(op, "delete") -> endpoint(segments, op, openAPI, Method.DELETE)),
                pathItem.get.map(op => fieldName(op, "get") -> endpoint(segments, op, openAPI, Method.GET)),
                pathItem.head.map(op => fieldName(op, "head") -> endpoint(segments, op, openAPI, Method.HEAD)),
                pathItem.options.map(op => fieldName(op, "options") -> endpoint(segments, op, openAPI, Method.OPTIONS)),
                pathItem.post.map(op => fieldName(op, "post") -> endpoint(segments, op, openAPI, Method.POST)),
                pathItem.put.map(op => fieldName(op, "put") -> endpoint(segments, op, openAPI, Method.PUT)),
                pathItem.patch.map(op => fieldName(op, "patch") -> endpoint(segments, op, openAPI, Method.PATCH)),
                pathItem.trace.map(op => fieldName(op, "trace") -> endpoint(segments, op, openAPI, Method.TRACE)),
              ).flatten.toMap,
              objects = anonymousTypes.values.toList,
              caseClasses = Nil,
              enums = Nil,
            ),
          ),
          caseClasses = Nil,
          enums = Nil,
        )
      }.toList ++
        openAPI.components.toList.flatMap { components =>
          components.schemas.flatMap { case (OpenAPI.Key(name), refOrSchema) =>
            var annotations: Chunk[JsonSchema.MetaData] = Chunk.empty
            val schema                                  = refOrSchema match {
              case ReferenceOr.Or(schema: JsonSchema) =>
                annotations = schema.annotations
                schema.withoutAnnotations
              case ReferenceOr.Reference(ref, _, _)   =>
                val schema = resolveSchemaRef(openAPI, ref)
                annotations = schema.annotations
                schema.withoutAnnotations
            }
            schemaToCode(schema, openAPI, name, annotations)
          }
        }
    }

  private def fieldName(op: OpenAPI.Operation, fallback: String) =
    Code.Field(op.operationId.getOrElse(fallback))

  private def endpoint(
    segments: List[Code.PathSegmentCode],
    op: OpenAPI.Operation,
    openAPI: OpenAPI,
    method: Method,
  ) = {

    val params      = op.parameters.map {
      case OpenAPI.ReferenceOr.Or(param: OpenAPI.Parameter)       => param
      case OpenAPI.ReferenceOr.Reference(ParameterRef(key), _, _) => resolveParameterRef(openAPI, key)
      case other => throw new Exception(s"Unexpected parameter definition: $other")
    }
    // TODO: Resolve query and header parameters from components
    val queryParams = params.collect {
      case p if p.in == "query" =>
        schemaToQueryParamCodec(
          p.schema.get.asInstanceOf[ReferenceOr.Or[JsonSchema]].value,
          openAPI,
          p.name,
        )
    }
    val headers     = params.collect { case p if p.in == "header" => Code.HeaderCode(p.name) }.toList
    val inType      =
      op.requestBody.flatMap {
        case OpenAPI.ReferenceOr.Reference(RequestBodyRef(key), _, _) => Some(key)
        case OpenAPI.ReferenceOr.Or(body: OpenAPI.RequestBody)        =>
          body.content
            .get("application/json")
            .map { mt =>
              mt.schema match {
                case ReferenceOr.Or(s)                                   =>
                  s.withoutAnnotations match {
                    case JsonSchema.Null                                     =>
                      Inline.Null
                    case JsonSchema.RefSchema(SchemaRef(ref))                =>
                      ref
                    case schema if schema.isPrimitive || schema.isCollection =>
                      CodeGen.render("")(schemaToField(schema, openAPI, "unused", Chunk.empty).get.fieldType)
                    case schema                                              =>
                      val code = schemaToCode(schema, openAPI, Inline.RequestBodyType, Chunk.empty)
                        .getOrElse(
                          throw new Exception(s"Could not generate code for request body $schema"),
                        )
                      anonymousTypes += method.toString ->
                        Code.Object(
                          method.toString,
                          schema = false,
                          endpoints = Map.empty,
                          objects = code.objects,
                          caseClasses = code.caseClasses,
                          enums = code.enums,
                        )
                      s"$method.${Inline.RequestBodyType}"
                  }
                case OpenAPI.ReferenceOr.Reference(SchemaRef(ref), _, _) => ref
                case other => throw new Exception(s"Unexpected request body schema: $other")
              }
            }
        case other => throw new Exception(s"Unexpected request body definition: $other")
      }.getOrElse("Unit")

    val outCodes: Iterable[Code.OutCode] =
      // TODO: ignore default for now. Not sure how to handle it
      op.responses.collect {
        case (OpenAPI.StatusOrDefault.StatusValue(status), OpenAPI.ReferenceOr.Reference(ResponseRef(key), _, _)) =>
          val response = resolveResponseRef(openAPI, key)
          Code.OutCode(
            outType = response.content
              .get("application/json")
              .map { mt =>
                mt.schema match {
                  case ReferenceOr.Or(s)                                   =>
                    s.withoutAnnotations match {
                      case JsonSchema.Null                                     => Inline.Null
                      case JsonSchema.RefSchema(SchemaRef(ref))                => ref
                      case schema if schema.isPrimitive || schema.isCollection =>
                        CodeGen.render("")(schemaToField(schema, openAPI, "unused", Chunk.empty).get.fieldType)
                      case schema                                              =>
                        val code = schemaToCode(schema, openAPI, Inline.ResponseBodyType, Chunk.empty)
                          .getOrElse(
                            throw new Exception(s"Could not generate code for request body $schema"),
                          )
                        val obj  = Code.Object(
                          method.toString,
                          schema = false,
                          endpoints = Map.empty,
                          objects = code.objects,
                          caseClasses = code.caseClasses,
                          enums = code.enums,
                        )
                        anonymousTypes += method.toString -> anonymousTypes.get(method.toString).fold(obj) { obj =>
                          obj.copy(
                            objects = obj.objects ++ code.objects,
                            caseClasses = obj.caseClasses ++ code.caseClasses,
                            enums = obj.enums ++ code.enums,
                          )
                        }
                        s"$method.${Inline.ResponseBodyType}"
                    }
                  case OpenAPI.ReferenceOr.Reference(SchemaRef(ref), _, _) => ref
                  case other => throw new Exception(s"Unexpected response body schema: $other")
                }
              }
              .getOrElse("Unit"),
            status = status,
            mediaType = Some("application/json"),
            doc = None,
          )
        case (OpenAPI.StatusOrDefault.StatusValue(status), OpenAPI.ReferenceOr.Or(response: OpenAPI.Response))    =>
          Code.OutCode(
            outType = response.content
              .get("application/json")
              .map { mt =>
                mt.schema match {
                  case ReferenceOr.Or(s)                                   =>
                    s.withoutAnnotations match {
                      case JsonSchema.Null                                     => Inline.Null
                      case JsonSchema.RefSchema(SchemaRef(ref))                => ref
                      case schema if schema.isPrimitive || schema.isCollection =>
                        CodeGen.render("")(schemaToField(schema, openAPI, "unused", Chunk.empty).get.fieldType)
                      case schema                                              =>
                        val code = schemaToCode(schema, openAPI, Inline.ResponseBodyType, Chunk.empty)
                          .getOrElse(
                            throw new Exception(s"Could not generate code for request body $schema"),
                          )
                        val obj  = Code.Object(
                          method.toString,
                          schema = false,
                          endpoints = Map.empty,
                          objects = code.objects,
                          caseClasses = code.caseClasses,
                          enums = code.enums,
                        )
                        anonymousTypes += method.toString -> anonymousTypes.get(method.toString).fold(obj) { obj =>
                          obj.copy(
                            objects = obj.objects ++ code.objects,
                            caseClasses = obj.caseClasses ++ code.caseClasses,
                            enums = obj.enums ++ code.enums,
                          )
                        }
                        s"$method.${Inline.ResponseBodyType}"
                    }
                  case OpenAPI.ReferenceOr.Reference(SchemaRef(ref), _, _) => ref
                  case other => throw new Exception(s"Unexpected response body schema: $other")
                }
              }
              .getOrElse("Unit"),
            status = status,
            mediaType = Some("application/json"),
            doc = None,
          )
      }

    Code.EndpointCode(
      method = method,
      pathPatternCode = Code.PathPatternCode(segments),
      queryParamsCode = queryParams,
      headersCode = Code.HeadersCode(headers),
      inCode = Code.InCode(inType, None, None),
      outCodes = outCodes.filterNot(_.status.isError).toList,
      errorsCode = outCodes.filter(_.status.isError).toList,
    )

  }

  private def parameterToPathCodec(openAPI: OpenAPI, param: OpenAPI.Parameter): Code.PathSegmentCode = {
    param.schema match {
      case Some(OpenAPI.ReferenceOr.Or(schema: JsonSchema)) =>
        schemaToPathCodec(schema, openAPI, param.name)
      case Some(OpenAPI.ReferenceOr.Reference(ref, _, _))   =>
        schemaToPathCodec(resolveSchemaRef(openAPI, ref), openAPI, param.name)
      case None                                             =>
        // Not sure if open api allows path parameters without schema.
        // But string seems a good default
        schemaToPathCodec(JsonSchema.String(), openAPI, param.name)
    }
  }

  @tailrec
  private def resolveParameterRef(openAPI: OpenAPI, key: String): OpenAPI.Parameter =
    openAPI.components match {
      case Some(components) =>
        val param = components.parameters.getOrElse(
          OpenAPI.Key.fromString(key).get,
          throw new Exception(s"Only references to internal parameters are supported. Not found: $key"),
        )
        param match {
          case ReferenceOr.Reference(ref, _, _) => resolveParameterRef(openAPI, ref)
          case ReferenceOr.Or(param)            => param
        }
      case None             =>
        throw new Exception(s"Found reference to parameter $key, but no components section found.")
    }

  @tailrec
  private def resolveSchemaRef(openAPI: OpenAPI, key: String): JsonSchema =
    openAPI.components match {
      case Some(components) =>
        val schema = components.schemas.getOrElse(
          OpenAPI.Key.fromString(key).get,
          throw new Exception(s"Only references to internal schemas are supported. Not found: $key"),
        )
        schema match {
          case ReferenceOr.Reference(ref, _, _) => resolveSchemaRef(openAPI, ref)
          case ReferenceOr.Or(schema)           => schema
        }
      case None             =>
        throw new Exception(s"Found reference to schema $key, but no components section found.")
    }

  @tailrec
  private def resolveRequestBodyRef(openAPI: OpenAPI, key: String): OpenAPI.RequestBody =
    openAPI.components match {
      case Some(components) =>
        val schema = components.requestBodies.getOrElse(
          OpenAPI.Key.fromString(key).get,
          throw new Exception(s"Only references to internal schemas are supported. Not found: $key"),
        )
        schema match {
          case ReferenceOr.Reference(ref, _, _) => resolveRequestBodyRef(openAPI, ref)
          case ReferenceOr.Or(schema)           => schema
        }
      case None             =>
        throw new Exception(s"Found reference to schema $key, but no components section found.")
    }

  @tailrec
  private def resolveResponseRef(openAPI: OpenAPI, key: String): OpenAPI.Response =
    openAPI.components match {
      case Some(components) =>
        val schema = components.responses.getOrElse(
          OpenAPI.Key.fromString(key).get,
          throw new Exception(s"Only references to internal schemas are supported. Not found: $key"),
        )
        schema match {
          case ReferenceOr.Reference(ref, _, _) => resolveResponseRef(openAPI, ref)
          case ReferenceOr.Or(schema)           => schema
        }
      case None             =>
        throw new Exception(s"Found reference to schema $key, but no components section found.")
    }

  @tailrec
  private def schemaToPathCodec(schema: JsonSchema, openAPI: OpenAPI, name: String): Code.PathSegmentCode = {
    schema match {
      case JsonSchema.AnnotatedSchema(s, _) => schemaToPathCodec(s, openAPI, name)
      case JsonSchema.RefSchema(ref)        => schemaToPathCodec(resolveSchemaRef(openAPI, ref), openAPI, name)
      case JsonSchema.Integer(JsonSchema.IntegerFormat.Int32)       =>
        Code.PathSegmentCode(name = name, segmentType = Code.CodecType.Int)
      case JsonSchema.Integer(JsonSchema.IntegerFormat.Int64)       =>
        Code.PathSegmentCode(name = name, segmentType = Code.CodecType.Long)
      case JsonSchema.Integer(JsonSchema.IntegerFormat.Timestamp)   =>
        Code.PathSegmentCode(name = name, segmentType = Code.CodecType.Long)
      case JsonSchema.String(Some(JsonSchema.StringFormat.UUID), _) =>
        Code.PathSegmentCode(name = name, segmentType = Code.CodecType.UUID)
      case JsonSchema.String(_, _)                                  =>
        Code.PathSegmentCode(name = name, segmentType = Code.CodecType.String)
      case JsonSchema.Boolean                                       =>
        Code.PathSegmentCode(name = name, segmentType = Code.CodecType.Boolean)
      case JsonSchema.OneOfSchema(_) => throw new Exception("Alternative path variables are not supported")
      case JsonSchema.AllOfSchema(_) => throw new Exception("Path variables must have exactly one schema")
      case JsonSchema.AnyOfSchema(_) => throw new Exception("Path variables must have exactly one schema")
      case JsonSchema.Number(_)      => throw new Exception("Floating point path variables are currently not supported")
      case JsonSchema.ArrayType(_)   => throw new Exception("Array path variables are not supported")
      case JsonSchema.Object(_, _, _) => throw new Exception("Object path variables are not supported")
      case JsonSchema.Enum(_)         => throw new Exception("Enum path variables are not supported")
      case JsonSchema.Null            => throw new Exception("Null path variables are not supported")
      case JsonSchema.AnyJson         => throw new Exception("AnyJson path variables are not supported")
    }
  }

  @tailrec
  private def schemaToQueryParamCodec(
    schema: JsonSchema,
    openAPI: OpenAPI,
    name: String,
  ): Code.QueryParamCode = {
    schema match {
      case JsonSchema.AnnotatedSchema(s, _)                         =>
        schemaToQueryParamCodec(s, openAPI, name)
      case JsonSchema.RefSchema(ref)                                =>
        schemaToQueryParamCodec(resolveSchemaRef(openAPI, ref), openAPI, name)
      case JsonSchema.Integer(JsonSchema.IntegerFormat.Int32)       =>
        Code.QueryParamCode(name = name, queryType = Code.CodecType.Int)
      case JsonSchema.Integer(JsonSchema.IntegerFormat.Int64)       =>
        Code.QueryParamCode(name = name, queryType = Code.CodecType.Long)
      case JsonSchema.Integer(JsonSchema.IntegerFormat.Timestamp)   =>
        Code.QueryParamCode(name = name, queryType = Code.CodecType.Long)
      case JsonSchema.String(Some(JsonSchema.StringFormat.UUID), _) =>
        Code.QueryParamCode(name = name, queryType = Code.CodecType.UUID)
      case JsonSchema.String(_, _)                                  =>
        Code.QueryParamCode(name = name, queryType = Code.CodecType.String)
      case JsonSchema.Boolean                                       =>
        Code.QueryParamCode(name = name, queryType = Code.CodecType.Boolean)
      case JsonSchema.OneOfSchema(_) => throw new Exception("Alternative query parameters are not supported")
      case JsonSchema.AllOfSchema(_) => throw new Exception("Query parameters must have exactly one schema")
      case JsonSchema.AnyOfSchema(_) => throw new Exception("Query parameters must have exactly one schema")
      case JsonSchema.Number(_)    => throw new Exception("Floating point query parameters are currently not supported")
      case JsonSchema.ArrayType(_) => throw new Exception("Array query parameters are not supported")
      case JsonSchema.Object(_, _, _) => throw new Exception("Object query parameters are not supported")
      case JsonSchema.Enum(_)         => throw new Exception("Enum query parameters are not supported")
      case JsonSchema.Null            => throw new Exception("Null query parameters are not supported")
      case JsonSchema.AnyJson         => throw new Exception("AnyJson query parameters are not supported")
    }
  }

  def schemaToCode(
    schema: JsonSchema,
    openAPI: OpenAPI,
    name: String,
    annotations: Chunk[JsonSchema.MetaData],
  ): Option[Code.File] = {
    schema match {
      case JsonSchema.AnnotatedSchema(s, _)          =>
        schemaToCode(s.withoutAnnotations, openAPI, name, schema.annotations)
      case JsonSchema.RefSchema(RequestBodyRef(ref)) =>
        val (schemaName, schema) = resolveRequestBodyRef(openAPI, ref).content
          .get("application/json")
          .map { mt =>
            mt.schema match {
              case ReferenceOr.Or(s: JsonSchema)                       => name -> s
              case OpenAPI.ReferenceOr.Reference(SchemaRef(ref), _, _) =>
                ref.capitalize -> resolveSchemaRef(openAPI, ref)
              case other                                               =>
                throw new Exception(s"Unexpected reference schema: $other")
            }
          }
          .getOrElse(throw new Exception(s"Could not find content type application/json for request body $ref"))
        schemaToCode(schema, openAPI, schemaName, annotations)

      case JsonSchema.RefSchema(SchemaRef(ref)) =>
        val schema = resolveSchemaRef(openAPI, ref)
        schemaToCode(schema, openAPI, ref.capitalize, annotations)

      case JsonSchema.RefSchema(ResponseRef(ref)) =>
        val (schemaName, schema) = resolveResponseRef(openAPI, ref).content
          .get("application/json")
          .map { mt =>
            mt.schema match {
              case ReferenceOr.Or(s: JsonSchema)                       => name -> s
              case OpenAPI.ReferenceOr.Reference(SchemaRef(ref), _, _) =>
                ref.capitalize -> resolveSchemaRef(openAPI, ref)
              case other                                               =>
                throw new Exception(s"Unexpected reference schema: $other")
            }
          }
          .getOrElse(throw new Exception(s"Could not find content type application/json for response $ref"))
        schemaToCode(schema, openAPI, schemaName, annotations)

      case JsonSchema.RefSchema(ref) => throw new Exception(s"Unexpected reference schema: $ref")
      case JsonSchema.Integer(_)     => None
      case JsonSchema.String(_, _)   => None // this could maybe be im proved to generate a string type with validation
      case JsonSchema.Boolean        => None
      case JsonSchema.OneOfSchema(schemas) if schemas.exists(_.isPrimitive) =>
        throw new Exception("OneOf schemas with primitive types are not supported")
      case JsonSchema.OneOfSchema(schemas)                                  =>
        val discriminatorInfo                    =
          annotations.collectFirst { case JsonSchema.MetaData.Discriminator(discriminator) => discriminator }
        val discriminator: Option[String]        = discriminatorInfo.map(_.propertyName)
        val caseNameMapping: Map[String, String] = discriminatorInfo.map(_.mapping).getOrElse(Map.empty).map {
          case (k, v) => v -> k
        }
        var caseNames: List[String]              = Nil
        val caseClasses                          = schemas
          .map(_.withoutAnnotations)
          .flatMap {
            case schema @ JsonSchema.Object(properties, _, _) if singleFieldTypeTag(schema) =>
              val (name, schema) = properties.head
              caseNames :+= name
              schemaToCode(schema, openAPI, name, annotations)
                .getOrElse(
                  throw new Exception(s"Could not generate code for field $name of object $name"),
                )
                .caseClasses
            case schema @ JsonSchema.RefSchema(ref @ SchemaRef(name))                       =>
              caseNameMapping.get(ref).foreach(caseNames :+= _)
              schemaToCode(schema, openAPI, name, annotations)
                .getOrElse(
                  throw new Exception(s"Could not generate code for subtype $name of oneOf schema $schema"),
                )
                .caseClasses
            case schema @ JsonSchema.Object(_, _, _)                                        =>
              schemaToCode(schema, openAPI, name, annotations)
                .getOrElse(
                  throw new Exception(s"Could not generate code for subtype $name of oneOf schema $schema"),
                )
                .caseClasses
            case other                                                                      =>
              throw new Exception(s"Unexpected subtype $other for oneOf schema $schema")
          }
          .toList
        val noDiscriminator                      = caseNames.isEmpty
        Some(
          Code.File(
            List("component", name.capitalize + ".scala"),
            pkgPath = List("component"),
            imports = dataImports(caseClasses.flatMap(_.fields)) ++
              (if (noDiscriminator || caseNames.nonEmpty) List(Code.Import("zio.schema.annotation._")) else Nil),
            objects = Nil,
            caseClasses = Nil,
            enums = List(
              Code.Enum(
                name = name,
                cases = caseClasses,
                caseNames = caseNames,
                discriminator = discriminator,
                noDiscriminator = noDiscriminator,
                schema = true,
              ),
            ),
          ),
        )
      case JsonSchema.AllOfSchema(schemas)                                  =>
        val genericFieldIndex = Iterator.from(0)
        val fields            = schemas.map(_.withoutAnnotations).flatMap {
          case schema @ JsonSchema.Object(_, _, _)            =>
            schemaToCode(schema, openAPI, name, annotations)
              .getOrElse(
                throw new Exception(s"Could not generate code for field $name of object $name"),
              )
              .caseClasses
              .headOption
              .toList
              .flatMap(_.fields)
          case schema @ JsonSchema.RefSchema(SchemaRef(name)) =>
            schemaToCode(schema, openAPI, name, annotations)
              .getOrElse(
                throw new Exception(s"Could not generate code for subtype $name of allOf schema $schema"),
              )
              .caseClasses
              .headOption
              .toList
              .flatMap(_.fields)
          case schema if schema.isPrimitive                   =>
            val name = s"field${genericFieldIndex.next()}"
            Chunk(schemaToField(schema, openAPI, name, annotations)).flatten
          case other                                          =>
            throw new Exception(s"Unexpected subtype $other for allOf schema $schema")
        }
        Some(
          Code.File(
            List("component", name.capitalize + ".scala"),
            pkgPath = List("component"),
            imports = dataImports(fields),
            objects = Nil,
            caseClasses = List(
              Code.CaseClass(
                name,
                fields.toList,
                companionObject = Some(Code.Object.schemaCompanion(name)),
              ),
            ),
            enums = Nil,
          ),
        )
      case JsonSchema.AnyOfSchema(schemas) if schemas.exists(_.isPrimitive) =>
        throw new Exception("AnyOf schemas with primitive types are not supported")
      case JsonSchema.AnyOfSchema(schemas)                                  =>
        val discriminatorInfo                    =
          annotations.collectFirst { case JsonSchema.MetaData.Discriminator(discriminator) => discriminator }
        val discriminator: Option[String]        = discriminatorInfo.map(_.propertyName)
        val caseNameMapping: Map[String, String] = discriminatorInfo.map(_.mapping).getOrElse(Map.empty).map {
          case (k, v) => v -> k
        }
        var caseNames: List[String]              = Nil
        val caseClasses                          = schemas
          .map(_.withoutAnnotations)
          .flatMap {
            case schema @ JsonSchema.Object(properties, _, _) if singleFieldTypeTag(schema) =>
              val (name, schema) = properties.head
              caseNames :+= name
              schemaToCode(schema, openAPI, name, annotations)
                .getOrElse(
                  throw new Exception(s"Could not generate code for field $name of object $name"),
                )
                .caseClasses
            case schema @ JsonSchema.RefSchema(ref @ SchemaRef(name))                       =>
              caseNameMapping.get(ref).foreach(caseNames :+= _)
              schemaToCode(schema, openAPI, name, annotations)
                .getOrElse(
                  throw new Exception(s"Could not generate code for subtype $name of anyOf schema $schema"),
                )
                .caseClasses
            case schema @ JsonSchema.Object(_, _, _)                                        =>
              schemaToCode(schema, openAPI, name, annotations)
                .getOrElse(
                  throw new Exception(s"Could not generate code for subtype $name of anyOf schema $schema"),
                )
                .caseClasses
            case other                                                                      =>
              throw new Exception(s"Unexpected subtype $other for anyOf schema $schema")
          }
          .toList
        Some(
          Code.File(
            List("component", name.capitalize + ".scala"),
            pkgPath = List("component"),
            imports = dataImports(caseClasses.flatMap(_.fields)),
            objects = Nil,
            caseClasses = Nil,
            enums = List(
              Code.Enum(
                name = name,
                cases = caseClasses,
                caseNames = caseNames,
                discriminator = discriminator,
                noDiscriminator = caseNames.isEmpty,
                schema = true,
              ),
            ),
          ),
        )
      case JsonSchema.Number(_)                                             => None
      case JsonSchema.ArrayType(None)                                       => None
      case JsonSchema.ArrayType(Some(schema))                               =>
        schemaToCode(schema, openAPI, name, annotations)
      // TODO use additionalProperties
      case JsonSchema.Object(properties, additionalProperties, required)    =>
        val fields            = properties.map { case (name, schema) =>
          val field = schemaToField(schema, openAPI, name, annotations)
            .getOrElse(
              throw new Exception(s"Could not generate code for field $name of object $name"),
            )
            .asInstanceOf[Code.Field]
          if (required.contains(name)) field else field.copy(fieldType = field.fieldType.opt)
        }.toList
        val nested            =
          properties.map { case (name, schema) => name -> schema.withoutAnnotations }.collect {
            case (name, schema)
                if !schema.isInstanceOf[JsonSchema.RefSchema]
                  && !schema.isPrimitive
                  && !schema.isCollection =>
              schemaToCode(schema, openAPI, name.capitalize, Chunk.empty)
                .getOrElse(
                  throw new Exception(s"Could not generate code for field $name of object $name"),
                )
          }
        val nestedObjects     = nested.flatMap(_.objects)
        val nestedCaseClasses = nested.flatMap(_.caseClasses)
        Some(
          Code.File(
            List("component", name.capitalize + ".scala"),
            pkgPath = List("component"),
            imports = dataImports(fields),
            objects = nestedObjects.toList,
            caseClasses = List(
              Code.CaseClass(
                name,
                fields,
                companionObject = Some(Code.Object.schemaCompanion(name)),
              ),
            ) ++ nestedCaseClasses,
            enums = Nil,
          ),
        )

      case JsonSchema.Enum(enums) =>
        Some(
          Code.File(
            List("component", name.capitalize + ".scala"),
            pkgPath = List("component"),
            imports = DataImports,
            objects = Nil,
            caseClasses = Nil,
            enums = List(
              Code.Enum(
                name,
                enums.flatMap {
                  case JsonSchema.EnumValue.Str(e) => Some(Code.CaseClass(e))
                  case JsonSchema.EnumValue.Null   =>
                    None // can be ignored here, but field of this type should be optional
                  case other => throw new Exception(s"OpenAPI Enums of value $other, are currently unsupported")
                }.toList,
              ),
            ),
          ),
        )
      case JsonSchema.Null        => throw new Exception("Null query parameters are not supported")
      case JsonSchema.AnyJson     => throw new Exception("AnyJson query parameters are not supported")
    }
  }

  private def singleFieldTypeTag(schema: JsonSchema.Object) =
    schema.properties.size == 1 &&
      schema.properties.head._2.isInstanceOf[JsonSchema.RefSchema] &&
      schema.additionalProperties == Left(false) &&
      schema.required == Chunk(schema.properties.head._1)

  def schemaToField(
    schema: JsonSchema,
    openAPI: OpenAPI,
    name: String,
    annotations: Chunk[JsonSchema.MetaData],
  ): Option[Code.Field] = {
    schema match {
      case JsonSchema.AnnotatedSchema(s, _)                         =>
        schemaToField(s.withoutAnnotations, openAPI, name, schema.annotations)
      case JsonSchema.RefSchema(SchemaRef(ref))                     =>
        Some(Code.Field(name, Code.TypeRef(ref.capitalize)))
      case JsonSchema.RefSchema(ref)                                =>
        throw new Exception(s" Not found: $ref. Only references to internal schemas are supported.")
      case JsonSchema.Integer(JsonSchema.IntegerFormat.Int32)       =>
        Some(Code.Field(name, Code.Primitive.ScalaInt))
      case JsonSchema.Integer(JsonSchema.IntegerFormat.Int64)       =>
        Some(Code.Field(name, Code.Primitive.ScalaLong))
      case JsonSchema.Integer(JsonSchema.IntegerFormat.Timestamp)   =>
        Some(Code.Field(name, Code.Primitive.ScalaLong))
      case JsonSchema.String(Some(JsonSchema.StringFormat.UUID), _) =>
        Some(Code.Field(name, Code.Primitive.ScalaUUID))
      case JsonSchema.String(_, _)                                  =>
        Some(Code.Field(name, Code.Primitive.ScalaString))
      case JsonSchema.Boolean                                       =>
        Some(Code.Field(name, Code.Primitive.ScalaBoolean))
      case JsonSchema.OneOfSchema(schemas)                          =>
        val tpe =
          schemas
            .map(_.withoutAnnotations)
            .flatMap(schemaToField(_, openAPI, "unused", annotations))
            .map(_.fieldType)
            .reduceLeft(ScalaType.Or(_, _))
        Some(Code.Field(name, tpe))
      case JsonSchema.AllOfSchema(_)                                =>
        throw new Exception("Inline allOf schemas are not supported for fields")
      case JsonSchema.AnyOfSchema(schemas)                          =>
        val tpe =
          schemas
            .map(_.withoutAnnotations)
            .flatMap(schemaToField(_, openAPI, "unused", annotations))
            .map(_.fieldType)
            .reduceLeft(ScalaType.Or(_, _))
        Some(Code.Field(name, tpe))
      case JsonSchema.Number(JsonSchema.NumberFormat.Double)        =>
        Some(Code.Field(name, Code.Primitive.ScalaDouble))
      case JsonSchema.Number(JsonSchema.NumberFormat.Float)         =>
        Some(Code.Field(name, Code.Primitive.ScalaFloat))
      case JsonSchema.ArrayType(items)                              =>
        val tpe = items
          .flatMap(schemaToField(_, openAPI, name, annotations))
          .map(_.fieldType.seq)
          .orElse(
            Some(Code.Primitive.ScalaString.seq),
          )
        tpe.map(Code.Field(name, _))
      case JsonSchema.Object(_, _, _)                               =>
        Some(Code.Field(name, Code.TypeRef(name.capitalize)))
      case JsonSchema.Enum(_)                                       =>
        Some(Code.Field(name, Code.TypeRef(name.capitalize)))
      case JsonSchema.Null                                          =>
        Some(Code.Field(name, ScalaType.Unit))
      case JsonSchema.AnyJson                                       =>
        Some(Code.Field(name, ScalaType.JsonAST))
    }
  }

  private def dataImports(fields: Iterable[Code.Field]) = {
    if (fields.exists(_.fieldType.isInstanceOf[Code.Collection.Seq])) List(Code.Import("zio._"))
    else Nil
  } ++ DataImports
}
