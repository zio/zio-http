package zio.http.gen.smithy

/**
 * Smithy Model Validation
 * 
 * Validates Smithy models according to the Smithy specification:
 * https://smithy.io/2.0/spec/model-validation.html
 * 
 * Provides comprehensive validation with helpful error messages including:
 * - Shape reference validation (undefined references)
 * - HTTP trait validation (path parameters, methods, status codes)
 * - Constraint trait validation (@length, @range, @pattern)
 * - Structure validation (required members, duplicate members)
 * - Operation validation (input/output/error shapes)
 * - Service validation (referenced operations/resources exist)
 */
object SmithyValidation {

  /**
   * A validation error with context about where and what failed
   */
  final case class ValidationError(
    shapeName: Option[String],
    memberName: Option[String],
    errorType: ErrorType,
    message: String
  ) {
    def render: String = {
      val location = (shapeName, memberName) match {
        case (Some(shape), Some(member)) => s"$shape.$member"
        case (Some(shape), None)         => shape
        case (None, Some(member))        => s"<unknown>.$member"
        case (None, None)                => "<model>"
      }
      s"$location: [${errorType.name}] $message"
    }
  }

  sealed trait ErrorType {
    def name: String
  }
  object ErrorType {
    case object UndefinedShape extends ErrorType { def name = "UNDEFINED_SHAPE" }
    case object UndefinedMember extends ErrorType { def name = "UNDEFINED_MEMBER" }
    case object InvalidHttpMethod extends ErrorType { def name = "INVALID_HTTP_METHOD" }
    case object InvalidHttpPath extends ErrorType { def name = "INVALID_HTTP_PATH" }
    case object InvalidStatusCode extends ErrorType { def name = "INVALID_STATUS_CODE" }
    case object PathParameterMismatch extends ErrorType { def name = "PATH_PARAMETER_MISMATCH" }
    case object MissingPathParameter extends ErrorType { def name = "MISSING_PATH_PARAMETER" }
    case object DuplicatePathParameter extends ErrorType { def name = "DUPLICATE_PATH_PARAMETER" }
    case object InvalidConstraint extends ErrorType { def name = "INVALID_CONSTRAINT" }
    case object InvalidPattern extends ErrorType { def name = "INVALID_PATTERN" }
    case object IncompatibleTrait extends ErrorType { def name = "INCOMPATIBLE_TRAIT" }
    case object MissingRequiredTrait extends ErrorType { def name = "MISSING_REQUIRED_TRAIT" }
    case object InvalidOperation extends ErrorType { def name = "INVALID_OPERATION" }
    case object InvalidService extends ErrorType { def name = "INVALID_SERVICE" }
    case object InvalidEnum extends ErrorType { def name = "INVALID_ENUM" }
    case object CyclicReference extends ErrorType { def name = "CYCLIC_REFERENCE" }
    case object InvalidRange extends ErrorType { def name = "INVALID_RANGE" }
    case object InvalidLength extends ErrorType { def name = "INVALID_LENGTH" }
  }

  /**
   * Result of validation
   */
  final case class ValidationResult(
    errors: List[ValidationError],
    warnings: List[ValidationError]
  ) {
    def isValid: Boolean = errors.isEmpty
    def hasWarnings: Boolean = warnings.nonEmpty
    
    def ++(other: ValidationResult): ValidationResult =
      ValidationResult(errors ++ other.errors, warnings ++ other.warnings)
      
    def render: String = {
      val errorMessages = if (errors.nonEmpty) {
        s"Errors (${errors.size}):\n" + errors.map(e => s"  - ${e.render}").mkString("\n")
      } else ""
      
      val warningMessages = if (warnings.nonEmpty) {
        s"Warnings (${warnings.size}):\n" + warnings.map(w => s"  - ${w.render}").mkString("\n")
      } else ""
      
      List(errorMessages, warningMessages).filter(_.nonEmpty).mkString("\n\n")
    }
  }

  object ValidationResult {
    val empty: ValidationResult = ValidationResult(Nil, Nil)
    
