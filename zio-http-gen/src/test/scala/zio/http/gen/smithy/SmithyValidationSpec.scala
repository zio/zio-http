package zio.http.gen.smithy

import zio.Scope
import zio.test._

object SmithyValidationSpec extends ZIOSpecDefault {

  override def spec: Spec[TestEnvironment with Scope, Any] = suite("SmithyValidationSpec")(
    suite("Shape Reference Validation")(
      test("detects undefined shape references in structure members") {
        val smithy = """
                       |$version: "2"
                       |namespace test
                       |
                       |structure User {
                       |    id: String
                       |    profile: NonExistentProfile
                       |}
        """.stripMargin

        val result = for {
          model <- SmithyParser.parse(smithy)
        } yield SmithyValidation.validate(model)

        assertTrue(result.isRight) &&
        assertTrue(!result.toOption.get.isValid) &&
        assertTrue(
          result.toOption.get.errors.exists(e =>
            e.errorType == SmithyValidation.ErrorType.UndefinedShape &&
              e.message.contains("NonExistentProfile"),
          ),
        )
      },
      test("detects undefined shape references in list members") {
        val smithy = """
                       |$version: "2"
                       |namespace test
                       |
                       |list Items {
                       |    member: NonExistentItem
                       |}
        """.stripMargin

        val result = for {
          model <- SmithyParser.parse(smithy)
        } yield SmithyValidation.validate(model)

        assertTrue(result.isRight) &&
        assertTrue(!result.toOption.get.isValid) &&
        assertTrue(result.toOption.get.errors.exists(_.errorType == SmithyValidation.ErrorType.UndefinedShape))
      },
      test("detects undefined shape references in map shapes") {
        val smithy = """
                       |$version: "2"
                       |namespace test
                       |
                       |map UserMap {
                       |    key: String
                       |    value: NonExistentUser
                       |}
        """.stripMargin

        val result = for {
          model <- SmithyParser.parse(smithy)
        } yield SmithyValidation.validate(model)

        assertTrue(result.isRight) &&
        assertTrue(!result.toOption.get.isValid) &&
        assertTrue(result.toOption.get.errors.exists(_.errorType == SmithyValidation.ErrorType.UndefinedShape))
      },
      test("allows prelude types") {
        val smithy = """
                       |$version: "2"
                       |namespace test
                       |
                       |structure User {
                       |    id: String
                       |    age: Integer
                       |    active: Boolean
                       |    score: Double
                       |    created: Timestamp
                       |}
        """.stripMargin

        val result = for {
          model <- SmithyParser.parse(smithy)
        } yield SmithyValidation.validate(model)

        assertTrue(result.isRight) &&
        assertTrue(result.toOption.get.isValid)
      },
      test("detects undefined operation input/output") {
        val smithy = """
                       |$version: "2"
                       |namespace test
                       |
                       |operation GetUser {
                       |    input: NonExistentInput
                       |    output: NonExistentOutput
                       |}
        """.stripMargin

        val result = for {
          model <- SmithyParser.parse(smithy)
        } yield SmithyValidation.validate(model)

        assertTrue(result.isRight) &&
        assertTrue(!result.toOption.get.isValid) &&
        assertTrue(result.toOption.get.errors.count(_.errorType == SmithyValidation.ErrorType.UndefinedShape) >= 2)
      },
    ),
    suite("HTTP Trait Validation")(
      test("detects invalid HTTP methods") {
        val smithy = """
                       |$version: "2"
                       |namespace test
                       |
                       |@http(method: "INVALID", uri: "/users")
                       |operation ListUsers {
                       |    output: ListUsersOutput
                       |}
                       |
                       |structure ListUsersOutput {}
        """.stripMargin

        val result = for {
          model <- SmithyParser.parse(smithy)
        } yield SmithyValidation.validate(model)

        assertTrue(result.isRight) &&
        assertTrue(!result.toOption.get.isValid) &&
        assertTrue(
          result.toOption.get.errors.exists(e =>
            e.errorType == SmithyValidation.ErrorType.InvalidHttpMethod &&
              e.message.contains("INVALID"),
          ),
        )
      },
      test("detects invalid HTTP status codes") {
        val smithy = """
                       |$version: "2"
                       |namespace test
                       |
                       |@http(method: "GET", uri: "/users", code: 999)
                       |operation ListUsers {
                       |    output: ListUsersOutput
                       |}
                       |
                       |structure ListUsersOutput {}
        """.stripMargin

        val result = for {
          model <- SmithyParser.parse(smithy)
        } yield SmithyValidation.validate(model)

        assertTrue(result.isRight) &&
        assertTrue(!result.toOption.get.isValid) &&
        assertTrue(result.toOption.get.errors.exists(_.errorType == SmithyValidation.ErrorType.InvalidStatusCode))
      },
      test("detects invalid @httpError status codes") {
        val smithy = """
                       |$version: "2"
                       |namespace test
                       |
                       |@error("client")
                       |@httpError(200)
                       |structure MyError {
                       |    message: String
                       |}
        """.stripMargin

        val result = for {
          model <- SmithyParser.parse(smithy)
        } yield SmithyValidation.validate(model)

        assertTrue(result.isRight) &&
        assertTrue(!result.toOption.get.isValid) &&
        assertTrue(
          result.toOption.get.errors.exists(e =>
            e.errorType == SmithyValidation.ErrorType.InvalidStatusCode &&
              e.message.contains("4xx or 5xx"),
          ),
        )
      },
      test("detects URI not starting with /") {
        val smithy = """
                       |$version: "2"
                       |namespace test
                       |
                       |@http(method: "GET", uri: "users")
                       |operation ListUsers {
                       |    output: ListUsersOutput
                       |}
                       |
                       |structure ListUsersOutput {}
        """.stripMargin

        val result = for {
          model <- SmithyParser.parse(smithy)
        } yield SmithyValidation.validate(model)

        assertTrue(result.isRight) &&
        assertTrue(!result.toOption.get.isValid) &&
        assertTrue(result.toOption.get.errors.exists(_.errorType == SmithyValidation.ErrorType.InvalidHttpPath))
      },
      test("detects missing @httpLabel for path parameters") {
        val smithy = """
                       |$version: "2"
                       |namespace test
                       |
                       |@http(method: "GET", uri: "/users/{userId}")
                       |operation GetUser {
                       |    input: GetUserInput
                       |    output: GetUserOutput
                       |}
                       |
                       |structure GetUserInput {
                       |    userId: String
                       |}
                       |
                       |structure GetUserOutput {}
        """.stripMargin

        val result = for {
          model <- SmithyParser.parse(smithy)
        } yield SmithyValidation.validate(model)

        assertTrue(result.isRight) &&
        assertTrue(!result.toOption.get.isValid) &&
        assertTrue(result.toOption.get.errors.exists(_.errorType == SmithyValidation.ErrorType.MissingRequiredTrait))
      },
      test("detects missing path parameter member") {
        val smithy = """
                       |$version: "2"
                       |namespace test
                       |
                       |@http(method: "GET", uri: "/users/{userId}")
                       |operation GetUser {
                       |    input: GetUserInput
                       |    output: GetUserOutput
                       |}
                       |
                       |structure GetUserInput {
                       |    name: String
                       |}
                       |
                       |structure GetUserOutput {}
        """.stripMargin

        val result = for {
          model <- SmithyParser.parse(smithy)
        } yield SmithyValidation.validate(model)

        assertTrue(result.isRight) &&
        assertTrue(!result.toOption.get.isValid) &&
        assertTrue(result.toOption.get.errors.exists(_.errorType == SmithyValidation.ErrorType.MissingPathParameter))
      },
      test("detects @httpLabel member not in path") {
        val smithy = """
                       |$version: "2"
                       |namespace test
                       |
                       |@http(method: "GET", uri: "/users")
                       |operation ListUsers {
                       |    input: ListUsersInput
                       |    output: ListUsersOutput
                       |}
                       |
                       |structure ListUsersInput {
                       |    @httpLabel
                       |    @required
                       |    userId: String
                       |}
                       |
                       |structure ListUsersOutput {}
        """.stripMargin

        val result = for {
          model <- SmithyParser.parse(smithy)
        } yield SmithyValidation.validate(model)

        assertTrue(result.isRight) &&
        assertTrue(!result.toOption.get.isValid) &&
        assertTrue(result.toOption.get.errors.exists(_.errorType == SmithyValidation.ErrorType.PathParameterMismatch))
      },
      test("detects duplicate path parameters") {
        val smithy = """
                       |$version: "2"
                       |namespace test
                       |
                       |@http(method: "GET", uri: "/users/{id}/posts/{id}")
                       |operation GetUserPost {
                       |    input: GetUserPostInput
                       |    output: GetUserPostOutput
                       |}
                       |
                       |structure GetUserPostInput {
                       |    @httpLabel
                       |    @required
                       |    id: String
                       |}
                       |
                       |structure GetUserPostOutput {}
        """.stripMargin

        val result = for {
          model <- SmithyParser.parse(smithy)
        } yield SmithyValidation.validate(model)

        assertTrue(result.isRight) &&
        assertTrue(!result.toOption.get.isValid) &&
        assertTrue(result.toOption.get.errors.exists(_.errorType == SmithyValidation.ErrorType.DuplicatePathParameter))
      },
      test("detects multiple @httpPayload members") {
        val smithy = """
                       |$version: "2"
                       |namespace test
                       |
                       |@http(method: "POST", uri: "/users")
                       |operation CreateUser {
                       |    input: CreateUserInput
                       |    output: CreateUserOutput
                       |}
                       |
                       |structure CreateUserInput {
                       |    @httpPayload
                       |    body1: String
                       |    @httpPayload
                       |    body2: String
                       |}
                       |
                       |structure CreateUserOutput {}
        """.stripMargin

        val result = for {
          model <- SmithyParser.parse(smithy)
        } yield SmithyValidation.validate(model)

        assertTrue(result.isRight) &&
        assertTrue(!result.toOption.get.isValid) &&
        assertTrue(
          result.toOption.get.errors.exists(e =>
            e.errorType == SmithyValidation.ErrorType.IncompatibleTrait &&
              e.message.contains("httpPayload"),
          ),
        )
      },
      test("valid HTTP operation passes validation") {
        val smithy = """
                       |$version: "2"
                       |namespace test
                       |
                       |@http(method: "GET", uri: "/users/{userId}")
                       |operation GetUser {
                       |    input: GetUserInput
                       |    output: GetUserOutput
                       |}
                       |
                       |structure GetUserInput {
                       |    @httpLabel
                       |    @required
                       |    userId: String
                       |    
                       |    @httpQuery("expand")
                       |    expand: Boolean
                       |}
                       |
                       |structure GetUserOutput {
                       |    name: String
                       |}
        """.stripMargin

        val result = for {
          model <- SmithyParser.parse(smithy)
        } yield SmithyValidation.validate(model)

        assertTrue(result.isRight) &&
        assertTrue(result.toOption.get.isValid)
      },
    ),
    suite("Constraint Trait Validation")(
      test("detects invalid @length with min > max") {
        val smithy = """
                       |$version: "2"
                       |namespace test
                       |
                       |@length(min: 10, max: 5)
                       |string Username
        """.stripMargin

        val result = for {
          model <- SmithyParser.parse(smithy)
        } yield SmithyValidation.validate(model)

        assertTrue(result.isRight) &&
        assertTrue(!result.toOption.get.isValid) &&
        assertTrue(
          result.toOption.get.errors.exists(e =>
            e.errorType == SmithyValidation.ErrorType.InvalidLength &&
              e.message.contains("min") && e.message.contains("max"),
          ),
        )
      },
      test("detects negative @length values") {
        val smithy = """
                       |$version: "2"
                       |namespace test
                       |
                       |@length(min: -5)
                       |string Username
        """.stripMargin

        val result = for {
          model <- SmithyParser.parse(smithy)
        } yield SmithyValidation.validate(model)

        assertTrue(result.isRight) &&
        assertTrue(!result.toOption.get.isValid) &&
        assertTrue(
          result.toOption.get.errors.exists(e =>
            e.errorType == SmithyValidation.ErrorType.InvalidLength &&
              e.message.contains("non-negative"),
          ),
        )
      },
      test("detects @length on incompatible shape") {
        val smithy = """
                       |$version: "2"
                       |namespace test
                       |
                       |@length(min: 1)
                       |integer Age
        """.stripMargin

        val result = for {
          model <- SmithyParser.parse(smithy)
        } yield SmithyValidation.validate(model)

        assertTrue(result.isRight) &&
        assertTrue(!result.toOption.get.isValid) &&
        assertTrue(
          result.toOption.get.errors.exists(e =>
            e.errorType == SmithyValidation.ErrorType.IncompatibleTrait &&
              e.message.contains("length"),
          ),
        )
      },
      test("detects invalid @range with min > max") {
        val smithy = """
                       |$version: "2"
                       |namespace test
                       |
                       |@range(min: 100, max: 0)
                       |integer Age
        """.stripMargin

        val result = for {
          model <- SmithyParser.parse(smithy)
        } yield SmithyValidation.validate(model)

        assertTrue(result.isRight) &&
        assertTrue(!result.toOption.get.isValid) &&
        assertTrue(result.toOption.get.errors.exists(_.errorType == SmithyValidation.ErrorType.InvalidRange))
      },
      test("detects @range on incompatible shape") {
        val smithy = """
                       |$version: "2"
                       |namespace test
                       |
                       |@range(min: 1, max: 100)
                       |string Username
        """.stripMargin

        val result = for {
          model <- SmithyParser.parse(smithy)
        } yield SmithyValidation.validate(model)

        assertTrue(result.isRight) &&
        assertTrue(!result.toOption.get.isValid) &&
        assertTrue(
          result.toOption.get.errors.exists(e =>
            e.errorType == SmithyValidation.ErrorType.IncompatibleTrait &&
              e.message.contains("range"),
          ),
        )
      },
      test("detects invalid regex in @pattern") {
        val smithy = """
                       |$version: "2"
                       |namespace test
                       |
                       |@pattern("[invalid")
                       |string Username
        """.stripMargin

        val result = for {
          model <- SmithyParser.parse(smithy)
        } yield SmithyValidation.validate(model)

        assertTrue(result.isRight) &&
        assertTrue(!result.toOption.get.isValid) &&
        assertTrue(
          result.toOption.get.errors.exists(e =>
            e.errorType == SmithyValidation.ErrorType.InvalidPattern &&
              e.message.contains("invalid regex"),
          ),
        )
      },
      test("detects @pattern on incompatible shape") {
        val smithy = """
                       |$version: "2"
                       |namespace test
                       |
                       |@pattern("^[a-z]+$")
                       |integer Count
        """.stripMargin

        val result = for {
          model <- SmithyParser.parse(smithy)
        } yield SmithyValidation.validate(model)

        assertTrue(result.isRight) &&
        assertTrue(!result.toOption.get.isValid) &&
        assertTrue(
          result.toOption.get.errors.exists(e =>
            e.errorType == SmithyValidation.ErrorType.IncompatibleTrait &&
              e.message.contains("pattern"),
          ),
        )
      },
      test("detects invalid @timestampFormat") {
        val smithy = """
                       |$version: "2"
                       |namespace test
                       |
                       |@timestampFormat("invalid-format")
                       |timestamp MyTimestamp
        """.stripMargin

        val result = for {
          model <- SmithyParser.parse(smithy)
        } yield SmithyValidation.validate(model)

        assertTrue(result.isRight) &&
        assertTrue(!result.toOption.get.isValid) &&
        assertTrue(
          result.toOption.get.errors.exists(e =>
            e.errorType == SmithyValidation.ErrorType.InvalidConstraint &&
              e.message.contains("timestampFormat"),
          ),
        )
      },
      test("valid constraint traits pass validation") {
        val smithy = """
                       |$version: "2"
                       |namespace test
                       |
                       |@length(min: 1, max: 100)
                       |@pattern("^[a-zA-Z0-9]+$")
                       |string Username
                       |
                       |@range(min: 0, max: 150)
                       |integer Age
                       |
                       |@timestampFormat("date-time")
                       |timestamp CreatedAt
        """.stripMargin

        val result = for {
          model <- SmithyParser.parse(smithy)
        } yield SmithyValidation.validate(model)

        assertTrue(result.isRight) &&
        assertTrue(result.toOption.get.isValid)
      },
    ),
    suite("Operation Validation")(
      test("detects non-structure operation input") {
        val smithy = """
                       |$version: "2"
                       |namespace test
                       |
                       |operation GetUser {
                       |    input: Username
                       |    output: GetUserOutput
                       |}
                       |
                       |string Username
                       |structure GetUserOutput {}
        """.stripMargin

        val result = for {
          model <- SmithyParser.parse(smithy)
        } yield SmithyValidation.validate(model)

        assertTrue(result.isRight) &&
        assertTrue(!result.toOption.get.isValid) &&
        assertTrue(
          result.toOption.get.errors.exists(e =>
            e.errorType == SmithyValidation.ErrorType.InvalidOperation &&
              e.message.contains("input must be a structure"),
          ),
        )
      },
      test("detects non-structure operation output") {
        val smithy = """
                       |$version: "2"
                       |namespace test
                       |
                       |operation GetUser {
                       |    input: GetUserInput
                       |    output: Username
                       |}
                       |
                       |structure GetUserInput {}
                       |string Username
        """.stripMargin

        val result = for {
          model <- SmithyParser.parse(smithy)
        } yield SmithyValidation.validate(model)

        assertTrue(result.isRight) &&
        assertTrue(!result.toOption.get.isValid) &&
        assertTrue(
          result.toOption.get.errors.exists(e =>
            e.errorType == SmithyValidation.ErrorType.InvalidOperation &&
              e.message.contains("output must be a structure"),
          ),
        )
      },
      test("detects error structure without @error trait") {
        val smithy = """
                       |$version: "2"
                       |namespace test
                       |
                       |operation GetUser {
                       |    input: GetUserInput
                       |    output: GetUserOutput
                       |    errors: [NotFoundError]
                       |}
                       |
                       |structure GetUserInput {}
                       |structure GetUserOutput {}
                       |structure NotFoundError {
                       |    message: String
                       |}
        """.stripMargin

        val result = for {
          model <- SmithyParser.parse(smithy)
        } yield SmithyValidation.validate(model)

        assertTrue(result.isRight) &&
        assertTrue(!result.toOption.get.isValid) &&
        assertTrue(
          result.toOption.get.errors.exists(e =>
            e.errorType == SmithyValidation.ErrorType.MissingRequiredTrait &&
              e.message.contains("@error trait"),
          ),
        )
      },
      test("valid operation with error trait passes validation") {
        val smithy = """
                       |$version: "2"
                       |namespace test
                       |
                       |operation GetUser {
                       |    input: GetUserInput
                       |    output: GetUserOutput
                       |    errors: [NotFoundError]
                       |}
                       |
                       |structure GetUserInput {}
                       |structure GetUserOutput {}
                       |
                       |@error("client")
                       |structure NotFoundError {
                       |    message: String
                       |}
        """.stripMargin

        val result = for {
          model <- SmithyParser.parse(smithy)
        } yield SmithyValidation.validate(model)

        assertTrue(result.isRight) &&
        assertTrue(result.toOption.get.isValid)
      },
    ),
    suite("Service Validation")(
      test("detects non-operation in service operations") {
        val smithy = """
                       |$version: "2"
                       |namespace test
                       |
                       |service MyService {
                       |    version: "1.0"
                       |    operations: [NotAnOperation]
                       |}
                       |
                       |structure NotAnOperation {}
        """.stripMargin

        val result = for {
          model <- SmithyParser.parse(smithy)
        } yield SmithyValidation.validate(model)

        assertTrue(result.isRight) &&
        assertTrue(!result.toOption.get.isValid) &&
        assertTrue(
          result.toOption.get.errors.exists(e =>
            e.errorType == SmithyValidation.ErrorType.InvalidService &&
              e.message.contains("not an operation shape"),
          ),
        )
      },
      test("detects empty service version") {
        val smithy = """
                       |$version: "2"
                       |namespace test
                       |
                       |service MyService {
                       |    version: ""
                       |}
        """.stripMargin

        val result = for {
          model <- SmithyParser.parse(smithy)
        } yield SmithyValidation.validate(model)

        assertTrue(result.isRight) &&
        assertTrue(!result.toOption.get.isValid) &&
        assertTrue(
          result.toOption.get.errors.exists(e =>
            e.errorType == SmithyValidation.ErrorType.InvalidService &&
              e.message.contains("version should not be empty"),
          ),
        )
      },
    ),
    suite("Enum Validation")(
      test("detects empty enum") {
        val model = SmithyModel(
          version = "2",
          namespace = "test",
          shapes = Map("EmptyEnum" -> Shape.EnumShape(Map.empty)),
        )

        val validation = SmithyValidation.validate(model)

        assertTrue(!validation.isValid) &&
        assertTrue(
          validation.errors.exists(e =>
            e.errorType == SmithyValidation.ErrorType.InvalidEnum &&
              e.message.contains("at least one member"),
          ),
        )
      },
      test("detects duplicate enum values") {
        val model = SmithyModel(
          version = "2",
          namespace = "test",
          shapes = Map(
            "Status" -> Shape.EnumShape(
              Map(
                "ACTIVE"   -> Shape.EnumMember(Some("active")),
                "ENABLED"  -> Shape.EnumMember(Some("active")),
                "DISABLED" -> Shape.EnumMember(Some("disabled")),
              ),
            ),
          ),
        )

        val validation = SmithyValidation.validate(model)

        assertTrue(!validation.isValid) &&
        assertTrue(
          validation.errors.exists(e =>
            e.errorType == SmithyValidation.ErrorType.InvalidEnum &&
              e.message.contains("Duplicate enum value"),
          ),
        )
      },
    ),
    suite("Union Validation")(
      test("detects empty union") {
        val model = SmithyModel(
          version = "2",
          namespace = "test",
          shapes = Map("EmptyUnion" -> Shape.UnionShape(Map.empty)),
        )

        val validation = SmithyValidation.validate(model)

        assertTrue(!validation.isValid) &&
        assertTrue(validation.errors.exists(_.message.contains("at least one member")))
      },
    ),
    suite("ValidationResult")(
      test("render produces readable output") {
        val result = SmithyValidation.ValidationResult(
          errors = List(
            SmithyValidation.ValidationError(
              Some("User"),
              Some("profile"),
              SmithyValidation.ErrorType.UndefinedShape,
              "Reference to undefined shape: Profile",
            ),
          ),
          warnings = Nil,
        )

        val rendered = result.render
        assertTrue(
          rendered.contains("User.profile") &&
            rendered.contains("UNDEFINED_SHAPE") &&
            rendered.contains("Profile"),
        )
      },
      test("isValid returns true for empty errors") {
        val result = SmithyValidation.ValidationResult(Nil, Nil)
        assertTrue(result.isValid)
      },
      test("isValid returns false when errors present") {
        val result = SmithyValidation.ValidationResult(
          errors = List(
            SmithyValidation.ValidationError(None, None, SmithyValidation.ErrorType.UndefinedShape, "test"),
          ),
          warnings = Nil,
        )
        assertTrue(!result.isValid)
      },
      test("++ combines results correctly") {
        val r1 = SmithyValidation.ValidationResult.error(
          SmithyValidation.ValidationError(Some("A"), None, SmithyValidation.ErrorType.UndefinedShape, "error1"),
        )
        val r2 = SmithyValidation.ValidationResult.warning(
          SmithyValidation.ValidationError(Some("B"), None, SmithyValidation.ErrorType.InvalidConstraint, "warning1"),
        )

        val combined = r1 ++ r2
        assertTrue(
          combined.errors.size == 1 &&
            combined.warnings.size == 1 &&
            !combined.isValid &&
            combined.hasWarnings,
        )
      },
    ),
    suite("validateOrThrow")(
      test("throws exception on invalid model") {
        val model = SmithyModel(
          version = "2",
          namespace = "test",
          shapes = Map("EmptyUnion" -> Shape.UnionShape(Map.empty)),
        )

        val threw =
          try {
            SmithyValidation.validateOrThrow(model)
            false
          } catch {
            case _: SmithyValidation.SmithyValidationException => true
            case _: Throwable                                  => false
          }

        assertTrue(threw)
      },
      test("returns model on valid model") {
        val model = SmithyModel(
          version = "2",
          namespace = "test",
          shapes = Map(
            "User" -> Shape.StructureShape(
              members = Map("id" -> Member("id", ShapeId("String"))),
            ),
          ),
        )

        val result = SmithyValidation.validateOrThrow(model)
        assertTrue(result == model)
      },
    ),
    suite("Full Model Validation")(
      test("validates a complete valid model") {
        val smithy = """
                       |$version: "2"
                       |namespace example.api
                       |
                       |service UserService {
                       |    version: "1.0"
                       |    operations: [GetUser, CreateUser]
                       |}
                       |
                       |@http(method: "GET", uri: "/users/{userId}")
                       |operation GetUser {
                       |    input: GetUserInput
                       |    output: User
                       |    errors: [NotFoundError]
                       |}
                       |
                       |@http(method: "POST", uri: "/users")
                       |operation CreateUser {
                       |    input: CreateUserInput
                       |    output: User
                       |}
                       |
                       |structure GetUserInput {
                       |    @httpLabel
                       |    @required
                       |    userId: String
                       |}
                       |
                       |structure CreateUserInput {
                       |    @required
                       |    name: String
                       |    
                       |    @length(min: 5, max: 100)
                       |    email: String
                       |    
                       |    @range(min: 0, max: 150)
                       |    age: Integer
                       |}
                       |
                       |structure User {
                       |    @required
                       |    id: String
                       |    name: String
                       |    email: String
                       |    age: Integer
                       |}
                       |
                       |@error("client")
                       |@httpError(404)
                       |structure NotFoundError {
                       |    @required
                       |    message: String
                       |}
        """.stripMargin

        val result = for {
          model <- SmithyParser.parse(smithy)
        } yield SmithyValidation.validate(model)

        assertTrue(result.isRight) &&
        assertTrue(result.toOption.get.isValid)
      },
    ),
  )
}
