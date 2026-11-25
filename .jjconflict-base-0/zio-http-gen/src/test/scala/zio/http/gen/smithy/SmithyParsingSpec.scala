package zio.http.gen.smithy

import scala.io.Source

import zio.Scope
import zio.test._

object SmithyParsingSpec extends ZIOSpecDefault {
  private def loadResource(name: String): String           = {
    val stream = getClass.getResourceAsStream(s"/smithy/$name")
    Source.fromInputStream(stream).mkString
  }
  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("SmithyParsingSpec")(
      suite("Full IDL Parsing")(
        test("parse simple service") {
          val smithyString = """$version: "2"
                               |namespace example.weather
                               |
                               |service Weather {
                               |    version: "2006-03-01"
                               |}""".stripMargin
          val result       = SmithyParser.parse(smithyString)
          assertTrue(result.isRight) &&
          assertTrue(result.toOption.get.namespace == "example.weather") &&
          assertTrue(result.toOption.get.version == "2") &&
          assertTrue(result.toOption.get.shapes.contains("Weather"))
        },
        test("parse service with resource") {
          val smithyString = """$version: "2"
                               |namespace example.weather
                               |
                               |/// Provides weather forecasts.
                               |service Weather {
                               |    version: "2006-03-01"
                               |    resources: [
                               |        City
                               |    ]
                               |}
                               |
                               |resource City {
                               |    identifiers: { cityId: CityId }
                               |    read: GetCity
                               |    list: ListCities
                               |}
                               |
                               |@pattern("^[A-Za-z0-9 ]+$")
                               |string CityId""".stripMargin
          val result       = SmithyParser.parse(smithyString)
          assertTrue(result.isRight) &&
          assertTrue(result.toOption.get.shapes.contains("Weather")) &&
          assertTrue(result.toOption.get.shapes.contains("City")) &&
          assertTrue(result.toOption.get.shapes.contains("CityId"))
        },
        test("parse operation with HTTP trait") {
          val smithyString = """$version: "2"
                               |namespace example.api
                               |
                               |@http(method: "GET", uri: "/users/{userId}")
                               |operation GetUser {
                               |    input: GetUserInput
                               |    output: GetUserOutput
                               |}
                               |
                               |structure GetUserInput {
                               |    @required
                               |    @httpLabel
                               |    userId: String
                               |}
                               |
                               |structure GetUserOutput {
                               |    name: String
                               |    email: String
                               |}""".stripMargin
          val result       = SmithyParser.parse(smithyString)
          assertTrue(result.isRight) && {
            val model = result.toOption.get
            val op    = model.getOperation("GetUser")
            assertTrue(op.isDefined) &&
            assertTrue(op.get.httpTrait.isDefined) &&
            assertTrue(op.get.httpTrait.get.method == "GET") &&
            assertTrue(op.get.httpTrait.get.uri == "/users/{userId}")
          }
        },
        test("parse structure with members") {
          val smithyString = """$version: "2"
                               |namespace example.api
                               |
                               |structure Person {
                               |    @required
                               |    name: String
                               |    age: Integer
                               |    @httpHeader("X-Request-Id")
                               |    requestId: String
                               |}""".stripMargin
          val result       = SmithyParser.parse(smithyString)
          assertTrue(result.isRight) && {
            val model  = result.toOption.get
            val struct = model.getStructure("Person")
            assertTrue(struct.isDefined) &&
            assertTrue(struct.get.members.contains("name")) &&
            assertTrue(struct.get.members("name").isRequired) &&
            assertTrue(struct.get.members.contains("age")) &&
            assertTrue(struct.get.members.contains("requestId")) &&
            assertTrue(struct.get.members("requestId").httpHeader == Some("X-Request-Id"))
          }
        },
        test("parse list shape") {
          val smithyString = """$version: "2"
                               |namespace example.api
                               |
                               |list StringList {
                               |    member: String
                               |}""".stripMargin
          val result       = SmithyParser.parse(smithyString)
          assertTrue(result.isRight) && {
            val model     = result.toOption.get
            val listShape = model.shapes.get("StringList")
            assertTrue(listShape.isDefined) &&
            assertTrue(listShape.get.isInstanceOf[Shape.ListShape]) &&
            assertTrue(listShape.get.asInstanceOf[Shape.ListShape].member.name == "String")
          }
        },
        test("parse map shape") {
          val smithyString = """$version: "2"
                               |namespace example.api
                               |
                               |map StringMap {
                               |    key: String
                               |    value: Integer
                               |}""".stripMargin
          val result       = SmithyParser.parse(smithyString)
          assertTrue(result.isRight) && {
            val model    = result.toOption.get
            val mapShape = model.shapes.get("StringMap")
            assertTrue(mapShape.isDefined) &&
            assertTrue(mapShape.get.isInstanceOf[Shape.MapShape]) &&
            assertTrue(mapShape.get.asInstanceOf[Shape.MapShape].key.name == "String") &&
            assertTrue(mapShape.get.asInstanceOf[Shape.MapShape].value.name == "Integer")
          }
        },
        test("parse union shape") {
          val smithyString = """$version: "2"
                               |namespace example.api
                               |
                               |union IntOrString {
                               |    intValue: Integer
                               |    stringValue: String
                               |}""".stripMargin
          val result       = SmithyParser.parse(smithyString)
          assertTrue(result.isRight) && {
            val model      = result.toOption.get
            val unionShape = model.shapes.get("IntOrString")
            assertTrue(unionShape.isDefined) &&
            assertTrue(unionShape.get.isInstanceOf[Shape.UnionShape]) &&
            assertTrue(unionShape.get.asInstanceOf[Shape.UnionShape].members.contains("intValue")) &&
            assertTrue(unionShape.get.asInstanceOf[Shape.UnionShape].members.contains("stringValue"))
          }
        },
        test("parse enum shape") {
          val smithyString = """$version: "2"
                               |namespace example.api
                               |
                               |enum Status {
                               |    PENDING
                               |    ACTIVE = "active"
                               |    COMPLETED
                               |}""".stripMargin
          val result       = SmithyParser.parse(smithyString)
          assertTrue(result.isRight) && {
            val model     = result.toOption.get
            val enumShape = model.shapes.get("Status")
            assertTrue(enumShape.isDefined) &&
            assertTrue(enumShape.get.isInstanceOf[Shape.EnumShape])
          }
        },
        test("parse simple shapes") {
          val smithyString = """$version: "2"
                               |namespace example.api
                               |
                               |string MyString
                               |integer MyInteger
                               |long MyLong
                               |boolean MyBoolean
                               |timestamp MyTimestamp
                               |blob MyBlob""".stripMargin
          val result       = SmithyParser.parse(smithyString)
          assertTrue(result.isRight) && {
            val model = result.toOption.get
            assertTrue(model.shapes.get("MyString").exists(_.isInstanceOf[Shape.StringShape])) &&
            assertTrue(model.shapes.get("MyInteger").exists(_.isInstanceOf[Shape.IntegerShape])) &&
            assertTrue(model.shapes.get("MyLong").exists(_.isInstanceOf[Shape.LongShape])) &&
            assertTrue(model.shapes.get("MyBoolean").exists(_.isInstanceOf[Shape.BooleanShape])) &&
            assertTrue(model.shapes.get("MyTimestamp").exists(_.isInstanceOf[Shape.TimestampShape])) &&
            assertTrue(model.shapes.get("MyBlob").exists(_.isInstanceOf[Shape.BlobShape]))
          }
        },
        test("parse operation with errors") {
          val smithyString = """$version: "2"
                               |namespace example.api
                               |
                               |@http(method: "DELETE", uri: "/items/{itemId}")
                               |operation DeleteItem {
                               |    input: DeleteItemInput
                               |    output: DeleteItemOutput
                               |    errors: [NotFoundError, ValidationError]
                               |}
                               |
                               |structure DeleteItemInput {
                               |    @required
                               |    @httpLabel
                               |    itemId: String
                               |}
                               |
                               |structure DeleteItemOutput {}
                               |
                               |@error("client")
                               |structure NotFoundError {
                               |    message: String
                               |}
                               |
                               |@error("client")
                               |structure ValidationError {
                               |    message: String
                               |}""".stripMargin
          val result       = SmithyParser.parse(smithyString)
          assertTrue(result.isRight) && {
            val model = result.toOption.get
            val op    = model.getOperation("DeleteItem")
            assertTrue(op.isDefined) &&
            assertTrue(op.get.errors.length == 2) &&
            assertTrue(op.get.errors.exists(_.name == "NotFoundError")) &&
            assertTrue(op.get.errors.exists(_.name == "ValidationError"))
          }
        },
        test("parse http traits on members") {
          val smithyString = """$version: "2"
                               |namespace example.api
                               |
                               |structure SearchRequest {
                               |    @httpQuery("q")
                               |    query: String
                               |    
                               |    @httpQuery("limit")
                               |    limit: Integer
                               |    
                               |    @httpHeader("X-Api-Key")
                               |    apiKey: String
                               |    
                               |    @httpPayload
                               |    body: String
                               |}""".stripMargin
          val result       = SmithyParser.parse(smithyString)
          assertTrue(result.isRight) && {
            val model  = result.toOption.get
            val struct = model.getStructure("SearchRequest")
            assertTrue(struct.isDefined) && {
              val members = struct.get.members
              assertTrue(members("query").httpQuery == Some("q")) &&
              assertTrue(members("limit").httpQuery == Some("limit")) &&
              assertTrue(members("apiKey").httpHeader == Some("X-Api-Key")) &&
              assertTrue(members("body").httpPayload)
            }
          }
        },
      ),
      suite("Real World .smithy Files")(
        test("parse pet_store.smithy") {
          val smithyString = loadResource("pet_store.smithy")
          val result       = SmithyParser.parse(smithyString)
          assertTrue(result.isRight) && {
            val model = result.toOption.get
            assertTrue(model.namespace == "example.petstore") &&
            assertTrue(model.shapes.contains("PetStore")) &&
            assertTrue(model.shapes.contains("Pet")) &&
            assertTrue(model.shapes.contains("Species")) &&
            assertTrue(model.shapes.get("Species").exists(_.isInstanceOf[Shape.EnumShape])) &&
            assertTrue(model.getOperation("GetPet").isDefined) &&
            assertTrue(model.getOperation("ListPets").isDefined) &&
            assertTrue(model.getOperation("CreatePet").isDefined) &&
            assertTrue(model.getOperation("DeletePet").isDefined) &&
            assertTrue(model.getOperation("GetPet").get.httpTrait.get.method == "GET") &&
            assertTrue(model.getOperation("GetPet").get.httpTrait.get.uri == "/pets/{petId}") &&
            assertTrue(model.getOperation("CreatePet").get.httpTrait.get.method == "POST") &&
            assertTrue(model.getOperation("DeletePet").get.httpTrait.get.method == "DELETE") &&
            assertTrue(model.getStructure("Pet").isDefined) &&
            assertTrue(model.getStructure("Pet").get.members.size == 6) &&
            assertTrue(model.getStructure("Pet").get.members("id").isRequired) &&
            assertTrue(model.getStructure("Pet").get.members("name").isRequired) &&
            assertTrue(model.getStructure("Pet").get.members("species").isRequired) &&
            assertTrue(model.getStructure("Pet").get.members("createdAt").isRequired) &&
            assertTrue(!model.getStructure("Pet").get.members("age").isRequired)
          }
        },
        test("parse auth_service.smithy") {
          val smithyString = loadResource("auth_service.smithy")
          val result       = SmithyParser.parse(smithyString)
          assertTrue(result.isRight) && {
            val model = result.toOption.get
            assertTrue(model.namespace == "example.auth") &&
            assertTrue(model.shapes.contains("AuthService")) &&
            assertTrue(model.shapes.contains("User")) &&
            assertTrue(model.shapes.contains("Role")) &&
            assertTrue(model.shapes.contains("UserStatus")) &&
            assertTrue(model.getOperation("Login").isDefined) &&
            assertTrue(model.getOperation("Logout").isDefined) &&
            assertTrue(model.getOperation("GetCurrentUser").isDefined) &&
            assertTrue(model.getOperation("RefreshToken").isDefined) &&
            assertTrue(model.getOperation("Login").get.httpTrait.get.uri == "/auth/login") &&
            assertTrue(model.getOperation("Login").get.errors.exists(_.name == "InvalidCredentials")) &&
            assertTrue(model.getOperation("Login").get.errors.exists(_.name == "AccountLocked")) &&
            assertTrue(model.getStructure("User").isDefined) &&
            assertTrue(model.getStructure("User").get.members.contains("id")) &&
            assertTrue(model.getStructure("User").get.members.contains("email")) &&
            assertTrue(model.getStructure("User").get.members.contains("roles")) &&
            assertTrue(model.getStructure("LoginInput").get.members("username").isRequired) &&
            assertTrue(model.getStructure("LoginInput").get.members("password").isRequired)
          }
        },
        test("parse order_service.smithy") {
          val smithyString = loadResource("order_service.smithy")
          val result       = SmithyParser.parse(smithyString)
          assertTrue(result.isRight) && {
            val model = result.toOption.get
            assertTrue(model.namespace == "example.orders") &&
            assertTrue(model.shapes.contains("OrderService")) &&
            assertTrue(model.shapes.contains("Order")) &&
            assertTrue(model.shapes.contains("OrderItem")) &&
            assertTrue(model.shapes.contains("Money")) &&
            assertTrue(model.shapes.contains("Address")) &&
            assertTrue(model.shapes.contains("OrderStatus")) &&
            assertTrue(model.shapes.contains("Currency")) &&
            assertTrue(model.getOperation("GetOrder").isDefined) &&
            assertTrue(model.getOperation("CreateOrder").isDefined) &&
            assertTrue(model.getOperation("UpdateOrder").isDefined) &&
            assertTrue(model.getOperation("CancelOrder").isDefined) &&
            assertTrue(model.getOperation("ListOrders").isDefined) &&
            assertTrue(model.getOperation("SearchOrders").isDefined) &&
            assertTrue(model.getOperation("AddOrderItem").isDefined) &&
            assertTrue(model.getOperation("RemoveOrderItem").isDefined) &&
            assertTrue(
              model.getOperation("RemoveOrderItem").get.httpTrait.get.uri == "/orders/{orderId}/items/{itemId}",
            ) &&
            assertTrue(model.getStructure("OrderData").isDefined) &&
            assertTrue(model.getStructure("OrderData").get.members.size == 14) &&
            assertTrue(model.getStructure("Money").isDefined) &&
            assertTrue(model.getStructure("Money").get.members.contains("amount")) &&
            assertTrue(model.getStructure("Money").get.members.contains("currency")) &&
            assertTrue(model.getStructure("Address").isDefined) &&
            assertTrue(model.getStructure("Address").get.members.size == 6)
          }
        },
        test("parse weather_service.smithy") {
          val smithyString = loadResource("weather_service.smithy")
          val result       = SmithyParser.parse(smithyString)
          assertTrue(result.isRight) && {
            val model = result.toOption.get
            assertTrue(model.namespace == "example.weather") &&
            assertTrue(model.shapes.contains("Weather"))
          }
        },
      ),
      suite("Legacy API Compatibility")(
        test("parse service via legacy API") {
          val smithyString = """$version: "2"
                               |namespace example.weather
                               |
                               |service Weather {
                               |    version: "2006-03-01"
                               |}""".stripMargin
          val parsed       = Smithy.parse(smithyString)
          assertTrue(parsed.services.contains("Weather")) &&
          assertTrue(parsed.services("Weather").version == "2006-03-01")
        },
        test("parse service and resource via legacy API") {
          val smithyString = """$version: "2"
                               |namespace example.weather
                               |
                               |/// Provides weather forecasts.
                               |service Weather {
                               |    version: "2006-03-01"
                               |    resources: [
                               |        City
                               |    ]
                               |}
                               |
                               |resource City {
                               |    identifiers: { cityId: CityId }
                               |    read: GetCity
                               |    list: ListCities
                               |}
                               |
                               |@pattern("^[A-Za-z0-9 ]+$")
                               |string CityId""".stripMargin
          val parsed       = Smithy.parse(smithyString)
          assertTrue(parsed.services.contains("Weather")) &&
          assertTrue(parsed.resources.contains("City"))
        },
        test("parse operation via legacy API") {
          val smithyString = """$version: "2"
                               |namespace example.api
                               |
                               |@http(method: "GET", uri: "/users/{userId}")
                               |operation GetUser {
                               |    input: GetUserInput
                               |    output: GetUserOutput
                               |}
                               |
                               |structure GetUserInput {
                               |    @required
                               |    @httpLabel
                               |    userId: String
                               |}
                               |
                               |structure GetUserOutput {
                               |    name: String
                               |}""".stripMargin
          val parsed       = Smithy.parse(smithyString)
          assertTrue(parsed.operations.contains("GetUser")) &&
          assertTrue(parsed.operations("GetUser").input == "GetUserInput") &&
          assertTrue(parsed.operations("GetUser").output == "GetUserOutput")
        },
      ),
    )
}
