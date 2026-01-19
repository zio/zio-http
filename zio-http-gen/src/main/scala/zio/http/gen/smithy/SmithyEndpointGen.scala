package zio.http.gen.smithy

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import scala.jdk.CollectionConverters._

import zio.http.Method
import zio.http.gen.scala.Code
import zio.http.gen.scala.Code._
import zio.http.gen.scala.CodeGen

/**
 * Generates zio-http Endpoint definitions from Smithy models.
 *
 * This code generator reads a SmithyModel AST and produces:
 *   - Endpoint definitions for operations with @http traits
 *   - Case classes for structure shapes
 *   - Sealed traits for union shapes
 *   - Type aliases for simple shapes
 */
object SmithyEndpointGen {

  private val DataImports = List(
    Code.Import("zio.schema._"),
  )

  private val EndpointImports = List(
    Code.Import("zio.http._"),
    Code.Import("zio.http.endpoint._"),
    Code.Import("zio.http.codec._"),
  )

  /**
   * Generate Code.Files from a SmithyModel using default config
   */
  def fromSmithyModel(model: SmithyModel): Code.Files =
    fromSmithyModel(model, SmithyConfig.default)

  /**
   * Generate Code.Files from a SmithyModel
   *
   * @param model
   *   The SmithyModel to generate code from
   * @param config
   *   Configuration options for code generation
   */
  def fromSmithyModel(model: SmithyModel, config: SmithyConfig): Code.Files = {
    val gen = new SmithyEndpointGen(model, config)
    gen.generate()
  }

  /**
   * Generate Code.Files from a SmithyModel with validation using default config
   *
   * @param model
   *   The SmithyModel to generate code from
   * @param validate
   *   Whether to validate the model before generation
   * @return
   *   Either validation errors or the generated code files
   */
  def fromSmithyModelValidated(model: SmithyModel, validate: Boolean = true): Either[String, Code.Files] =
    fromSmithyModelValidated(model, SmithyConfig.default, validate)

  /**
   * Generate Code.Files from a SmithyModel with optional validation
   *
   * @param model
   *   The SmithyModel to generate code from
   * @param config
   *   Configuration options for code generation
   * @param validate
   *   Whether to validate the model before generation (overrides config)
   * @return
   *   Either validation errors or the generated code files
   */
  def fromSmithyModelValidated(
    model: SmithyModel,
    config: SmithyConfig,
    validate: Boolean,
  ): Either[String, Code.Files] = {
    val shouldValidate = validate && config.validateBeforeGeneration
    if (shouldValidate) {
      val validationResult = SmithyValidation.validate(model)
      if (!validationResult.isValid) {
        Left(s"Smithy model validation failed:\n${validationResult.render}")
      } else {
        Right(fromSmithyModel(model, config))
      }
    } else {
      Right(fromSmithyModel(model, config))
    }
  }

  /**
   * Parse a Smithy IDL string and generate Code.Files using default config
   */
  def fromString(smithyIdl: String): Either[String, Code.Files] =
    fromString(smithyIdl, SmithyConfig.default)

  /**
   * Parse a Smithy IDL string and generate Code.Files
   *
   * @param smithyIdl
   *   The Smithy IDL string to parse
   * @param config
   *   Configuration options for code generation
   */
  def fromString(smithyIdl: String, config: SmithyConfig): Either[String, Code.Files] = {
    SmithyParser.parse(smithyIdl).flatMap { model =>
      fromSmithyModelValidated(model, config, config.validateBeforeGeneration)
    }
  }

  /**
   * Read a single .smithy file and generate Code.Files using default config
   */
  def fromFile(path: Path): Either[String, Code.Files] =
    fromFile(path, SmithyConfig.default)

  /**
   * Read a single .smithy file and generate Code.Files
   *
   * @param path
   *   Path to the .smithy file
   * @param config
   *   Configuration options for code generation
   */
  def fromFile(path: Path, config: SmithyConfig): Either[String, Code.Files] = {
    try {
      val content = new String(Files.readAllBytes(path), StandardCharsets.UTF_8)
      fromString(content, config)
    } catch {
      case e: Exception => Left(s"Failed to read file ${path}: ${e.getMessage}")
    }
  }