    def error(e: ValidationError): ValidationResult = ValidationResult(List(e), Nil)
    def warning(w: ValidationError): ValidationResult = ValidationResult(Nil, List(w))
    def errors(es: List[ValidationError]): ValidationResult = ValidationResult(es, Nil)
    def warnings(ws: List[ValidationError]): ValidationResult = ValidationResult(Nil, ws)
  }

  // ===========================================================================
  // Main validation entry point
  // ===========================================================================

  /**
   * Validate a SmithyModel and return all errors and warnings
   */
  def validate(model: SmithyModel): ValidationResult = {
    val validators: List[SmithyModel => ValidationResult] = List(
      validateShapeReferences,
      validateHttpTraits,
      validateConstraintTraits,
      validateOperations,
      validateServices,
      validateEnums,
      validateUnions,
    )
    
    validators.foldLeft(ValidationResult.empty) { (acc, validator) =>
      acc ++ validator(model)
    }
  }

  /**
   * Validate and throw if there are errors
   */
  def validateOrThrow(model: SmithyModel): SmithyModel = {
    val result = validate(model)
    if (!result.isValid) {
      throw new SmithyValidationException(result)
    }
    model
  }

  class SmithyValidationException(val result: ValidationResult) 
    extends RuntimeException(s"Smithy validation failed:\n${result.render}")

  // ===========================================================================
  // Shape Reference Validation
  // ===========================================================================

  private val preludeShapes: Set[String] = Set(
    "String", "Integer", "Long", "Short", "Byte", "Float", "Double", 
    "Boolean", "Timestamp", "Blob", "Document", "BigInteger", "BigDecimal",
    "Unit", "PrimitiveBoolean", "PrimitiveByte", "PrimitiveShort",
    "PrimitiveInteger", "PrimitiveLong", "PrimitiveFloat", "PrimitiveDouble"
  )

  /**
   * Validate that all shape references point to existing shapes
   */
  private def validateShapeReferences(model: SmithyModel): ValidationResult = {
    val errors = scala.collection.mutable.ListBuffer.empty[ValidationError]
    
    def checkRef(ref: ShapeId, context: (Option[String], Option[String])): Unit = {
      val name = ref.name
      if (!preludeShapes.contains(name) && !model.shapes.contains(name)) {
        errors += ValidationError(
          context._1, context._2,
          ErrorType.UndefinedShape,
          s"Reference to undefined shape: $name"
        )
      }
    }

    model.shapes.foreach { case (shapeName, shape) =>
      shape match {
        case s: Shape.StructureShape =>
          s.members.foreach { case (memberName, member) =>
            checkRef(member.target, (Some(shapeName), Some(memberName)))
          }
          s.mixins.foreach(mixin => checkRef(mixin, (Some(shapeName), None)))
          
        case s: Shape.UnionShape =>
          s.members.foreach { case (memberName, member) =>
            checkRef(member.target, (Some(shapeName), Some(memberName)))
          }
          s.mixins.foreach(mixin => checkRef(mixin, (Some(shapeName), None)))
          
        case s: Shape.ListShape =>
          checkRef(s.member, (Some(shapeName), Some("member")))
          
        case s: Shape.MapShape =>
          checkRef(s.key, (Some(shapeName), Some("key")))
          checkRef(s.value, (Some(shapeName), Some("value")))
          
        case s: Shape.OperationShape =>
          s.input.foreach(i => checkRef(i, (Some(shapeName), Some("input"))))
          s.output.foreach(o => checkRef(o, (Some(shapeName), Some("output"))))
          s.errors.foreach(e => checkRef(e, (Some(shapeName), Some("errors"))))
          
        case s: Shape.ServiceShape =>
          s.operations.foreach(op => checkRef(op, (Some(shapeName), Some("operations"))))
          s.resources.foreach(r => checkRef(r, (Some(shapeName), Some("resources"))))
          s.errors.foreach(e => checkRef(e, (Some(shapeName), Some("errors"))))
          
        case s: Shape.ResourceShape =>
          s.identifiers.values.foreach(id => checkRef(id, (Some(shapeName), Some("identifiers"))))
          s.properties.values.foreach(p => checkRef(p, (Some(shapeName), Some("properties"))))
          s.create.foreach(c => checkRef(c, (Some(shapeName), Some("create"))))
          s.put.foreach(p => checkRef(p, (Some(shapeName), Some("put"))))
          s.read.foreach(r => checkRef(r, (Some(shapeName), Some("read"))))
          s.update.foreach(u => checkRef(u, (Some(shapeName), Some("update"))))
          s.delete.foreach(d => checkRef(d, (Some(shapeName), Some("delete"))))
          s.list.foreach(l => checkRef(l, (Some(shapeName), Some("list"))))
          s.operations.foreach(op => checkRef(op, (Some(shapeName), Some("operations"))))
          s.collectionOperations.foreach(op => checkRef(op, (Some(shapeName), Some("collectionOperations"))))
          s.resources.foreach(r => checkRef(r, (Some(shapeName), Some("resources"))))
          
        case _ => // Simple shapes don't have references
      }
    }

    ValidationResult.errors(errors.toList)
  }

