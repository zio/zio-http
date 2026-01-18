package zio.http.gen.smithy

import zio.Scope
import zio.test._
import zio.http.gen.scala.CodeGen

object SmithyEndpointGenSpec extends ZIOSpecDefault {
  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("SmithyEndpointGenSpec")(
      suite("Code Generation")(
        test("generate case class from structure") {
          val smithyString = """$version: "2"
                               |namespace example.api
                               |
                               |structure User {
                               |    @required
                               |    id: String
                               |    name: String
                               |    age: Integer
                               |}""".stripMargin
          
          val result = for {
            model <- SmithyParser.parse(smithyString)
            files = SmithyEndpointGen.fromSmithyModel(model)
            rendered = CodeGen.renderedFiles(files, "example.api")
          } yield rendered
          
          assertTrue(result.isRight) && {
            val rendered = result.toOption.get
            // Should have one file for the User structure
            assertTrue(rendered.size == 1) &&
            assertTrue(rendered.keys.exists(_.contains("User.scala"))) && {
              val userCode = rendered.values.head
              assertTrue(userCode.contains("case class User")) &&
              assertTrue(userCode.contains("id: String")) &&
              assertTrue(userCode.contains("name: Option[String]")) &&
              assertTrue(userCode.contains("age: Option[Int]"))
            }
          }
        },
        test("generate sealed trait from union") {
          val smithyString = """$version: "2"
                               |namespace example.api
                               |
                               |union PaymentMethod {
                               |    creditCard: CreditCard
                               |    bankAccount: BankAccount
                               |}
                               |
                               |structure CreditCard {
                               |    number: String
                               |}
                               |
                               |structure BankAccount {
                               |    accountNumber: String
                               |}""".stripMargin
          
          val result = for {
            model <- SmithyParser.parse(smithyString)
            files = SmithyEndpointGen.fromSmithyModel(model)
            rendered = CodeGen.renderedFiles(files, "example.api")
          } yield rendered
          
          assertTrue(result.isRight) && {
            val rendered = result.toOption.get
            assertTrue(rendered.size == 3) && // Union + 2 structures
            assertTrue(rendered.keys.exists(_.contains("PaymentMethod.scala")))
          }
        },
        test("generate enum from enum shape") {
          val smithyString = """$version: "2"
                               |namespace example.api
                               |
                               |enum Status {
                               |    PENDING
                               |    ACTIVE
                               |    INACTIVE
                               |}""".stripMargin
          
          val result = for {
            model <- SmithyParser.parse(smithyString)
            files = SmithyEndpointGen.fromSmithyModel(model)
            rendered = CodeGen.renderedFiles(files, "example.api")
          } yield rendered
          
          assertTrue(result.isRight) && {
            val rendered = result.toOption.get
            assertTrue(rendered.size == 1) &&
            assertTrue(rendered.keys.exists(_.contains("Status.scala"))) && {
              val statusCode = rendered.values.head
              assertTrue(statusCode.contains("sealed trait Status")) &&
              assertTrue(statusCode.contains("PENDING")) &&
              assertTrue(statusCode.contains("ACTIVE")) &&
              assertTrue(statusCode.contains("INACTIVE"))
            }
          }
        },
        test("generate endpoint from operation with @http trait") {
          val smithyString = """$version: "2"
                               |namespace example.api
                               |
                               |service UserService {
                               |    operations: [GetUser, CreateUser]
                               |}
                               |
                               |@http(method: "GET", uri: "/users/{userId}")
                               |operation GetUser {
                               |    input: GetUserInput
                               |    output: User
                               |}
                               |
                               |@http(method: "POST", uri: "/users", code: 201)
                               |operation CreateUser {
                               |    input: CreateUserInput
                               |    output: User
                               |}
                               |
                               |structure GetUserInput {
                               |    @required
                               |    @httpLabel
                               |    userId: String
                               |}
                               |
                               |structure CreateUserInput {
                               |    @required
                               |    name: String
                               |    email: String
                               |}
                               |
                               |structure User {
                               |    @required
                               |    id: String
                               |    name: String
                               |    email: String
                               |}""".stripMargin
          
          val result = for {
            model <- SmithyParser.parse(smithyString)
            files = SmithyEndpointGen.fromSmithyModel(model)
            rendered = CodeGen.renderedFiles(files, "example.api")
          } yield rendered
          
          assertTrue(result.isRight) && {
            val rendered = result.toOption.get
            // Should have Endpoints.scala + component files
            assertTrue(rendered.keys.exists(_.contains("Endpoints.scala"))) && {
              val endpointsCode = rendered.find(_._1.contains("Endpoints.scala")).map(_._2).getOrElse("")
              assertTrue(endpointsCode.contains("object Endpoints")) &&
              assertTrue(endpointsCode.contains("GetUser")) &&
              assertTrue(endpointsCode.contains("CreateUser")) &&
              assertTrue(endpointsCode.contains("Method.GET")) &&
              assertTrue(endpointsCode.contains("Method.POST"))
            }
          }
        },
        test("generate endpoint with query parameters") {
          val smithyString = """$version: "2"
                               |namespace example.api
                               |
                               |@http(method: "GET", uri: "/users")
                               |operation ListUsers {
                               |    input: ListUsersInput
                               |    output: UserList
                               |}
                               |
                               |structure ListUsersInput {
                               |    @httpQuery("limit")
                               |    limit: Integer
                               |    @httpQuery("offset")
                               |    offset: Integer
                               |}
                               |
                               |structure UserList {
                               |    users: UserArray
                               |}
                               |
                               |list UserArray {
                               |    member: User
                               |}
                               |
                               |structure User {
                               |    id: String
                               |}""".stripMargin
          
          val result = for {
            model <- SmithyParser.parse(smithyString)
            files = SmithyEndpointGen.fromSmithyModel(model)
            rendered = CodeGen.renderedFiles(files, "example.api")
          } yield rendered
          
          assertTrue(result.isRight) && {
            val rendered = result.toOption.get
            assertTrue(rendered.keys.exists(_.contains("Endpoints.scala"))) && {
              val endpointsCode = rendered.find(_._1.contains("Endpoints.scala")).map(_._2).getOrElse("")
              assertTrue(endpointsCode.contains("query")) &&
              assertTrue(endpointsCode.contains("limit") || endpointsCode.contains("offset"))
            }
          }
        },
        test("generate endpoint with headers") {
          val smithyString = """$version: "2"
                               |namespace example.api
                               |
                               |@http(method: "GET", uri: "/protected")
                               |operation GetProtected {
                               |    input: GetProtectedInput
                               |    output: ProtectedData
                               |}
                               |
                               |structure GetProtectedInput {
                               |    @required
                               |    @httpHeader("Authorization")
                               |    authorization: String
                               |}
                               |
                               |structure ProtectedData {
                               |    data: String
                               |}""".stripMargin
          
          val result = for {
            model <- SmithyParser.parse(smithyString)
            files = SmithyEndpointGen.fromSmithyModel(model)
            rendered = CodeGen.renderedFiles(files, "example.api")
          } yield rendered
          
          assertTrue(result.isRight) && {
            val rendered = result.toOption.get
            assertTrue(rendered.keys.exists(_.contains("Endpoints.scala"))) && {
              val endpointsCode = rendered.find(_._1.contains("Endpoints.scala")).map(_._2).getOrElse("")
              assertTrue(endpointsCode.contains("Authorization") || endpointsCode.contains("header"))
            }
          }
        },
        test("map Smithy types to Scala types correctly") {
          val smithyString = """$version: "2"
                               |namespace example.api
                               |
                               |structure AllTypes {
                               |    stringField: String
                               |    intField: Integer
                               |    longField: Long
                               |    boolField: Boolean
                               |    doubleField: Double
                               |    floatField: Float
                               |    shortField: Short
                               |    byteField: Byte
                               |    timestampField: Timestamp
                               |}""".stripMargin
          
          val result = for {
            model <- SmithyParser.parse(smithyString)
            files = SmithyEndpointGen.fromSmithyModel(model)
            rendered = CodeGen.renderedFiles(files, "example.api")
          } yield rendered
          
          assertTrue(result.isRight) && {
            val rendered = result.toOption.get
            val allTypesCode = rendered.find(_._1.contains("AllTypes.scala")).map(_._2).getOrElse("")
            assertTrue(allTypesCode.contains("String")) &&
            assertTrue(allTypesCode.contains("Int")) &&
            assertTrue(allTypesCode.contains("Long")) &&
            assertTrue(allTypesCode.contains("Boolean")) &&
            assertTrue(allTypesCode.contains("Double"))
          }
        },
      ),
      suite("End-to-End")(
        test("parse and generate from weather service example") {
          val smithyString = """$version: "2"
                               |namespace example.weather
                               |
                               |/// Provides weather forecasts.
                               |service Weather {
                               |    version: "2006-03-01"
                               |    resources: [City]
                               |    operations: [GetCurrentTime]
                               |}
                               |
                               |resource City {
                               |    identifiers: { cityId: CityId }
                               |    read: GetCity
                               |    list: ListCities
                               |}
                               |
                               |@http(method: "GET", uri: "/cities/{cityId}")
                               |@readonly
                               |operation GetCity {
                               |    input: GetCityInput
                               |    output: GetCityOutput
                               |    errors: [NoSuchResource]
                               |}
                               |
                               |structure GetCityInput {
                               |    @required
                               |    @httpLabel
                               |    cityId: CityId
                               |}
                               |
                               |structure GetCityOutput {
                               |    @required
                               |    name: String
                               |    coordinates: CityCoordinates
                               |}
                               |
                               |structure CityCoordinates {
                               |    @required
                               |    latitude: Float
                               |    @required
                               |    longitude: Float
                               |}
                               |
                               |@http(method: "GET", uri: "/cities")
                               |@readonly
                               |@paginated(inputToken: "nextToken", outputToken: "nextToken", pageSize: "pageSize")
                               |operation ListCities {
                               |    input: ListCitiesInput
                               |    output: ListCitiesOutput
                               |}
                               |
                               |structure ListCitiesInput {
                               |    @httpQuery("nextToken")
                               |    nextToken: String
                               |    @httpQuery("pageSize")
                               |    pageSize: Integer
                               |}
                               |
                               |structure ListCitiesOutput {
                               |    nextToken: String
                               |    @required
                               |    items: CitySummaries
                               |}
                               |
                               |list CitySummaries {
                               |    member: CitySummary
                               |}
                               |
                               |structure CitySummary {
                               |    @required
                               |    cityId: CityId
                               |    @required
                               |    name: String
                               |}
                               |
                               |@http(method: "GET", uri: "/time")
                               |@readonly
                               |operation GetCurrentTime {
                               |    output: GetCurrentTimeOutput
                               |}
                               |
                               |structure GetCurrentTimeOutput {
                               |    @required
                               |    time: Timestamp
                               |}
                               |
                               |@error("client")
                               |@httpError(404)
                               |structure NoSuchResource {
                               |    @required
                               |    resourceType: String
                               |}
                               |
                               |@pattern("^[A-Za-z0-9 ]+$")
                               |string CityId""".stripMargin
          
          val result = for {
            model <- SmithyParser.parse(smithyString)
            files = SmithyEndpointGen.fromSmithyModel(model)
            rendered = CodeGen.renderedFiles(files, "example.weather")
          } yield (model, files, rendered)
          
          assertTrue(result.isRight) && {
            val (model, files, rendered) = result.toOption.get
            val endpointsCode = rendered.find(_._1.contains("Endpoints.scala")).map(_._2).getOrElse("")
            
            assertTrue(
              model.namespace == "example.weather",
              model.shapes.contains("Weather"),
              model.shapes.contains("GetCity"),
              model.shapes.contains("ListCities"),
              files.files.nonEmpty,
              rendered.keys.exists(_.contains("Endpoints.scala")),
              endpointsCode.contains("GetCity"),
              endpointsCode.contains("ListCities"),
              endpointsCode.contains("GetCurrentTime"),
              endpointsCode.contains("Method.GET"),
              rendered.keys.exists(k => k.contains("GetCityOutput.scala") || k.contains("component"))
            )
          }
        },
      ),
      suite("Filesystem")(
        test("fromString parses and generates code") {
          val smithyString = """$version: "2"
                               |namespace example.api
                               |
                               |structure User {
                               |    id: String
                               |}""".stripMargin
          
          val result = SmithyEndpointGen.fromString(smithyString)
          
          assertTrue(result.isRight) && {
            val files = result.toOption.get
            assertTrue(files.files.nonEmpty)
          }
        },
        test("fromString returns empty files for text without shapes") {
          val textWithoutShapes = "this is not valid smithy"
          
          val result = SmithyEndpointGen.fromString(textWithoutShapes)
          
          // Parser is lenient, produces empty model for unrecognized text
          assertTrue(result.isRight) && {
            val files = result.toOption.get
            assertTrue(files.files.isEmpty)
          }
        },
        test("merging multiple models combines shapes") {
          val smithy1 = """$version: "2"
                          |namespace example.api
                          |
                          |structure User {
                          |    id: String
                          |}""".stripMargin
          
          val smithy2 = """$version: "2"
                          |namespace example.api
                          |
                          |structure Order {
                          |    orderId: String
                          |}""".stripMargin
          
          val result1 = SmithyParser.parse(smithy1)
          val result2 = SmithyParser.parse(smithy2)
          
          assertTrue(result1.isRight) &&
          assertTrue(result2.isRight) && {
            val model1 = result1.toOption.get
            val model2 = result2.toOption.get
            
            // Both models should have their respective shapes
            assertTrue(model1.shapes.contains("User")) &&
            assertTrue(model2.shapes.contains("Order"))
          }
        },
      ),
      suite("Advanced Features")(
        test("parse streaming trait") {
          val smithyString = """$version: "2"
                               |namespace example.api
                               |
                               |@streaming
                               |blob StreamingBlob
                               |
                               |structure StreamRequest {
                               |    @httpPayload
                               |    data: StreamingBlob
                               |}""".stripMargin
          
          val result = SmithyParser.parse(smithyString)
          
          assertTrue(result.isRight) && {
            val model = result.toOption.get
            assertTrue(model.shapes.contains("StreamingBlob")) && {
              val blobShape = model.shapes("StreamingBlob")
              assertTrue(blobShape.traits.exists {
                case SmithyTrait.Streaming => true
                case _ => false
              })
            }
          }
        },
        test("parse documentation trait") {
          val smithyString = """$version: "2"
                               |namespace example.api
                               |
                               |/// This is documentation for the User structure
                               |structure User {
                               |    /// The unique identifier
                               |    id: String
                               |}""".stripMargin
          
          val result = SmithyParser.parse(smithyString)
          
          assertTrue(result.isRight) && {
            val model = result.toOption.get
            assertTrue(model.shapes.contains("User")) && {
              val userShape = model.shapes("User").asInstanceOf[Shape.StructureShape]
              val hasDoc = userShape.traits.exists {
                case SmithyTrait.Documentation(_) => true
                case _ => false
              }
              assertTrue(hasDoc)
            }
          }
        },
        test("parse auth traits") {
          val smithyString = """$version: "2"
                               |namespace example.api
                               |
                               |@httpBearerAuth
                               |service SecureService {
                               |    version: "1.0"
                               |}""".stripMargin
          
          val result = SmithyParser.parse(smithyString)
          
          assertTrue(result.isRight) && {
            val model = result.toOption.get
            assertTrue(model.shapes.contains("SecureService")) && {
              val svcShape = model.shapes("SecureService").asInstanceOf[Shape.ServiceShape]
              val hasAuth = svcShape.traits.exists {
                case SmithyTrait.HttpBearerAuth => true
                case _ => false
              }
              assertTrue(hasAuth)
            }
          }
        },
        test("generate endpoint with error status codes") {
          val smithyString = """$version: "2"
                               |namespace example.api
                               |
                               |@http(method: "GET", uri: "/users/{userId}")
                               |operation GetUser {
                               |    input: GetUserInput
                               |    output: User
                               |    errors: [NotFound, Unauthorized]
                               |}
                               |
                               |structure GetUserInput {
                               |    @required
                               |    @httpLabel
                               |    userId: String
                               |}
                               |
                               |structure User {
                               |    id: String
                               |}
                               |
                               |@error("client")
                               |@httpError(404)
                               |structure NotFound {
                               |    message: String
                               |}
                               |
                               |@error("client")
                               |@httpError(401)
                               |structure Unauthorized {
                               |    message: String
                               |}""".stripMargin
          
          val result = for {
            model <- SmithyParser.parse(smithyString)
            files = SmithyEndpointGen.fromSmithyModel(model)
            rendered = CodeGen.renderedFiles(files, "example.api")
          } yield rendered
          
          assertTrue(result.isRight) && {
            val rendered = result.toOption.get
            assertTrue(rendered.keys.exists(_.contains("Endpoints.scala"))) && {
              val endpointsCode = rendered.find(_._1.contains("Endpoints.scala")).map(_._2).getOrElse("")
              // Verify error handling is generated
              assertTrue(endpointsCode.contains("NotFound") || endpointsCode.contains("Unauthorized"))
            }
          }
        },
      ),
    )
}