  /**
   * Read all .smithy files from a directory and generate combined Code.Files
   * using default config
   */
  def fromDirectory(dir: Path): Either[String, Code.Files] =
    fromDirectory(dir, SmithyConfig.default)

  /**
   * Read all .smithy files from a directory and generate combined Code.Files
   *
   * @param dir
   *   Directory containing .smithy files
   * @param config
   *   Configuration options for code generation
   * @return
   *   Either parse/validation errors or the generated code files
   */
  def fromDirectory(dir: Path, config: SmithyConfig): Either[String, Code.Files] = {
    try {
      if (!Files.isDirectory(dir)) {
        return Left(s"Not a directory: $dir")
      }

      val smithyFiles = Files
        .walk(dir)
        .iterator()
        .asScala
        .filter(p => Files.isRegularFile(p) && p.toString.endsWith(".smithy"))
        .toList

      if (smithyFiles.isEmpty) {
        return Left(s"No .smithy files found in $dir")
      }

      // Parse all files
      val results = smithyFiles.map { file =>
        val content = new String(Files.readAllBytes(file), StandardCharsets.UTF_8)
        SmithyParser.parse(content) match {
          case Right(model) => Right(file -> model)
          case Left(err)    => Left(s"Failed to parse ${file}: $err")
        }
      }

      // Check for parse errors
      val errors = results.collect { case Left(err) => err }
      if (errors.nonEmpty) {
        return Left(errors.mkString("\n"))
      }

      // Merge all models
      val models      = results.collect { case Right((_, model)) => model }
      val mergedModel = mergeModels(models)

      // Optionally validate based on config
      fromSmithyModelValidated(mergedModel, config, config.validateBeforeGeneration)
    } catch {
      case e: Exception => Left(s"Failed to read directory ${dir}: ${e.getMessage}")
    }
  }

  /**
   * Generate code from .smithy files and write to target directory using
   * default config
   *
   * @param sourceDir
   *   Directory containing .smithy files
   * @param targetDir
   *   Directory to write generated Scala files
   * @param basePackage
   *   Base package for generated code
   * @param scalafmtPath
   *   Optional path to scalafmt config for formatting
   * @return
   *   Either an error message or the list of generated file paths
   */
  def generate(
    sourceDir: Path,
    targetDir: Path,
    basePackage: String,
    scalafmtPath: Option[Path] = None,
  ): Either[String, Iterable[Path]] =
    generate(sourceDir, targetDir, basePackage, scalafmtPath, SmithyConfig.default)

  /**
   * Generate code from .smithy files and write to target directory
   *
   * @param sourceDir
   *   Directory containing .smithy files
   * @param targetDir
   *   Directory to write generated Scala files
   * @param basePackage
   *   Base package for generated code
   * @param scalafmtPath
   *   Optional path to scalafmt config for formatting
   * @param config
   *   Configuration options for code generation
   * @return
   *   Either an error message or the list of generated file paths
   */
  def generate(
    sourceDir: Path,
    targetDir: Path,
    basePackage: String,
    scalafmtPath: Option[Path],
    config: SmithyConfig,
  ): Either[String, Iterable[Path]] = {
    fromDirectory(sourceDir, config).map { files =>
      CodeGen.writeFiles(files, targetDir, basePackage, scalafmtPath)
    }
  }

  /**
   * Generate code from a single .smithy file and write to target directory
   * using default config
   *
   * @param sourceFile
   *   Path to the .smithy file
   * @param targetDir
   *   Directory to write generated Scala files
   * @param basePackage
   *   Base package for generated code
   * @param scalafmtPath
   *   Optional path to scalafmt config for formatting
   * @return
   *   Either an error message or the list of generated file paths
   */
  def generateFromFile(
    sourceFile: Path,
    targetDir: Path,
    basePackage: String,
    scalafmtPath: Option[Path] = None,
  ): Either[String, Iterable[Path]] =
    generateFromFile(sourceFile, targetDir, basePackage, scalafmtPath, SmithyConfig.default)