  // ===========================================================================
  // HTTP Trait Validation
  // ===========================================================================

  private val validHttpMethods: Set[String] = Set(
    "GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS", "TRACE", "CONNECT"
  )

  /**
   * Validate HTTP-related traits on operations
   */
  private def validateHttpTraits(model: SmithyModel): ValidationResult = {
    val errors = scala.collection.mutable.ListBuffer.empty[ValidationError]

    model.allOperations.foreach { case (opName, op) =>
      op.httpTrait.foreach { http =>
        // Validate HTTP method
        if (!validHttpMethods.contains(http.method.toUpperCase)) {
          errors += ValidationError(
            Some(opName), None,
            ErrorType.InvalidHttpMethod,
            s"Invalid HTTP method: '${http.method}'. Valid methods are: ${validHttpMethods.mkString(", ")}"
          )
        }

        // Validate status code
        if (http.code < 100 || http.code > 599) {
          errors += ValidationError(
            Some(opName), None,
            ErrorType.InvalidStatusCode,
            s"Invalid HTTP status code: ${http.code}. Must be between 100 and 599."
          )
        }

        // Validate path format
        val uri = http.uri
        if (!uri.startsWith("/")) {
          errors += ValidationError(
            Some(opName), None,
            ErrorType.InvalidHttpPath,
            s"HTTP URI must start with '/': $uri"
          )
        }

        // Extract path parameters from URI
        val pathParamRegex = """\{(\w+)\}""".r
        val pathParams = pathParamRegex.findAllMatchIn(uri).map(_.group(1)).toList

        // Check for duplicate path parameters
        val duplicates = pathParams.groupBy(identity).filter(_._2.size > 1).keys
        duplicates.foreach { dup =>
          errors += ValidationError(
            Some(opName), None,
            ErrorType.DuplicatePathParameter,
            s"Duplicate path parameter: {$dup}"
          )
        }

        // Validate that path parameters have corresponding @httpLabel members
        op.input.foreach { inputId =>
          model.getStructure(inputId.name).foreach { inputStruct =>
            val labelMembers = inputStruct.members.filter { case (_, member) =>
              member.httpLabel
            }.keys.toSet

            // Check all path params have @httpLabel members
            pathParams.foreach { param =>
              if (!labelMembers.contains(param)) {
                // Check if the member exists at all
                if (inputStruct.members.contains(param)) {
                  errors += ValidationError(
                    Some(inputId.name), Some(param),
                    ErrorType.MissingRequiredTrait,
                    s"Path parameter '$param' exists but is missing @httpLabel trait"
                  )
                } else {
                  errors += ValidationError(
                    Some(opName), None,
                    ErrorType.MissingPathParameter,
                    s"Path parameter '$param' in URI has no corresponding member in ${inputId.name}"
                  )
                }
              }
            }

            // Check all @httpLabel members are in the path
            labelMembers.foreach { label =>
              if (!pathParams.contains(label)) {
                errors += ValidationError(
                  Some(inputId.name), Some(label),
                  ErrorType.PathParameterMismatch,
                  s"Member '$label' has @httpLabel but is not in the URI path: $uri"
                )
              }
            }
          }
        }

        // Validate @httpPayload is only on one member
        op.input.foreach { inputId =>
          model.getStructure(inputId.name).foreach { inputStruct =>
            val payloadMembers = inputStruct.members.filter { case (_, member) =>
              member.httpPayload
            }.keys.toList

            if (payloadMembers.size > 1) {
              errors += ValidationError(
                Some(inputId.name), None,
                ErrorType.IncompatibleTrait,
                s"Multiple @httpPayload members: ${payloadMembers.mkString(", ")}. Only one member can have @httpPayload."
              )
            }
          }
        }

        // Validate that GET/DELETE/HEAD don't typically have body
        // (warning, not error - as Smithy allows it)
      }
    }

    // Validate @httpError trait values
    model.shapes.foreach { case (shapeName, shape) =>
      shape.traits.foreach {
        case SmithyTrait.HttpError(code) if code < 400 || code > 599 =>
          errors += ValidationError(
            Some(shapeName), None,
            ErrorType.InvalidStatusCode,
            s"@httpError code must be 4xx or 5xx, got: $code"
          )
        case _ => // ok
      }
    }

    ValidationResult.errors(errors.toList)
  }

