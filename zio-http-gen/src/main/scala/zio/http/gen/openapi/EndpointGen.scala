package zio.http.gen.openapi

import scala.annotation.tailrec
import scala.reflect.ClassTag

import zio.Chunk

import zio.http.Method
import zio.http.endpoint.openapi.OpenAPI.ReferenceOr
import zio.http.endpoint.openapi.{JsonSchema, OpenAPI}
import zio.http.gen.scala.Code._
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

  def fromOpenAPI(openAPI: OpenAPI, config: Config = Config.default): Code.Files =
    EndpointGen(config).fromOpenAPI(openAPI)

  implicit class MapCompatOps[K, V](m: Map[K, V]) {
    // scala 2.12 collection does not support updatedWith natively, so we're adding this as an extension.
    def updatedWith(k: K)(f: Option[V] => Option[V]): Map[K, V] =
      f(m.get(k)) match {
        case Some(v) => m.updated(k, v)
        case None    => m - k
      }
  }
}

final case class EndpointGen(config: Config) {
  import EndpointGen._

  private var anonymousTypes: Map[String, Code.Object] = Map.empty[String, Code.Object]

  object OneOfAllReferencesAsSimpleNames {
    // if all oneOf schemas are references,
    // we should render the oneOf as a sealed trait,
    // and make all objects case classes extending that sealed trait.
    def unapply(schema: JsonSchema.OneOfSchema): Option[List[String]] =
      schema.oneOf.foldRight(Option(List.empty[String])) {
        case (JsonSchema.RefSchema(SchemaRef(simpleName)), simpleNames) =>
          simpleNames.map(simpleName :: _)
        case _                                                          => None
      }
  }

  object AllOfSchemaExistsReferencesAsSimpleNames {
    // if all subtypes of a shared trait has same set of allOf schemas,
    // then we can render a sealed trait whose abstract methods are the fields shared by all subtypes.
    def unapply(schema: JsonSchema.AllOfSchema): Some[List[String]] = Some(
      schema.allOf.foldRight(List.empty[String]) {
        case (JsonSchema.RefSchema(SchemaRef(simpleName)), simpleNames) =>
          simpleName :: simpleNames
        case (_, simpleNames)                                           => simpleNames
      },
    )
  }

  private def extractComponents(openAPI: OpenAPI): List[Code.File] = {

    // maps and inverse bookkeeping for later.
    // We'll collect all components relations,
    // and use it to amend generated code file.
    var traitToSubtypes            = Map.empty[String, Set[String]]
    var subtypeToTraits            = Map.empty[String, Set[String]]
    var caseClassToSharedFields    = Map.empty[String, Set[String]]
    var nameToSchemaAndAnnotations = Map.empty[String, (JsonSchema, Chunk[JsonSchema.MetaData])]
    var aliasedPrimitives          = Map.empty[String, ScalaType]

    openAPI.components.toList.foreach { components =>
      components.schemas.foreach { case (OpenAPI.Key(name), refOrSchema) =>
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

        schema match {
          case OneOfAllReferencesAsSimpleNames(refNames)          =>
            traitToSubtypes = traitToSubtypes.updatedWith(name) {
              case Some(subtypes) => Some(subtypes ++ refNames)
              case None           => Some(refNames.toSet)
            }
            refNames.foreach { refName =>
              subtypeToTraits = subtypeToTraits.updatedWith(refName) {
                case Some(traits) => Some(traits + name)
                case None         => Some(Set(name))
              }
            }
          case AllOfSchemaExistsReferencesAsSimpleNames(refNames) =>
            if (config.commonFieldsOnSuperType && refNames.nonEmpty) {
              caseClassToSharedFields = caseClassToSharedFields.updatedWith(name) {
                case Some(fields) => Some(fields ++ refNames)
                case None         => Some(refNames.toSet)
              }
            }
          case _                                                  =>
            // primitives that are aliased should be registered for a special Newtype treatment,
            // or if opted out, then replaced with their un-aliased form.
            if (schema.isPrimitive) {
              if (config.generateSafeTypeAliases)
                aliasedPrimitives = aliasedPrimitives.updated(name, Code.TypeRef(name + ".Type"))
              else
                schemaToField(schema, openAPI, name, Chunk.empty).foreach {
                  case Code.Field(_, fieldType: Code.Primitive, _) =>
                    aliasedPrimitives = aliasedPrimitives.updated(name, fieldType)
                  case _                                           => // do nothing, we only modify aliased primitives
                }
            }
        }
        nameToSchemaAndAnnotations = nameToSchemaAndAnnotations.updated(name, schema -> annotations)
      }
    }

    // generate code per component by name
    // the generated code will emit file per component,
    // even when sum type (sealed trait) is used,
    // which in this case, the sealed trait companion contains incomplete case classes (these do not extend anything),
    // but we have the complete case classes in separate files.
    // so the map will be used to replace inner incomplete enum case classes with complete stand alone files.
    val componentNameToCodeFile: Map[String, Code.File] = nameToSchemaAndAnnotations.view.map {
      case (name, (schema, annotations)) =>
        val abstractMembersOfTrait: List[JsonSchema.Object] =
          traitToSubtypes
            .get(name)
            .fold(List.empty[JsonSchema.Object]) { subtypes =>
              if (subtypes.isEmpty) Nil
              else
                subtypes.view
                  .map(caseClassToSharedFields.getOrElse(_, Set.empty))
                  .reduce(_ intersect _)
                  .map(nameToSchemaAndAnnotations)
                  .collect { case (o: JsonSchema.Object, _) => o }
                  .toList
            }

        val mixins = subtypeToTraits.get(name).fold(List.empty[String])(_.toList)

        name -> schemaToCode(schema, openAPI, name, annotations, mixins, abstractMembersOfTrait)
    }.collect { case (name, Some(file)) => name -> file }.toMap

    // for every case class that extends a sealed trait,
    // we don't need a separate code file, as it will be included in the sealed trait companion.
    // this var stores the bookkeeping of such case classes, and is later used to omit the redundant code files.
    var replacedCasesToOmitAsTopComponents = Set.empty[String]
    val allComponents: List[Code.File]     = componentNameToCodeFile.view.map { case (name, codeFile) =>
      traitToSubtypes
        .get(name)
        .fold(codeFile) { subtypes =>
          codeFile.copy(enums = codeFile.enums.map { anEnum =>
            val (shouldBeReplaced, shouldBePreserved) = anEnum.cases.partition(cc => subtypes.contains(cc.name))
            if (shouldBeReplaced.isEmpty) anEnum
            else
              anEnum.copy(cases = shouldBePreserved ++ shouldBeReplaced.flatMap { cc =>
                replacedCasesToOmitAsTopComponents = replacedCasesToOmitAsTopComponents + cc.name
                componentNameToCodeFile(cc.name).caseClasses
              })
          })
        }
    }.toList

    val noDuplicateFiles = allComponents.filterNot { cf =>
      cf.enums.isEmpty && cf.objects.isEmpty && cf.caseClasses.nonEmpty && cf.caseClasses.forall(cc =>
        replacedCasesToOmitAsTopComponents(cc.name),
      )
    }

    // After filtering out duplicate files, we can make sure  that in all the files left, all fields has proper types.
    // The `mapType` function is used to alter any relevant part of each code file
    noDuplicateFiles.map { cf =>
      cf.copy(
        objects = cf.objects.map(mapType(_.name, subtypeToTraits, aliasedPrimitives)),
        caseClasses = cf.caseClasses.map(mapType(_.name, subtypeToTraits, aliasedPrimitives)),
        enums = cf.enums.map(enm =>
          mapType[Code.Enum](_.name, subtypeToTraits, aliasedPrimitives)(enm).copy(
            abstractMembers = enm.abstractMembers.map(mapField(subtypeToTraits, aliasedPrimitives, enm.name)),
          ),
        ),
      )
    }
  }

  /**
   * The types may not be valid in case we reference a concrete subtype of a
   * sealed trait, as the subtype is defined as an inner class encapsulated
   * inside the trait's companion. Therefore, we can alter the type to include
   * the enclosing trait/object's name. The following function will be used to
   * alter the type of all fields needed. The `mapCaseClasses` helper takes a
   * function that alters a case class, and lifts it such that we can apply it
   * to any structure, and it'll take care to recurse when needed.
   *
   * Another issue we fix here, is when we have aliased primitives. In that case
   * we need to append `.Type` to the aliased primitive type.
   *
   * @param getEncapsulatingName
   *   used to get the name of the code structure we operate on
   *   (Object/CaseClass/Enum)
   * @param subtypeToTraits
   *   mappings of subtypes to their mixins - if theres only one, we assume
   *   subtype is nested.
   * @param codeStructureToAlter
   *   the structure to modify
   * @return
   *   the modified structure
   */
  def mapType[T <: Code.ScalaType](
    getEncapsulatingName: T => String,
    subtypeToTraits: Map[String, Set[String]],
    aliasedPrimitives: Map[String, Code.ScalaType],
  )(
    codeStructureToAlter: T,
  ): T =
    mapCaseClasses { cc =>
      cc.copy(fields = cc.fields.foldRight(List.empty[Code.Field]) { case (field, tail) =>
        mapField(subtypeToTraits, aliasedPrimitives, getEncapsulatingName(codeStructureToAlter))(field) :: tail
      })
    }(codeStructureToAlter)

  def mapField(
    subtypeToTraits: Map[String, Set[String]],
    aliasedPrimitives: Map[String, ScalaType],
    encapsulatingName: => String,
  ): Code.Field => Code.Field = (f: Code.Field) =>
    f.copy(fieldType = mapTypeRef(f.fieldType) { case originalType @ Code.TypeRef(tName) =>
      // We use the subtypeToTraits map to check if the type is a concrete subtype of a sealed trait.
      // As of the time of writing this code, there should be only a single trait.
      // In case future code generalizes to allow multiple mixins, this code should be updated.
      //
      // If no mixins are found, we try to check maybe we deal with an aliased primitive,
      // which in this case we should use the provided alias (with ".Type" appended).
      //
      // If no alias, and no mixins, we return the original type.
      subtypeToTraits
        .get(tName)
        .fold(aliasedPrimitives.getOrElse(tName, originalType)) { set =>
          // If the type parameter has exactly 1 super type trait,
          // and that trait's name is different from our enclosing object's name,
          // then we should alter the type to include the object's name.
          if (set.size != 1 || set.head == encapsulatingName) originalType
          else Code.TypeRef(set.head + "." + tName)
        }
    })

  /**
   * Given the type parameter of a field, we may want to alter it, e.g. by
   * prepending the enclosing trait/object's name. This function will
   * recursively alter the type of a field. Recursion is needed for types that
   * contain a type parameter. e.g. transforming: {{{Chunk[Option[Zebra]]}}} to
   * {{{Chunk[Option[Animal.Zebra]]}}}
   *
   * @param sType
   *   the original type we want to alter
   * @param f
   *   a function that may alter the type, None means no altering is needed.
   * @return
   *   The altered type, or gives back the input if no modification was needed.
   */
  def mapTypeRef(sType: Code.ScalaType)(f: Code.TypeRef => Code.ScalaType): Code.ScalaType =
    sType match {
      case tref: Code.TypeRef              => f(tref)
      case Collection.Seq(inner, nonEmpty) => Collection.Seq(mapTypeRef(inner)(f), nonEmpty)
      case Collection.Set(inner, nonEmpty) => Collection.Set(mapTypeRef(inner)(f), nonEmpty)
      case Collection.Map(inner, keysType) => Collection.Map(mapTypeRef(inner)(f), keysType)
      case Collection.Opt(inner)           => Collection.Opt(mapTypeRef(inner)(f))
      case _                               => sType
    }

  /**
   * Given a function to alter a case class, this function will apply it to any
   * structure recursively.
   *
   * @param f
   *   function to transform a [[zio.http.gen.scala.Code.CaseClass]]
   * @param code
   *   the structure to apply transformation of case classes on
   * @return
   *   the transformed structure
   */
  def mapCaseClasses[T <: Code.ScalaType](f: Code.CaseClass => Code.CaseClass)(code: T): T =
    (code match {
      case obj: Code.Object   =>
        obj.copy(
          caseClasses = obj.caseClasses.map(mapCaseClasses(f)),
          objects = obj.objects.map(mapCaseClasses(f)),
        )
      case cc: Code.CaseClass => f(cc)
      case sum: Code.Enum     => sum.copy(cases = sum.cases.map(mapCaseClasses(f)))
      case _                  => code
    }).asInstanceOf[T]

  def fromOpenAPI(openAPI: OpenAPI): Code.Files =
    Code.Files {
      val componentsCode = extractComponents(openAPI)
      val files          = openAPI.paths.map { case (path, pathItem) =>
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

        val (imports, endpoints) =
          List(
            pathItem.delete.map(op => fieldName(op, "delete") -> endpoint(segments, op, openAPI, Method.DELETE)),
            pathItem.get.map(op => fieldName(op, "get") -> endpoint(segments, op, openAPI, Method.GET)),
            pathItem.head.map(op => fieldName(op, "head") -> endpoint(segments, op, openAPI, Method.HEAD)),
            pathItem.options.map(op => fieldName(op, "options") -> endpoint(segments, op, openAPI, Method.OPTIONS)),
            pathItem.post.map(op => fieldName(op, "post") -> endpoint(segments, op, openAPI, Method.POST)),
            pathItem.put.map(op => fieldName(op, "put") -> endpoint(segments, op, openAPI, Method.PUT)),
            pathItem.patch.map(op => fieldName(op, "patch") -> endpoint(segments, op, openAPI, Method.PATCH)),
            pathItem.trace.map(op => fieldName(op, "trace") -> endpoint(segments, op, openAPI, Method.TRACE)),
          ).flatten.map { case (name, (imports, endpoint)) =>
            (imports, name -> endpoint)
          }.unzip
        Code.File(
          packageName.split('.').toList :+ s"$className.scala",
          pkgPath = packageName.split('.').toList,
          imports = (Code.Import.FromBase("component._") :: imports.flatten).distinct,
          objects = List(
            Code.Object(
              name = className,
              extensions = Nil,
              schema = None,
              endpoints = endpoints.toMap,
              objects = anonymousTypes.values.toList,
              caseClasses = Nil,
              enums = Nil,
            ),
          ),
          caseClasses = Nil,
          enums = Nil,
        )
      }
      files.toList ++ componentsCode
    }

  private def fieldName(op: OpenAPI.Operation, fallback: String) =
    Code.Field(op.operationId.getOrElse(fallback), config.fieldNamesNormalization)

  private def endpoint(
    segments: List[Code.PathSegmentCode],
    op: OpenAPI.Operation,
    openAPI: OpenAPI,
    method: Method,
  ): (List[Code.Import], Code.EndpointCode) = {

    val params              = op.parameters.map {
      case OpenAPI.ReferenceOr.Or(param: OpenAPI.Parameter)       => param
      case OpenAPI.ReferenceOr.Reference(ParameterRef(key), _, _) => resolveParameterRef(openAPI, key)
      case other => throw new Exception(s"Unexpected parameter definition: $other")
    }
    // TODO: Resolve query and header parameters from components
    val queryParams         = params.collect {
      case p if p.in == "query" =>
        p.schema.get match {
          case ReferenceOr.Or(value)            =>
            schemaToQueryParamCodec(
              value,
              openAPI,
              p.name,
            )
          // references are only possible in case of aliased primitives
          case ReferenceOr.Reference(ref, _, _) =>
            val baref  = ref.replaceFirst("^#/components/schemas/", "")
            val schema = resolveSchemaRef(openAPI, baref)
            val qCodec = schemaToQueryParamCodec(schema, openAPI, p.name)
            if (!config.generateSafeTypeAliases) qCodec
            else qCodec.copy(queryType = CodecType.Aliased(qCodec.queryType, baref))
        }
    }
    val headers             = params.collect { case p if p.in == "header" => Code.HeaderCode(p.name) }.toList
    val (inImports, inType) =
      op.requestBody.flatMap {
        case OpenAPI.ReferenceOr.Reference(RequestBodyRef(key), _, _) => Some(Nil -> key)
        case OpenAPI.ReferenceOr.Or(body: OpenAPI.RequestBody)        =>
          body.content
            .get("application/json")
            .map { mt =>
              mt.schema match {
                case ReferenceOr.Or(s)                                   =>
                  s.withoutAnnotations match {
                    case JsonSchema.Null                                     =>
                      Nil -> Inline.Null
                    case JsonSchema.RefSchema(SchemaRef(ref))                =>
                      Nil -> ref
                    case schema if schema.isPrimitive || schema.isCollection =>
                      CodeGen.render("")(schemaToField(schema, openAPI, "unused", Chunk.empty).get.fieldType)
                    case schema                                              =>
                      val code = schemaToCode(schema, openAPI, Inline.RequestBodyType, Chunk.empty)
                        .getOrElse(
                          throw new Exception(s"Could not generate code for request body $schema"),
                        )
                      anonymousTypes += method.toString ->
                        Code.Object(
                          name = method.toString,
                          extensions = Nil,
                          schema = None,
                          endpoints = Map.empty,
                          objects = code.objects,
                          caseClasses = code.caseClasses,
                          enums = code.enums,
                        )
                      Nil                               -> s"$method.${Inline.RequestBodyType}"
                  }
                case OpenAPI.ReferenceOr.Reference(SchemaRef(ref), _, _) => Nil -> ref
                case other => throw new Exception(s"Unexpected request body schema: $other")
              }
            }
        case other => throw new Exception(s"Unexpected request body definition: $other")
      }.getOrElse(Nil -> "Unit")

    val (outImports: Iterable[List[Code.Import]], outCodes: Iterable[Code.OutCode]) =
      // TODO: ignore default for now. Not sure how to handle it
      op.responses.collect {
        case (OpenAPI.StatusOrDefault.StatusValue(status), OpenAPI.ReferenceOr.Reference(ResponseRef(key), _, _)) =>
          val response        = resolveResponseRef(openAPI, key)
          val (imports, code) =
            response.content
              .get("application/json")
              .map { mt =>
                mt.schema match {
                  case ReferenceOr.Or(s)                                   =>
                    s.withoutAnnotations match {
                      case JsonSchema.Null                                     => Nil -> Inline.Null
                      case JsonSchema.RefSchema(SchemaRef(ref))                => Nil -> ref
                      case schema if schema.isPrimitive || schema.isCollection =>
                        CodeGen.render("")(schemaToField(schema, openAPI, "unused", Chunk.empty).get.fieldType)
                      case schema                                              =>
                        val code = schemaToCode(schema, openAPI, Inline.ResponseBodyType, Chunk.empty)
                          .getOrElse(
                            throw new Exception(s"Could not generate code for request body $schema"),
                          )
                        val obj  = Code.Object(
                          name = method.toString,
                          extensions = Nil,
                          schema = None,
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
                        Nil                               -> s"$method.${Inline.ResponseBodyType}"
                    }
                  case OpenAPI.ReferenceOr.Reference(SchemaRef(ref), _, _) => Nil -> ref
                  case other => throw new Exception(s"Unexpected response body schema: $other")
                }
              }
              .getOrElse(Nil -> "Unit")
          imports ->
            Code.OutCode(
              outType = code,
              status = status,
              mediaType = Some("application/json"),
              doc = None,
              streaming = false,
            )
        case (OpenAPI.StatusOrDefault.StatusValue(status), OpenAPI.ReferenceOr.Or(response: OpenAPI.Response))    =>
          val (imports, code) =
            response.content
              .get("application/json")
              .map { mt =>
                mt.schema match {
                  case ReferenceOr.Or(s)                                   =>
                    s.withoutAnnotations match {
                      case JsonSchema.Null                                     => Nil -> Inline.Null
                      case JsonSchema.RefSchema(SchemaRef(ref))                => Nil -> ref
                      case schema if schema.isPrimitive || schema.isCollection =>
                        CodeGen.render("")(schemaToField(schema, openAPI, "unused", Chunk.empty).get.fieldType)
                      case schema                                              =>
                        val code = schemaToCode(schema, openAPI, Inline.ResponseBodyType, Chunk.empty)
                          .getOrElse(
                            throw new Exception(s"Could not generate code for request body $schema"),
                          )
                        val obj  = Code.Object(
                          name = method.toString,
                          extensions = Nil,
                          schema = None,
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
                        Nil                               -> s"$method.${Inline.ResponseBodyType}"
                    }
                  case OpenAPI.ReferenceOr.Reference(SchemaRef(ref), _, _) => Nil -> ref
                  case other => throw new Exception(s"Unexpected response body schema: $other")
                }
              }
              .getOrElse(Nil -> "Unit")
          imports -> Code.OutCode(
            outType = code,
            status = status,
            mediaType = Some("application/json"),
            doc = None,
            streaming = false,
          )
      }.unzip

    val imports = inImports ++ outImports.flatten
    val code    = Code.EndpointCode(
      method = method,
      pathPatternCode = Code.PathPatternCode(segments),
      queryParamsCode = queryParams,
      headersCode = Code.HeadersCode(headers),
      inCode = Code.InCode(inType),
      outCodes = outCodes.filterNot(_.status.isError).toList,
      errorsCode = outCodes.filter(_.status.isError).toList,
    )
    imports -> code
  }

  private def parameterToPathCodec(openAPI: OpenAPI, param: OpenAPI.Parameter): Code.PathSegmentCode = {
    param.schema match {
      case Some(OpenAPI.ReferenceOr.Or(schema: JsonSchema)) =>
        schemaToPathCodec(schema, openAPI, param.name)
      case Some(OpenAPI.ReferenceOr.Reference(ref, _, _))   =>
        if (ref.startsWith("#/components/schemas/")) {
          val baref  = ref.replaceFirst("^#/components/schemas/", "")
          val schema = resolveSchemaRef(openAPI, baref)
          val pCodec = schemaToPathCodec(schema, openAPI, param.name)
          if (!config.generateSafeTypeAliases) pCodec
          else pCodec.copy(segmentType = CodecType.Aliased(pCodec.segmentType, baref))
        } else {
          val schema = resolveSchemaRef(openAPI, ref)
          val pCodec = schemaToPathCodec(schema, openAPI, param.name)
          pCodec
        }
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

  /**
   * Used for aliased types to resolve into the wrapped primitive underlying
   * type.
   * @return
   *   if primitive, a list of potential imports (e.g: for UUID, we need to
   *   import java.util.UUID), and the primitive type name.
   */
  private def schemaToType(openAPI: OpenAPI, name: String, schema: JsonSchema): Option[(List[Code.Import], String)] =
    if (schema.isPrimitive) {
      val field = schemaToField(schema, openAPI, name, Chunk.empty).get // .get is safe, always defined for primitives
      Some(CodeGen.render("")(field.fieldType))
    } else None

  @tailrec
  private def schemaToPathCodec(schema: JsonSchema, openAPI: OpenAPI, name: String): Code.PathSegmentCode = {
    schema match {
      case JsonSchema.AnnotatedSchema(s, _) => schemaToPathCodec(s, openAPI, name)
      case JsonSchema.RefSchema(ref)        => schemaToPathCodec(resolveSchemaRef(openAPI, ref), openAPI, name)
      case JsonSchema.Integer(JsonSchema.IntegerFormat.Int32, _, _, _, _, _)     =>
        Code.PathSegmentCode(name = name, segmentType = Code.CodecType.Int)
      case JsonSchema.Integer(JsonSchema.IntegerFormat.Int64, _, _, _, _, _)     =>
        Code.PathSegmentCode(name = name, segmentType = Code.CodecType.Long)
      case JsonSchema.Integer(JsonSchema.IntegerFormat.Timestamp, _, _, _, _, _) =>
        Code.PathSegmentCode(name = name, segmentType = Code.CodecType.Long)
      case JsonSchema.String(Some(JsonSchema.StringFormat.UUID), _, _, _)        =>
        Code.PathSegmentCode(name = name, segmentType = Code.CodecType.UUID)
      case JsonSchema.String(_, _, _, _)                                         =>
        Code.PathSegmentCode(name = name, segmentType = Code.CodecType.String)
      case JsonSchema.Boolean                                                    =>
        Code.PathSegmentCode(name = name, segmentType = Code.CodecType.Boolean)
      case JsonSchema.OneOfSchema(_)           => throw new Exception("Alternative path variables are not supported")
      case JsonSchema.AllOfSchema(_)           => throw new Exception("Path variables must have exactly one schema")
      case JsonSchema.AnyOfSchema(_)           => throw new Exception("Path variables must have exactly one schema")
      case JsonSchema.Number(_, _, _, _, _, _) =>
        throw new Exception("Floating point path variables are currently not supported")
      case JsonSchema.ArrayType(_, _, _)       => throw new Exception("Array path variables are not supported")
      case JsonSchema.Object(_, _, _)          => throw new Exception("Object path variables are not supported")
      case JsonSchema.Enum(_)                  => throw new Exception("Enum path variables are not supported")
      case JsonSchema.Null                     => throw new Exception("Null path variables are not supported")
      case JsonSchema.AnyJson                  => throw new Exception("AnyJson path variables are not supported")
    }
  }

  @tailrec
  private def schemaToQueryParamCodec(
    schema: JsonSchema,
    openAPI: OpenAPI,
    name: String,
  ): Code.QueryParamCode = {
    schema match {
      case JsonSchema.AnnotatedSchema(s, _)                                      =>
        schemaToQueryParamCodec(s, openAPI, name)
      case JsonSchema.RefSchema(ref)                                             =>
        schemaToQueryParamCodec(resolveSchemaRef(openAPI, ref), openAPI, name)
      case JsonSchema.Integer(JsonSchema.IntegerFormat.Int32, _, _, _, _, _)     =>
        Code.QueryParamCode(name = name, queryType = Code.CodecType.Int)
      case JsonSchema.Integer(JsonSchema.IntegerFormat.Int64, _, _, _, _, _)     =>
        Code.QueryParamCode(name = name, queryType = Code.CodecType.Long)
      case JsonSchema.Integer(JsonSchema.IntegerFormat.Timestamp, _, _, _, _, _) =>
        Code.QueryParamCode(name = name, queryType = Code.CodecType.Long)
      case JsonSchema.String(Some(JsonSchema.StringFormat.UUID), _, _, _)        =>
        Code.QueryParamCode(name = name, queryType = Code.CodecType.UUID)
      case JsonSchema.String(_, _, _, _)                                         =>
        Code.QueryParamCode(name = name, queryType = Code.CodecType.String)
      case JsonSchema.Boolean                                                    =>
        Code.QueryParamCode(name = name, queryType = Code.CodecType.Boolean)
      case JsonSchema.OneOfSchema(_)           => throw new Exception("Alternative query parameters are not supported")
      case JsonSchema.AllOfSchema(_)           => throw new Exception("Query parameters must have exactly one schema")
      case JsonSchema.AnyOfSchema(_)           => throw new Exception("Query parameters must have exactly one schema")
      case JsonSchema.Number(_, _, _, _, _, _) =>
        throw new Exception("Floating point query parameters are currently not supported")
      case JsonSchema.ArrayType(_, _, _)       => throw new Exception("Array query parameters are not supported")
      case JsonSchema.Object(_, _, _)          => throw new Exception("Object query parameters are not supported")
      case JsonSchema.Enum(_)                  => throw new Exception("Enum query parameters are not supported")
      case JsonSchema.Null                     => throw new Exception("Null query parameters are not supported")
      case JsonSchema.AnyJson                  => throw new Exception("AnyJson query parameters are not supported")
    }
  }

  private def fieldsOfObject(openAPI: OpenAPI, annotations: Chunk[JsonSchema.MetaData])(
    obj: JsonSchema.Object,
  ): List[Code.Field] =
    obj.properties.map { case (name, schema) =>
      val field = schemaToField(schema, openAPI, name, annotations)
        .getOrElse(
          throw new Exception(s"Could not generate code for field $name of object $name"),
        )
        .asInstanceOf[Code.Field]
      if (obj.required.contains(name)) field else field.copy(fieldType = field.fieldType.opt)
    }.toList

  /**
   * @param openAPI
   * @param name
   * @param wrapped
   *   primitive type to be aliased as `name`
   * @return
   *   Code.File that have the following structure (e.g. for name="Name", and
   *   wrapped = String):
   *   {{{
   *           package base.components
   *
   *           import zio.prelude.Newtype
   *           import zio.schema.Schema
   *
   *           object Name extends Newtype[String] {
   *             implicit val schema: Schema[Name.Type] = Schema.primitive[String].transform(wrap, unwrap)
   *           }
   *   }}}
   */
  def aliasedSchemaToCode(openAPI: OpenAPI, name: String, wrapped: JsonSchema): Option[Code.File] = {
    if (!config.generateSafeTypeAliases) None
    else
      schemaToType(openAPI, name, wrapped).map { case (imports, wrappedType) =>
        Code.File(
          List("component", name.capitalize + ".scala"),
          pkgPath = List("component"),
          imports = Code.Import("zio.prelude.Newtype") :: Code.Import("zio.schema.Schema") :: imports,
          objects = List(
            Code.Object(
              name = name,
              extensions = List(s"Newtype[${wrappedType}]"),
              schema = Some(Code.Object.SchemaCode.AliasedNewtype(wrappedType)),
              endpoints = Map.empty,
              objects = Nil,
              caseClasses = Nil,
              enums = Nil,
            ),
          ),
          caseClasses = Nil,
          enums = Nil,
        )
      }
  }

  def schemaToCode(
    schema: JsonSchema,
    openAPI: OpenAPI,
    name: String,
    annotations: Chunk[JsonSchema.MetaData],
    mixins: List[String] = Nil,
    abstractMembers: List[JsonSchema.Object] = Nil,
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

      case JsonSchema.RefSchema(ref)            => throw new Exception(s"Unexpected reference schema: $ref")
      case JsonSchema.Integer(_, _, _, _, _, _) => aliasedSchemaToCode(openAPI, name, schema)
      case JsonSchema.String(_, _, _, _)        => aliasedSchemaToCode(openAPI, name, schema) /*
         * this could maybe be improved to generate a string type with validation.
         * in case of a Newtype alias, we can put validations inside the newtype object,
         * and use `transformOrFail` with real validations instead of just a plain
         * `transform` that simply `wrap` / `unwrap` the provided value.
         */
      case JsonSchema.Boolean                   => aliasedSchemaToCode(openAPI, name, schema)
      case JsonSchema.OneOfSchema(schemas) if schemas.exists(_.isPrimitive) =>
        throw new Exception("OneOf schemas with primitive types are not supported")
      case JsonSchema.OneOfSchema(schemas)                                  =>
        val discriminatorInfo                       =
          annotations.collectFirst { case JsonSchema.MetaData.Discriminator(discriminator) => discriminator }
        val discriminator: Option[String]           = discriminatorInfo.map(_.propertyName)
        val caseNameMapping: Map[String, String]    = discriminatorInfo.map(_.mapping).getOrElse(Map.empty).map {
          case (k, v) => v -> k
        }
        var caseNames: List[String]                 = Nil
        val caseClasses                             = schemas
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
        val noDiscriminator                         = caseNames.isEmpty
        val unvalidatedFields: List[Code.Field]     = abstractMembers.flatMap(fieldsOfObject(openAPI, annotations))
        val abstractMembersFields: List[Code.Field] = validateFields(unvalidatedFields).map(_.copy(annotations = Nil))
        Some(
          Code.File(
            List("component", name.capitalize + ".scala"),
            pkgPath = List("component"),
            imports = DataImports ++
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
                abstractMembers = abstractMembersFields,
              ),
            ),
          ),
        )
      case JsonSchema.AllOfSchema(schemas)                                  =>
        val genericFieldIndex = Iterator.from(0)
        val unvalidatedFields = schemas.toList.map(_.withoutAnnotations).flatMap {
          case schema @ JsonSchema.Object(_, _, _)            =>
            schemaToCode(schema, openAPI, name, annotations)
              .getOrElse(
                throw new Exception(s"Could not generate code for field $name of object $name"),
              )
              .caseClasses
              .headOption
              .fold(List.empty[Code.Field])(_.fields)
          case schema @ JsonSchema.RefSchema(SchemaRef(name)) =>
            schemaToCode(schema, openAPI, name, annotations)
              .getOrElse(
                throw new Exception(s"Could not generate code for subtype $name of allOf schema $schema"),
              )
              .caseClasses
              .headOption
              .fold(List.empty[Code.Field])(_.fields)
          case schema if schema.isPrimitive                   =>
            val name = s"field${genericFieldIndex.next()}"
            schemaToField(schema, openAPI, name, annotations).toList
          case other                                          =>
            throw new Exception(s"Unexpected subtype $other for allOf schema $schema")
        }
        val fields            = validateFields(unvalidatedFields)
        Some(
          Code.File(
            List("component", name.capitalize + ".scala"),
            pkgPath = List("component"),
            imports = DataImports,
            objects = Nil,
            caseClasses = List(
              Code.CaseClass(
                name,
                fields,
                companionObject = Some(Code.Object.schemaCompanion(name)),
                mixins = mixins,
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
        val noDiscriminator                      = caseNames.isEmpty
        Some(
          Code.File(
            List("component", name.capitalize + ".scala"),
            pkgPath = List("component"),
            imports = DataImports ++
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
      case JsonSchema.Number(_, _, _, _, _, _)      => aliasedSchemaToCode(openAPI, name, schema)
      // should we provide support for (Newtype) aliasing arrays of primitives?
      case JsonSchema.ArrayType(None, _, _)         => None
      case JsonSchema.ArrayType(Some(schema), _, _) =>
        schemaToCode(schema, openAPI, name, annotations)
      case obj: JsonSchema.Object if obj.isInvalid  =>
        throw new Exception("Object with properties and additionalProperties is not supported")
      case obj @ JsonSchema.Object(properties, _, _) if obj.isClosedDictionary =>
        val unvalidatedFields = fieldsOfObject(openAPI, annotations)(obj)
        val fields            = validateFields(unvalidatedFields)
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
            imports = DataImports,
            objects = nestedObjects.toList,
            caseClasses = List(
              Code.CaseClass(
                name,
                fields,
                companionObject = Some(Code.Object.schemaCompanion(name)),
                mixins = mixins,
              ),
            ) ++ nestedCaseClasses,
            enums = Nil,
          ),
        )
      case JsonSchema.Object(_, _, _)                                          =>
        // if obt.isOpenDictionary
        throw new IllegalArgumentException("Top-level maps are not supported")
      case JsonSchema.Enum(enums)                                              =>
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
                  case JsonSchema.EnumValue.Str(e) => Some(Code.CaseClass(e, mixins))
                  case JsonSchema.EnumValue.Null   =>
                    None // can be ignored here, but field of this type should be optional
                  case other => throw new Exception(s"OpenAPI Enums of value $other, are currently unsupported")
                }.toList,
              ),
            ),
          ),
        )
      case JsonSchema.Null    => throw new Exception("Null query parameters are not supported")
      case JsonSchema.AnyJson => throw new Exception("AnyJson query parameters are not supported")
    }
  }

  private def reconcileFieldTypes(sameName: String, fields: Seq[Code.Field]): Code.Field = {
    val reconciledFieldType = fields.view.map(_.fieldType).reduce[Code.ScalaType] {
      case (maybeBoth, areTheSame) if maybeBoth == areTheSame                 => areTheSame
      case (Collection.Opt(maybeInner), isTheSame) if maybeInner == isTheSame => isTheSame
      case (maybe, Collection.Opt(innerIsTheSame)) if maybe == innerIsTheSame => innerIsTheSame
      case (a, b)                                                             =>
        throw new Exception(
          s"Fields with the same name $sameName have different types that cannot be reconciled: $a != $b",
        )
    }
    // smart constructor will double-encode invalid scala term names,
    // so we use copy instead of creating a new instance.
    // name is the same for all, as we `.groupBy(_.name)`
    // and .head is safe (or else `reduce` would have thrown),
    // since groupBy returns a non-empty list for each key.
    fields.head.copy(fieldType = reconciledFieldType)
  }

  private def validateFields(fields: List[Code.Field]): List[Code.Field] =
    fields
      .groupBy(_.name)
      .map { case (name, fields) => reconcileFieldTypes(name, fields) }
      .toList
      .sortBy(cf => fields.iterator.map(_.name).indexOf(cf.name)) // preserve original order of fields

  private def singleFieldTypeTag(schema: JsonSchema.Object) =
    schema.properties.size == 1 &&
      schema.properties.head._2.isInstanceOf[JsonSchema.RefSchema] &&
      schema.additionalProperties == Left(false) &&
      schema.required == Chunk(schema.properties.head._1)

  private def annotationImports: List[Import] = List(
    Code.Import.Absolute("zio.schema.annotation.validate"),
    Code.Import.Absolute("zio.schema.validation.Validation"),
  )

  def addStringValidations(minLength: Option[Int], maxLength: Option[Int]): List[Annotation] = {
    (maxLength, minLength) match {
      case (Some(max), Some(min)) =>
        Annotation(
          s"@validate[String](Validation.maxLength($max) && Validation.minLength($min))",
          annotationImports,
        ) :: Nil
      case (Some(max), None)      =>
        Annotation(s"@validate[String](Validation.maxLength($max))", annotationImports) :: Nil
      case (None, Some(min))      =>
        Annotation(s"@validate[String](Validation.minLength($min))", annotationImports) :: Nil
      case (None, None)           =>
        Nil
    }
  }

  def addNumericValidations[T: ClassTag](minOpt: Option[T], maxOpt: Option[T]): List[Annotation] = {
    def typeName: String = implicitly[ClassTag[T]].toString

    (minOpt, maxOpt) match {
      case (Some(min), Some(max)) =>
        Annotation(
          s"@validate[${typeName}](Validation.greaterThan($min) && Validation.lessThan($max))",
          annotationImports,
        ) :: Nil
      case (Some(min), None)      =>
        Annotation(s"@validate[${typeName}](Validation.greaterThan($min))", annotationImports) :: Nil
      case (None, Some(max))      =>
        Annotation(s"@validate[${typeName}](Validation.lessThan($max))", annotationImports) :: Nil
      case (None, None)           =>
        Nil
    }
  }

  private def safeCastLongToInt(l: Long): Int = {
    val i = l.intValue()
    require(l == i, s"Long[$l] does not fit in an Int: failed to cast")
    i
  }

  private def safeCastDoubleToFloat(d: Double): Float = {
    val f = d.floatValue()
    require(d == f, s"Double[$d] does not fit in a Float: failed to cast")
    f
  }

  def schemaToField(
    schema: JsonSchema,
    openAPI: OpenAPI,
    name: String,
    annotations: Chunk[JsonSchema.MetaData],
  ): Option[Code.Field] = {
    schema match {
      case JsonSchema.AnnotatedSchema(s, _)     =>
        schemaToField(s.withoutAnnotations, openAPI, name, schema.annotations)
      case JsonSchema.RefSchema(SchemaRef(ref)) =>
        Some(Code.Field(name, Code.TypeRef(ref.capitalize), config.fieldNamesNormalization))
      case JsonSchema.RefSchema(ref)            =>
        throw new Exception(s" Not found: $ref. Only references to internal schemas are supported.")
      case JsonSchema.Integer(
            JsonSchema.IntegerFormat.Int32,
            minimum,
            exclusiveMinimum,
            maximum,
            exclusiveMaximum,
            _,
          ) =>
        val exclusiveMin =
          if (exclusiveMinimum.isDefined && exclusiveMinimum.get == Left(true)) minimum
          else if (exclusiveMinimum.isDefined && exclusiveMinimum.get.isRight) exclusiveMinimum.get.toOption
          else minimum.map(_ - 1)
        val exclusiveMax =
          if (exclusiveMaximum.isDefined && exclusiveMaximum.get == Left(true)) maximum
          else if (exclusiveMaximum.isDefined && exclusiveMaximum.get.isRight) exclusiveMaximum.get.toOption
          else maximum.map(_ + 1)

        val annotations = addNumericValidations[Int](
          exclusiveMin.collect { case l if l >= Int.MinValue => safeCastLongToInt(l) },
          exclusiveMax.collect { case l if l <= Int.MaxValue => safeCastLongToInt(l) },
        )

        Some(Code.Field(name, Code.Primitive.ScalaInt, annotations, config.fieldNamesNormalization))
      case JsonSchema.Integer(
            JsonSchema.IntegerFormat.Int64,
            minimum,
            exclusiveMinimum,
            maximum,
            exclusiveMaximum,
            _,
          ) =>
        val exclusiveMin =
          if (exclusiveMinimum.isDefined && exclusiveMinimum.get == Left(true)) minimum
          else if (exclusiveMinimum.isDefined && exclusiveMinimum.get.isRight) exclusiveMinimum.get.toOption
          else minimum.map(_ - 1)
        val exclusiveMax =
          if (exclusiveMaximum.isDefined && exclusiveMaximum.get == Left(true)) maximum
          else if (exclusiveMaximum.isDefined && exclusiveMaximum.get.isRight) exclusiveMaximum.get.toOption
          else maximum.map(_ + 1)

        val annotations = addNumericValidations[Long](exclusiveMin, exclusiveMax)
        Some(Code.Field(name, Code.Primitive.ScalaLong, annotations, config.fieldNamesNormalization))
      case JsonSchema.Integer(
            JsonSchema.IntegerFormat.Timestamp,
            minimum,
            exclusiveMinimum,
            maximum,
            exclusiveMaximum,
            _,
          ) =>
        val exclusiveMin =
          if (exclusiveMinimum.isDefined && exclusiveMinimum.get == Left(true)) minimum
          else if (exclusiveMinimum.isDefined && exclusiveMinimum.get.isRight) exclusiveMinimum.get.toOption
          else minimum.map(_ - 1)
        val exclusiveMax =
          if (exclusiveMaximum.isDefined && exclusiveMaximum.get == Left(true)) maximum
          else if (exclusiveMaximum.isDefined && exclusiveMaximum.get.isRight) exclusiveMaximum.get.toOption
          else maximum.map(_ + 1)
        val annotations  = addNumericValidations[Long](exclusiveMin, exclusiveMax)
        Some(Code.Field(name, Code.Primitive.ScalaLong, annotations, config.fieldNamesNormalization))

      case JsonSchema.String(Some(JsonSchema.StringFormat.UUID), _, maxLength, minLength)                             =>
        val annotations = addStringValidations(minLength, maxLength)
        Some(Code.Field(name, Code.Primitive.ScalaUUID, annotations, config.fieldNamesNormalization))
      case JsonSchema.String(_, _, maxLength, minLength)                                                              =>
        val annotations = addStringValidations(minLength, maxLength)
        Some(Code.Field(name, Code.Primitive.ScalaString, annotations, config.fieldNamesNormalization))
      case JsonSchema.Boolean                                                                                         =>
        Some(Code.Field(name, Code.Primitive.ScalaBoolean, config.fieldNamesNormalization))
      case JsonSchema.OneOfSchema(schemas)                                                                            =>
        val tpe =
          schemas
            .map(_.withoutAnnotations)
            .flatMap(schemaToField(_, openAPI, "unused", annotations))
            .map(_.fieldType)
            .reduceLeft(Code.ScalaType.Or.apply)
        Some(Code.Field(name, tpe, config.fieldNamesNormalization))
      case JsonSchema.AllOfSchema(_)                                                                                  =>
        throw new Exception("Inline allOf schemas are not supported for fields")
      case JsonSchema.AnyOfSchema(schemas)                                                                            =>
        val tpe =
          schemas
            .map(_.withoutAnnotations)
            .flatMap(schemaToField(_, openAPI, "unused", annotations))
            .map(_.fieldType)
            .reduceLeft(Code.ScalaType.Or.apply)
        Some(Code.Field(name, tpe, config.fieldNamesNormalization))
      case JsonSchema.Number(JsonSchema.NumberFormat.Double, minimum, exclusiveMinimum, maximum, exclusiveMaximum, _) =>
        val exclusiveMin =
          if (exclusiveMinimum.isDefined && exclusiveMinimum.get == Left(true)) minimum
          else if (exclusiveMinimum.isDefined && exclusiveMinimum.get.isRight) exclusiveMinimum.get.toOption
          else minimum.map(_ - 1)
        val exclusiveMax =
          if (exclusiveMaximum.isDefined && exclusiveMaximum.get == Left(true)) maximum
          else if (exclusiveMaximum.isDefined && exclusiveMaximum.get.isRight) exclusiveMaximum.get.toOption
          else maximum.map(_ + 1)

        val annotations = addNumericValidations[Double](exclusiveMin, exclusiveMax)
        Some(Code.Field(name, Code.Primitive.ScalaDouble, annotations, config.fieldNamesNormalization))
      case JsonSchema.Number(JsonSchema.NumberFormat.Float, minimum, exclusiveMinimum, maximum, exclusiveMaximum, _)  =>
        val exclusiveMin =
          if (exclusiveMinimum.isDefined && exclusiveMinimum.get == Left(true)) minimum
          else if (exclusiveMinimum.isDefined && exclusiveMinimum.get.isRight) exclusiveMinimum.get.toOption
          else minimum.map(_ - 1)
        val exclusiveMax =
          if (exclusiveMaximum.isDefined && exclusiveMaximum.get == Left(true)) maximum
          else if (exclusiveMaximum.isDefined && exclusiveMaximum.get.isRight) exclusiveMaximum.get.toOption
          else maximum.map(_ + 1)

        val annotations = addNumericValidations[Float](
          exclusiveMin.collect { case l if l >= Float.MinValue => safeCastDoubleToFloat(l) },
          exclusiveMax.collect { case l if l <= Float.MaxValue => safeCastDoubleToFloat(l) },
        )
        Some(Code.Field(name, Code.Primitive.ScalaFloat, annotations, config.fieldNamesNormalization))
      case JsonSchema.ArrayType(items, minItems, uniqueItems)                                                         =>
        val nonEmpty = minItems.exists(_ > 1)
        val tpe      = items
          .flatMap(schemaToField(_, openAPI, name, annotations))
          .map(f => if (uniqueItems) f.fieldType.set(nonEmpty) else f.fieldType.seq(nonEmpty))
          .orElse(
            Some {
              if (uniqueItems) Code.Primitive.ScalaString.set(nonEmpty) else Code.Primitive.ScalaString.seq(nonEmpty)
            },
          )
        tpe.map(Code.Field(name, _, config.fieldNamesNormalization))
      case JsonSchema.Object(properties, additionalProperties, _)
          if properties.nonEmpty && additionalProperties.isRight =>
        // Can't be an object and a map at the same time
        throw new Exception("Object with properties and additionalProperties is not supported")
      case JsonSchema.Object(properties, additionalProperties, _)
          if properties.isEmpty && additionalProperties.isRight =>
        val (vSchema, kSchemaOpt) = {
          val vs                = additionalProperties.toOption.get
          val (ks, annotations) = JsonSchema.Object.extractKeySchemaFromAnnotations(vs)
          vs.withoutAnnotations.annotate(annotations) -> ks
        }

        Some(
          Code.Field(
            name,
            Code.Collection.Map(
              schemaToField(vSchema, openAPI, name, annotations).get.fieldType,
              kSchemaOpt.collect {
                case ss: JsonSchema.String     =>
                  schemaToField(ss, openAPI, name, annotations).get.fieldType
                case JsonSchema.RefSchema(ref) =>
                  val baref = ref.replaceFirst("^#/components/schemas/", "")
                  resolveSchemaRef(openAPI, baref) match {
                    case ks: JsonSchema.String =>
                      if (config.generateSafeTypeAliases) TypeRef(baref + ".Type")
                      else schemaToField(ks, openAPI, name, annotations).get.fieldType
                    case nonStringSchema       =>
                      throw new IllegalArgumentException(
                        s"x-string-key-schema must reference a string schema, but got: ${nonStringSchema.toJson}",
                      )
                  }
                case nonStringSchema           =>
                  throw new IllegalArgumentException(
                    s"x-string-key-schema must be a string schema, but got: ${nonStringSchema.toJson}",
                  )
              },
            ),
            config.fieldNamesNormalization,
          ),
        )
      case JsonSchema.Object(_, _, _)                                                                                 =>
        Some(Code.Field(name, Code.TypeRef(name.capitalize), config.fieldNamesNormalization))
      case JsonSchema.Enum(_)                                                                                         =>
        Some(Code.Field(name, Code.TypeRef(name.capitalize), config.fieldNamesNormalization))
      case JsonSchema.Null                                                                                            =>
        Some(Code.Field(name, Code.ScalaType.Unit, config.fieldNamesNormalization))
      case JsonSchema.AnyJson                                                                                         =>
        Some(Code.Field(name, Code.ScalaType.JsonAST, config.fieldNamesNormalization))
    }
  }

}