  /**
   * Generate code from a single .smithy file and write to target directory
   *
   * @param sourceFile
   *   Path to the .smithy file
   * @param targetDir
   *   Directory to write generated Scala files
   * @param basePackage
   *   Base package for generated code
   * @param scalafmtPath
   *   Optional path to scalafmt config for formatting
   * @param config
   *   Configuration options for code generation
   * @return
   *   Either an error message or the list of generated file paths
   */
  def generateFromFile(
    sourceFile: Path,
    targetDir: Path,
    basePackage: String,
    scalafmtPath: Option[Path],
    config: SmithyConfig,
  ): Either[String, Iterable[Path]] = {
    fromFile(sourceFile, config).map { files =>
      CodeGen.writeFiles(files, targetDir, basePackage, scalafmtPath)
    }
  }

  /**
   * Merge multiple SmithyModels into one Uses the namespace from the first
   * model
   */
  private def mergeModels(models: List[SmithyModel]): SmithyModel = {
    if (models.isEmpty) {
      SmithyModel("2", "", Map.empty, Nil, Map.empty)
    } else {
      val first            = models.head
      val allShapes        = models.flatMap(_.shapes).toMap
      val allUseStatements = models.flatMap(_.useStatements).distinct
      val allMetadata      = models.flatMap(_.metadata).toMap

      SmithyModel(
        version = first.version,
        namespace = first.namespace,
        metadata = allMetadata,
        useStatements = allUseStatements,
        shapes = allShapes,
      )
    }
  }
}

final class SmithyEndpointGen(model: SmithyModel, config: SmithyConfig) {
  import SmithyEndpointGen._

  def generate(): Code.Files = {
    val componentFiles = generateComponents()
    val endpointFiles  = generateEndpoints()
    Code.Files(componentFiles ++ endpointFiles)
  }

  // ===========================================================================
  // Component Generation (structures, unions, enums, etc.)
  // ===========================================================================

  private def generateComponents(): List[Code.File] = {
    model.shapes.toList.flatMap { case (name, shape) =>
      shapeToCode(name, shape)
    }
  }

  private def shapeToCode(name: String, shape: Shape): Option[Code.File] = {
    shape match {
      case s: Shape.StructureShape => structureToCode(name, s)
      case s: Shape.UnionShape     => unionToCode(name, s)
      case s: Shape.EnumShape      => enumToCode(name, s)
      case _: Shape.ListShape      => None // Lists are inlined as Chunk[T]
      case _: Shape.MapShape       => None // Maps are inlined as Map[K, V]
      case _: Shape.SimpleShape    => None // Simple shapes are primitives
      case _: Shape.ServiceShape   => None // Services don't generate types
      case _: Shape.OperationShape => None // Operations generate endpoints
      case _: Shape.ResourceShape  => None // Resources are organizational
      case _                       => None
    }
  }

  private def structureToCode(name: String, structure: Shape.StructureShape): Option[Code.File] = {
    val fields = structure.members.toList.map { case (memberName, member) =>
      val fieldType = shapeIdToType(member.target)
      val finalType = if (member.isRequired) fieldType else fieldType.opt
      Code.Field(memberName, finalType, Nil, config.fieldNamesNormalization)
    }

    Some(
      Code.File(
        path = List("component", s"${name.capitalize}.scala"),
        pkgPath = List("component"),
        imports = DataImports,
        objects = Nil,
        caseClasses = List(
          Code.CaseClass(
            name = name,
            fields = fields,
            companionObject = Some(Code.Object.schemaCompanion(name)),
            mixins = Nil,
          ),
        ),
        enums = Nil,
      ),
    )
  }

  private def unionToCode(name: String, union: Shape.UnionShape): Option[Code.File] = {
    val cases = union.members.toList.map { case (memberName, member) =>
      val fields = List(
        Code.Field("value", shapeIdToType(member.target), Nil, config.fieldNamesNormalization),
      )
      Code.CaseClass(
        name = memberName.capitalize,
        fields = fields,
        companionObject = None,
        mixins = List(name),
      )
    }

    Some(
      Code.File(
        path = List("component", s"${name.capitalize}.scala"),
        pkgPath = List("component"),
        imports = DataImports ++ List(Code.Import("zio.schema.annotation._")),
        objects = Nil,
        caseClasses = Nil,
        enums = List(
          Code.Enum(
            name = name,
            cases = cases,
            caseNames = Nil,
            discriminator = None,
            noDiscriminator = true,
            schema = true,
          ),
        ),
      ),
    )
  }

