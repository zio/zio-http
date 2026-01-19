package zio.http.gen.smithy

import zio.Scope
import zio.test._

object SmithyParsingSpec extends ZIOSpecDefault {
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