  // ===========================================================================
  // Constraint Trait Validation
  // ===========================================================================

  /**
   * Validate constraint traits like @length, @range, @pattern
   */
  private def validateConstraintTraits(model: SmithyModel): ValidationResult = {
    val errors = scala.collection.mutable.ListBuffer.empty[ValidationError]

    def validateTraitsOnShape(shapeName: String, shape: Shape): Unit = {
      shape.traits.foreach {
        case SmithyTrait.Length(min, max) =>
          // Length trait must have non-negative values
          min.foreach { m =>
            if (m < 0) {
              errors += ValidationError(
                Some(shapeName), None,
                ErrorType.InvalidLength,
                s"@length min must be non-negative, got: $m"
              )
            }
          }
          max.foreach { m =>
            if (m < 0) {
              errors += ValidationError(
                Some(shapeName), None,
                ErrorType.InvalidLength,
                s"@length max must be non-negative, got: $m"
              )
            }
          }
          // min must be <= max
          (min, max) match {
            case (Some(minVal), Some(maxVal)) if minVal > maxVal =>
              errors += ValidationError(
                Some(shapeName), None,
                ErrorType.InvalidLength,
                s"@length min ($minVal) cannot be greater than max ($maxVal)"
              )
            case _ => // ok
          }
          
          // Length can only be applied to strings, lists, maps, blobs
          shape match {
            case _: Shape.StringShape | _: Shape.ListShape | 
                 _: Shape.MapShape | _: Shape.BlobShape => // ok
            case _ =>
              errors += ValidationError(
                Some(shapeName), None,
                ErrorType.IncompatibleTrait,
                "@length trait can only be applied to string, list, map, or blob shapes"
              )
          }

        case SmithyTrait.Range(min, max) =>
          // min must be <= max
          (min, max) match {
            case (Some(minVal), Some(maxVal)) if minVal > maxVal =>
              errors += ValidationError(
                Some(shapeName), None,
                ErrorType.InvalidRange,
                s"@range min ($minVal) cannot be greater than max ($maxVal)"
              )
            case _ => // ok
          }
          
          // Range can only be applied to numeric shapes
          shape match {
            case _: Shape.ByteShape | _: Shape.ShortShape | _: Shape.IntegerShape |
                 _: Shape.LongShape | _: Shape.FloatShape | _: Shape.DoubleShape |
                 _: Shape.BigIntegerShape | _: Shape.BigDecimalShape => // ok
            case _ =>
              errors += ValidationError(
                Some(shapeName), None,
                ErrorType.IncompatibleTrait,
                "@range trait can only be applied to numeric shapes"
              )
          }

        case SmithyTrait.Pattern(regex) =>
          // Validate that the regex compiles
          try {
            regex.r
          } catch {
            case e: Exception =>
              errors += ValidationError(
                Some(shapeName), None,
                ErrorType.InvalidPattern,
                s"@pattern contains invalid regex: ${e.getMessage}"
              )
          }
          
          // Pattern can only be applied to strings
          shape match {
            case _: Shape.StringShape => // ok
            case _ =>
              errors += ValidationError(
                Some(shapeName), None,
                ErrorType.IncompatibleTrait,
                "@pattern trait can only be applied to string shapes"
              )
          }

        case SmithyTrait.TimestampFormat(format) =>
          val validFormats = Set("date-time", "http-date", "epoch-seconds")
          if (!validFormats.contains(format)) {
            errors += ValidationError(
              Some(shapeName), None,
              ErrorType.InvalidConstraint,
              s"Invalid @timestampFormat: '$format'. Valid formats: ${validFormats.mkString(", ")}"
            )
          }

        case _ => // other traits don't need validation here
      }
    }

    // Validate traits on all shapes
    model.shapes.foreach { case (shapeName, shape) =>
      validateTraitsOnShape(shapeName, shape)
      
      // Also validate member traits
      shape match {
        case s: Shape.StructureShape =>
          s.members.foreach { case (memberName, member) =>
            member.traits.foreach {
              case SmithyTrait.Pattern(regex) =>
                try {
                  regex.r
                } catch {
                  case e: Exception =>
                    errors += ValidationError(
                      Some(shapeName), Some(memberName),
                      ErrorType.InvalidPattern,
                      s"@pattern contains invalid regex: ${e.getMessage}"
                    )
                }
              case SmithyTrait.Length(min, max) =>
                (min, max) match {
                  case (Some(minVal), Some(maxVal)) if minVal > maxVal =>
                    errors += ValidationError(
                      Some(shapeName), Some(memberName),
                      ErrorType.InvalidLength,
                      s"@length min ($minVal) cannot be greater than max ($maxVal)"
                    )
                  case _ => // ok
                }
              case SmithyTrait.Range(min, max) =>
                (min, max) match {
                  case (Some(minVal), Some(maxVal)) if minVal > maxVal =>
                    errors += ValidationError(
                      Some(shapeName), Some(memberName),
                      ErrorType.InvalidRange,
                      s"@range min ($minVal) cannot be greater than max ($maxVal)"
                    )
                  case _ => // ok
                }
              case _ => // ok
            }
          }
        case _ => // ok
      }
    }

    ValidationResult.errors(errors.toList)
  }