  private def enumToCode(name: String, `enum`: Shape.EnumShape): Option[Code.File] = {
    val cases = `enum`.members.toList.map { case (memberName, _) =>
      Code.CaseClass(
        name = memberName,
        fields = Nil,
        companionObject = None,
        mixins = List(name),
      )
    }

    Some(
      Code.File(
        path = List("component", s"${name.capitalize}.scala"),
        pkgPath = List("component"),
        imports = DataImports,
        objects = Nil,
        caseClasses = Nil,
        enums = List(
          Code.Enum(
            name = name,
            cases = cases,
            caseNames = Nil,
            discriminator = None,
            noDiscriminator = false,
            schema = true,
          ),
        ),
      ),
    )
  }

  // ===========================================================================
  // Endpoint Generation
  // ===========================================================================

  private def generateEndpoints(): List[Code.File] = {
    val httpOperations = model.httpOperations

    if (httpOperations.isEmpty) return Nil

    // Group operations by service or just put them all in one file
    val endpoints = httpOperations.toList.flatMap { case (opName, op) =>
      operationToEndpoint(opName, op)
    }

    if (endpoints.isEmpty) return Nil

    // Create a single Endpoints object
    List(
      Code.File(
        path = List("Endpoints.scala"),
        pkgPath = Nil,
        imports = EndpointImports ++ List(Code.Import.FromBase("component._")),
        objects = List(
          Code.Object(
            name = "Endpoints",
            extensions = Nil,
            schema = None,
            endpoints = endpoints.toMap,
            objects = Nil,
            caseClasses = Nil,
            enums = Nil,
          ),
        ),
        caseClasses = Nil,
        enums = Nil,
      ),
    )
  }

  private def operationToEndpoint(name: String, op: Shape.OperationShape): Option[(Code.Field, Code.EndpointCode)] = {
    op.httpTrait.map { http =>
      val method      = httpMethodToMethod(http.method)
      val inputShape  = op.input.flatMap(id => model.getStructure(id.name))
      val outputShape = op.output.flatMap(id => model.getStructure(id.name))

      // Build path segments
      val segments = buildPathSegments(http.uri, inputShape)

      // Extract query parameters (members with @httpQuery)
      val queryParams: Set[Code.QueryParamCode] = inputShape.toSet.flatMap { (struct: Shape.StructureShape) =>
        struct.members.values.flatMap { member =>
          member.httpQuery.map { queryName =>
            Code.QueryParamCode(queryName, shapeIdToCodecType(member.target))
          }
        }
      }

      // Extract headers (members with @httpHeader)
      val headers = inputShape.toList.flatMap { struct =>
        struct.members.values.flatMap { member =>
          member.httpHeader.map { headerName =>
            Code.HeaderCode(headerName)
          }
        }.toList
      }

      // Check for streaming on input
      val inputStreaming = isStreamingShape(op.input)

      // Check for streaming on output
      val outputStreaming = isStreamingShape(op.output)

      // Input type - the request body (member with @httpPayload or the whole input minus path/query/header)
      val inType = determineInputType(op, inputShape)

      // Output type
      val outType = op.output.map(_.name).getOrElse("Unit")

      // Get documentation from operation traits
      val opDoc = op.traits.collectFirst { case SmithyTrait.Documentation(doc) => doc }

      // Determine media type
      val inMediaType  = determineMediaType(inputShape)
      val outMediaType = determineMediaType(outputShape)

      // Error types
      val errorCodes = op.errors.map { errorId =>
        // Check if error has @httpError trait
        val errorShape = model.getStructure(errorId.name)
        val statusCode = errorShape.flatMap { s =>
          s.traits.collectFirst { case SmithyTrait.HttpError(code) => code }
        }.getOrElse(500)

        val errorDoc = errorShape.flatMap { s =>
          s.traits.collectFirst { case SmithyTrait.Documentation(doc) => doc }
        }

        Code.OutCode(
          outType = errorId.name,
          status = zio.http.Status.fromInt(statusCode),
          mediaType = Some("application/json"),
          doc = errorDoc,
          streaming = false,
        )
      }

      val endpointCode = Code.EndpointCode(
        method = method,
        pathPatternCode = Code.PathPatternCode(segments),
        queryParamsCode = queryParams,
        headersCode = Code.HeadersCode(headers),
        inCode = Code.InCode(inType, inMediaType, opDoc, inputStreaming),
        outCodes = List(
          Code.OutCode(
            outType = outType,
            status = zio.http.Status.fromInt(http.code),
            mediaType = outMediaType,
            doc = opDoc,
            streaming = outputStreaming,
          ),
        ),
        errorsCode = errorCodes,
        authTypeCode = None,
      )

      Code.Field(name, config.fieldNamesNormalization) -> endpointCode
    }
  }

  /**
   * Check if a shape reference points to a streaming blob
   */
  private def isStreamingShape(shapeRef: Option[ShapeId]): Boolean = {
    shapeRef.exists { ref =>
      model.getShape(ref.name) match {
        case Some(shape) =>
          shape.traits.exists {
            case SmithyTrait.Streaming   => true
            case SmithyTrait.EventStream => true
            case _                       => false
          }
        case None        => false
      }
    }
  }

  /**
   * Determine media type from shape traits
   */
  private def determineMediaType(shape: Option[Shape.StructureShape]): Option[String] = {
    shape.flatMap { s =>
      s.traits.collectFirst { case SmithyTrait.MediaType(mt) => mt }
    }.orElse(Some("application/json"))
  }

  private def httpMethodToMethod(method: String): Method = method.toUpperCase match {
    case "GET"     => Method.GET
    case "POST"    => Method.POST
    case "PUT"     => Method.PUT
    case "DELETE"  => Method.DELETE
    case "PATCH"   => Method.PATCH
    case "HEAD"    => Method.HEAD
    case "OPTIONS" => Method.OPTIONS
    case "TRACE"   => Method.TRACE
    case _         => Method.GET
  }

  private def buildPathSegments(
    uri: String,
    inputShape: Option[Shape.StructureShape],
  ): List[Code.PathSegmentCode] = {
    val segments = uri.stripPrefix("/").split("/").toList

    segments.filter(_.nonEmpty).map { segment =>
      if (segment.startsWith("{") && segment.endsWith("}")) {
        val paramName = segment.tail.init
        val paramType = inputShape.flatMap { struct =>
          struct.members.get(paramName).map(m => shapeIdToCodecType(m.target))
        }.getOrElse(Code.CodecType.String)

        Code.PathSegmentCode(paramName, paramType)
      } else {
        Code.PathSegmentCode(segment, Code.CodecType.Literal)
      }
    }
  }

  private def determineInputType(
    op: Shape.OperationShape,
    inputShape: Option[Shape.StructureShape],
  ): String = {
    inputShape match {
      case None         => "Unit"
      case Some(struct) =>
        // Check if there's a @httpPayload member
        val payloadMember = struct.members.values.find(_.httpPayload)
        payloadMember match {
          case Some(member) => shapeIdToTypeName(member.target)
          case None         =>
            // If no payload, check if there are non-path/query/header members
            val bodyMembers = struct.members.values.filterNot { m =>
              m.httpLabel || m.httpQuery.isDefined || m.httpHeader.isDefined
            }
            if (bodyMembers.isEmpty) "Unit"
            else op.input.map(_.name).getOrElse("Unit")
        }
    }
  }

  // ===========================================================================
  // Type Mapping
  // ===========================================================================