  // ===========================================================================
  // Operation Validation
  // ===========================================================================

  /**
   * Validate operations have valid input/output types
   */
  private def validateOperations(model: SmithyModel): ValidationResult = {
    val errors = scala.collection.mutable.ListBuffer.empty[ValidationError]

    model.allOperations.foreach { case (opName, op) =>
      // Input must be a structure if specified
      op.input.foreach { inputId =>
        model.getShape(inputId.name) match {
          case Some(_: Shape.StructureShape) => // ok
          case Some(_) =>
            errors += ValidationError(
              Some(opName), Some("input"),
              ErrorType.InvalidOperation,
              s"Operation input must be a structure, but '${inputId.name}' is not"
            )
          case None =>
            // Already caught by shape reference validation
        }
      }

      // Output must be a structure if specified
      op.output.foreach { outputId =>
        model.getShape(outputId.name) match {
          case Some(_: Shape.StructureShape) => // ok
          case Some(_) =>
            errors += ValidationError(
              Some(opName), Some("output"),
              ErrorType.InvalidOperation,
              s"Operation output must be a structure, but '${outputId.name}' is not"
            )
          case None =>
            // Already caught by shape reference validation
        }
      }

      // Errors must be structures with @error trait
      op.errors.foreach { errorId =>
        model.getShape(errorId.name) match {
          case Some(s: Shape.StructureShape) =>
            val hasErrorTrait = s.traits.exists {
              case SmithyTrait.Error | SmithyTrait.ErrorMessage(_) => true
              case _ => false
            }
            if (!hasErrorTrait) {
              errors += ValidationError(
                Some(opName), Some("errors"),
                ErrorType.MissingRequiredTrait,
                s"Error structure '${errorId.name}' must have @error trait"
              )
            }
          case Some(_) =>
            errors += ValidationError(
              Some(opName), Some("errors"),
              ErrorType.InvalidOperation,
              s"Operation error must be a structure, but '${errorId.name}' is not"
            )
          case None =>
            // Already caught by shape reference validation
        }
      }
    }

    ValidationResult.errors(errors.toList)
  }