  private def shapeIdToType(shapeId: ShapeId): Code.ScalaType = {
    val name = shapeId.name

    // Check if it's a prelude type
    name match {
      case "String"     => Code.Primitive.ScalaString
      case "Integer"    => Code.Primitive.ScalaInt
      case "Long"       => Code.Primitive.ScalaLong
      case "Short"      => Code.Primitive.ScalaShort
      case "Byte"       => Code.Primitive.ScalaByte
      case "Float"      => Code.Primitive.ScalaFloat
      case "Double"     => Code.Primitive.ScalaDouble
      case "Boolean"    => Code.Primitive.ScalaBoolean
      case "Timestamp"  => Code.Primitive.ScalaInstant
      case "Blob"       => Code.TypeRef("Chunk[Byte]")
      case "Document"   => Code.ScalaType.JsonAST
      case "BigInteger" => Code.TypeRef("BigInt")
      case "BigDecimal" => Code.TypeRef("BigDecimal")
      case "Unit"       => Code.ScalaType.Unit
      case _            =>
        // Check if it's a shape in our model
        model.shapes.get(name) match {
          case Some(_: Shape.ListShape)   =>
            model.shapes
              .get(name)
              .collect { case l: Shape.ListShape => l }
              .map { list =>
                shapeIdToType(list.member).seq(nonEmpty = false)
              }
              .getOrElse(Code.TypeRef(name))
          case Some(_: Shape.MapShape)    =>
            model.shapes
              .get(name)
              .collect { case m: Shape.MapShape => m }
              .map { map =>
                Code.Collection.Map(shapeIdToType(map.value), Some(shapeIdToType(map.key)))
              }
              .getOrElse(Code.TypeRef(name))
          case Some(_: Shape.SimpleShape) =>
            // Simple shapes are type aliases
            shapeIdToTypeForSimpleShape(name)
          case _                          =>
            // Reference to a structure, union, etc.
            Code.TypeRef(name)
        }
    }
  }

  private def shapeIdToTypeForSimpleShape(name: String): Code.ScalaType = {
    model.shapes.get(name) match {
      case Some(_: Shape.StringShape)     => Code.Primitive.ScalaString
      case Some(_: Shape.IntegerShape)    => Code.Primitive.ScalaInt
      case Some(_: Shape.LongShape)       => Code.Primitive.ScalaLong
      case Some(_: Shape.ShortShape)      => Code.Primitive.ScalaShort
      case Some(_: Shape.ByteShape)       => Code.Primitive.ScalaByte
      case Some(_: Shape.FloatShape)      => Code.Primitive.ScalaFloat
      case Some(_: Shape.DoubleShape)     => Code.Primitive.ScalaDouble
      case Some(_: Shape.BooleanShape)    => Code.Primitive.ScalaBoolean
      case Some(_: Shape.TimestampShape)  => Code.Primitive.ScalaInstant
      case Some(_: Shape.BlobShape)       => Code.TypeRef("Chunk[Byte]")
      case Some(_: Shape.DocumentShape)   => Code.ScalaType.JsonAST
      case Some(_: Shape.BigIntegerShape) => Code.TypeRef("BigInt")
      case Some(_: Shape.BigDecimalShape) => Code.TypeRef("BigDecimal")
      case _                              => Code.TypeRef(name)
    }
  }

  private def shapeIdToTypeName(shapeId: ShapeId): String = {
    val name = shapeId.name
    name match {
      case "String"     => "String"
      case "Integer"    => "Int"
      case "Long"       => "Long"
      case "Short"      => "Short"
      case "Byte"       => "Byte"
      case "Float"      => "Float"
      case "Double"     => "Double"
      case "Boolean"    => "Boolean"
      case "Timestamp"  => "java.time.Instant"
      case "Blob"       => "Chunk[Byte]"
      case "Document"   => "zio.json.ast.Json"
      case "BigInteger" => "BigInt"
      case "BigDecimal" => "BigDecimal"
      case "Unit"       => "Unit"
      case _            => name
    }
  }

  private def shapeIdToCodecType(shapeId: ShapeId): Code.CodecType = {
    val name = shapeId.name
    name match {
      case "String"    => Code.CodecType.String
      case "Integer"   => Code.CodecType.Int
      case "Long"      => Code.CodecType.Long
      case "Boolean"   => Code.CodecType.Boolean
      case "Timestamp" => Code.CodecType.Instant
      case _           =>
        // Check if it's a simple shape alias
        model.shapes.get(name) match {
          case Some(_: Shape.StringShape)    => Code.CodecType.String
          case Some(_: Shape.IntegerShape)   => Code.CodecType.Int
          case Some(_: Shape.LongShape)      => Code.CodecType.Long
          case Some(_: Shape.BooleanShape)   => Code.CodecType.Boolean
          case Some(_: Shape.TimestampShape) => Code.CodecType.Instant
          case _                             => Code.CodecType.String // fallback
        }
    }
  }
}