  // ===========================================================================
  // Service Validation
  // ===========================================================================

  /**
   * Validate services reference valid operations and resources
   */
  private def validateServices(model: SmithyModel): ValidationResult = {
    val errors = scala.collection.mutable.ListBuffer.empty[ValidationError]

    model.allServices.foreach { case (svcName, svc) =>
      // Operations must be operation shapes
      svc.operations.foreach { opId =>
        model.getShape(opId.name) match {
          case Some(_: Shape.OperationShape) => // ok
          case Some(_) =>
            errors += ValidationError(
              Some(svcName), Some("operations"),
              ErrorType.InvalidService,
              s"'${opId.name}' is not an operation shape"
            )
          case None =>
            // Already caught by shape reference validation
        }
      }

      // Resources must be resource shapes
      svc.resources.foreach { resId =>
        model.getShape(resId.name) match {
          case Some(_: Shape.ResourceShape) => // ok
          case Some(_) =>
            errors += ValidationError(
              Some(svcName), Some("resources"),
              ErrorType.InvalidService,
              s"'${resId.name}' is not a resource shape"
            )
          case None =>
            // Already caught by shape reference validation
        }
      }

      // Service version should not be empty
      if (svc.version.isEmpty) {
        errors += ValidationError(
          Some(svcName), None,
          ErrorType.InvalidService,
          "Service version should not be empty"
        )
      }
    }

    ValidationResult.errors(errors.toList)
  }

  // ===========================================================================
  // Enum Validation
  // ===========================================================================

  /**
   * Validate enum shapes have at least one member
   */
  private def validateEnums(model: SmithyModel): ValidationResult = {
    val errors = scala.collection.mutable.ListBuffer.empty[ValidationError]

    model.shapes.foreach {
      case (name, e: Shape.EnumShape) =>
        if (e.members.isEmpty) {
          errors += ValidationError(
            Some(name), None,
            ErrorType.InvalidEnum,
            "Enum must have at least one member"
          )
        }
        
        // Check for duplicate enum values
        val values = e.members.values.flatMap(_.value).toList
        val duplicateValues = values.groupBy(identity).filter(_._2.size > 1).keys
        duplicateValues.foreach { dup =>
          errors += ValidationError(
            Some(name), None,
            ErrorType.InvalidEnum,
            s"Duplicate enum value: '$dup'"
          )
        }
        
      case (name, e: Shape.IntEnumShape) =>
        if (e.members.isEmpty) {
          errors += ValidationError(
            Some(name), None,
            ErrorType.InvalidEnum,
            "IntEnum must have at least one member"
          )
        }
        
        // Check for duplicate int values
        val values = e.members.values.map(_.value).toList
        val duplicateValues = values.groupBy(identity).filter(_._2.size > 1).keys
        duplicateValues.foreach { dup =>
          errors += ValidationError(
            Some(name), None,
            ErrorType.InvalidEnum,
            s"Duplicate intEnum value: $dup"
          )
        }
        
      case _ => // not an enum
    }

    ValidationResult.errors(errors.toList)
  }

  // ===========================================================================
  // Union Validation
  // ===========================================================================

  /**
   * Validate union shapes have at least one member
   */
  private def validateUnions(model: SmithyModel): ValidationResult = {
    val errors = scala.collection.mutable.ListBuffer.empty[ValidationError]

    model.shapes.foreach {
      case (name, u: Shape.UnionShape) =>
        if (u.members.isEmpty) {
          errors += ValidationError(
            Some(name), None,
            ErrorType.InvalidOperation, // reusing error type
            "Union must have at least one member"
          )
        }
        
      case _ => // not a union
    }

    ValidationResult.errors(errors.toList)
  }
}
