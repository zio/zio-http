package zio.http.gen.openapi

import java.nio.file._

import zio._
import zio.test._

import zio.http._
import zio.http.codec.HeaderCodec
import zio.http.codec.HttpCodec.{query, queryInt}
import zio.http.endpoint._
import zio.http.endpoint.openapi.JsonSchema.SchemaStyle.Inline
import zio.http.endpoint.openapi.{OpenAPI, OpenAPIGen}
import zio.http.gen.model._
import zio.http.gen.scala.Code

object EndpointGenSpec extends ZIOSpecDefault {
  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("EndpointGenSpec")(
      suite("file gen spec")(
        test("right package and file name") {
          val openAPI   = OpenAPI.empty.path(
            OpenAPI.Path.fromString("/api/v1/users").get,
            OpenAPI.PathItem.empty.addGet(
              OpenAPI.Operation(
                summary = None,
                externalDocs = None,
                operationId = None,
                requestBody = None,
                description = None,
              ),
            ),
          )
          val scala     = EndpointGen.fromOpenAPI(openAPI)
          val filePath  = Paths.get("/api/v1", "Users.scala")
          val pkgPath   = List("api", "v1")
          val firstFile = scala.files.head
          assertTrue(firstFile.pkgPath == pkgPath, firstFile.path.mkString("/", "/", "") == filePath.toString)
        },
        test("right package and file name with path parameters") {
          val endpoint  = Endpoint(Method.GET / "api" / "v1" / "users" / int("userId"))
          val openAPI   = OpenAPIGen.fromEndpoints(endpoint)
          val scala     = EndpointGen.fromOpenAPI(openAPI)
          val filePath  = Paths.get("/api/v1/users", "UserId.scala")
          val pkgPath   = List("api", "v1", "users")
          val firstFile = scala.files.head
          assertTrue(firstFile.pkgPath == pkgPath, firstFile.path.mkString("/", "/", "") == filePath.toString)
        },
      ),
      suite("endpoint gen spec")(
        test("empty request and response") {
          val endpoint = Endpoint(Method.GET / "api" / "v1" / "users")
          val openAPI  = OpenAPIGen.fromEndpoints(endpoint)
          val scala    = EndpointGen.fromOpenAPI(openAPI)
          val expected = Code.File(
            List("api", "v1", "Users.scala"),
            pkgPath = List("api", "v1"),
            imports = List(Code.Import.FromBase(path = "component._")),
            objects = List(
              Code.Object(
                "Users",
                Map(
                  Code.Field("get") -> Code.EndpointCode(
                    Method.GET,
                    Code.PathPatternCode(segments =
                      List(Code.PathSegmentCode("api"), Code.PathSegmentCode("v1"), Code.PathSegmentCode("users")),
                    ),
                    queryParamsCode = Set.empty,
                    headersCode = Code.HeadersCode.empty,
                    inCode = Code.InCode("Unit"),
                    outCodes = Nil,
                    errorsCode = Nil,
                  ),
                ),
              ),
            ),
            Nil,
            Nil,
          )
          assertTrue(scala.files.head == expected)
        },
        test("empty request and response with int path parameter") {
          val endpoint = Endpoint(Method.GET / "api" / "v1" / "users" / int("userId"))
          val openAPI  = OpenAPIGen.fromEndpoints(endpoint)
          val scala    = EndpointGen.fromOpenAPI(openAPI)
          val expected = Code.File(
            List("api", "v1", "users", "UserId.scala"),
            pkgPath = List("api", "v1", "users"),
            imports = List(Code.Import.FromBase(path = "component._")),
            objects = List(
              Code.Object(
                "UserId",
                Map(
                  Code.Field("get") -> Code.EndpointCode(
                    Method.GET,
                    Code.PathPatternCode(segments =
                      List(
                        Code.PathSegmentCode("api"),
                        Code.PathSegmentCode("v1"),
                        Code.PathSegmentCode("users"),
                        Code.PathSegmentCode("userId", Code.CodecType.Int),
                      ),
                    ),
                    queryParamsCode = Set.empty,
                    headersCode = Code.HeadersCode.empty,
                    inCode = Code.InCode("Unit"),
                    outCodes = Nil,
                    errorsCode = Nil,
                  ),
                ),
              ),
            ),
            caseClasses = Nil,
            enums = Nil,
          )
          assertTrue(scala.files.head == expected)
        },
        test("empty request and response with string path parameter") {
          val endpoint = Endpoint(Method.GET / "api" / "v1" / "users" / string("userId"))
          val openAPI  = OpenAPIGen.fromEndpoints(endpoint)
          val scala    = EndpointGen.fromOpenAPI(openAPI)
          val expected = Code.File(
            List("api", "v1", "users", "UserId.scala"),
            pkgPath = List("api", "v1", "users"),
            imports = List(Code.Import.FromBase(path = "component._")),
            objects = List(
              Code.Object(
                "UserId",
                Map(
                  Code.Field("get") ->
                    Code.EndpointCode(
                      Method.GET,
                      Code.PathPatternCode(segments =
                        List(
                          Code.PathSegmentCode("api"),
                          Code.PathSegmentCode("v1"),
                          Code.PathSegmentCode("users"),
                          Code.PathSegmentCode("userId", Code.CodecType.String),
                        ),
                      ),
                      queryParamsCode = Set.empty,
                      headersCode = Code.HeadersCode.empty,
                      inCode = Code.InCode("Unit"),
                      outCodes = Nil,
                      errorsCode = Nil,
                    ),
                ),
              ),
            ),
            caseClasses = Nil,
            enums = Nil,
          )
          assertTrue(scala.files.head == expected)
        },
        test("empty request and response with long path parameter") {
          val endpoint = Endpoint(Method.GET / "api" / "v1" / "users" / long("userId"))
          val openAPI  = OpenAPIGen.fromEndpoints(endpoint)
          val scala    = EndpointGen.fromOpenAPI(openAPI)
          val expected = Code.File(
            List("api", "v1", "users", "UserId.scala"),
            pkgPath = List("api", "v1", "users"),
            imports = List(Code.Import.FromBase(path = "component._")),
            objects = List(
              Code.Object(
                "UserId",
                Map(
                  Code.Field("get") ->
                    Code.EndpointCode(
                      Method.GET,
                      Code.PathPatternCode(segments =
                        List(
                          Code.PathSegmentCode("api"),
                          Code.PathSegmentCode("v1"),
                          Code.PathSegmentCode("users"),
                          Code.PathSegmentCode("userId", Code.CodecType.Long),
                        ),
                      ),
                      queryParamsCode = Set.empty,
                      headersCode = Code.HeadersCode.empty,
                      inCode = Code.InCode("Unit"),
                      outCodes = Nil,
                      errorsCode = Nil,
                    ),
                ),
              ),
            ),
            caseClasses = Nil,
            enums = Nil,
          )
          assertTrue(scala.files.head == expected)
        },
        test("empty request and response with uuid path parameter") {
          val endpoint = Endpoint(Method.GET / "api" / "v1" / "users" / uuid("userId"))
          val openAPI  = OpenAPIGen.fromEndpoints(endpoint)
          val scala    = EndpointGen.fromOpenAPI(openAPI)
          val expected = Code.File(
            List("api", "v1", "users", "UserId.scala"),
            pkgPath = List("api", "v1", "users"),
            imports = List(Code.Import.FromBase(path = "component._")),
            objects = List(
              Code.Object(
                "UserId",
                Map(
                  Code.Field("get") ->
                    Code.EndpointCode(
                      Method.GET,
                      Code.PathPatternCode(segments =
                        List(
                          Code.PathSegmentCode("api"),
                          Code.PathSegmentCode("v1"),
                          Code.PathSegmentCode("users"),
                          Code.PathSegmentCode("userId", Code.CodecType.UUID),
                        ),
                      ),
                      queryParamsCode = Set.empty,
                      headersCode = Code.HeadersCode.empty,
                      inCode = Code.InCode("Unit"),
                      outCodes = Nil,
                      errorsCode = Nil,
                    ),
                ),
              ),
            ),
            caseClasses = Nil,
            enums = Nil,
          )
          assertTrue(scala.files.head == expected)
        },
        test("empty request and response with boolean path parameter") {
          val endpoint = Endpoint(Method.GET / "api" / "v1" / "users" / boolean("userId"))
          val openAPI  = OpenAPIGen.fromEndpoints(endpoint)
          val scala    = EndpointGen.fromOpenAPI(openAPI)
          val expected = Code.File(
            List("api", "v1", "users", "UserId.scala"),
            pkgPath = List("api", "v1", "users"),
            imports = List(Code.Import.FromBase(path = "component._")),
            objects = List(
              Code.Object(
                "UserId",
                Map(
                  Code.Field("get") ->
                    Code.EndpointCode(
                      Method.GET,
                      Code.PathPatternCode(segments =
                        List(
                          Code.PathSegmentCode("api"),
                          Code.PathSegmentCode("v1"),
                          Code.PathSegmentCode("users"),
                          Code.PathSegmentCode("userId", Code.CodecType.Boolean),
                        ),
                      ),
                      queryParamsCode = Set.empty,
                      headersCode = Code.HeadersCode.empty,
                      inCode = Code.InCode("Unit"),
                      outCodes = Nil,
                      errorsCode = Nil,
                    ),
                ),
              ),
            ),
            caseClasses = Nil,
            enums = Nil,
          )
          assertTrue(scala.files.head == expected)
        },
        test("empty request and response with accept header") {
          val endpoint = Endpoint(Method.GET / "api" / "v1" / "users").header(HeaderCodec.accept)
          val openAPI  = OpenAPIGen.fromEndpoints(endpoint)
          val scala    = EndpointGen.fromOpenAPI(openAPI)
          val expected = Code.File(
            List("api", "v1", "Users.scala"),
            pkgPath = List("api", "v1"),
            imports = List(Code.Import.FromBase(path = "component._")),
            objects = List(
              Code.Object(
                "Users",
                Map(
                  Code.Field("get") -> Code.EndpointCode(
                    Method.GET,
                    Code.PathPatternCode(segments =
                      List(Code.PathSegmentCode("api"), Code.PathSegmentCode("v1"), Code.PathSegmentCode("users")),
                    ),
                    queryParamsCode = Set.empty,
                    headersCode = Code.HeadersCode(List(Code.HeaderCode("accept"))),
                    inCode = Code.InCode("Unit"),
                    outCodes = Nil,
                    errorsCode = Nil,
                  ),
                ),
              ),
            ),
            caseClasses = Nil,
            enums = Nil,
          )
          assertTrue(scala.files.head == expected)
        },
        test("empty request and response with accept and content-type headers") {
          val endpoint = Endpoint(Method.GET / "api" / "v1" / "users")
            .header(HeaderCodec.accept)
            .header(HeaderCodec.contentType)
          val openAPI  = OpenAPIGen.fromEndpoints(endpoint)
          val scala    = EndpointGen.fromOpenAPI(openAPI)
          val expected = Code.File(
            List("api", "v1", "Users.scala"),
            pkgPath = List("api", "v1"),
            imports = List(Code.Import.FromBase(path = "component._")),
            objects = List(
              Code.Object(
                "Users",
                Map(
                  Code.Field("get") -> Code.EndpointCode(
                    Method.GET,
                    Code.PathPatternCode(segments =
                      List(Code.PathSegmentCode("api"), Code.PathSegmentCode("v1"), Code.PathSegmentCode("users")),
                    ),
                    queryParamsCode = Set.empty,
                    headersCode = Code.HeadersCode(List(Code.HeaderCode("accept"), Code.HeaderCode("content-type"))),
                    inCode = Code.InCode("Unit"),
                    outCodes = Nil,
                    errorsCode = Nil,
                  ),
                ),
              ),
            ),
            caseClasses = Nil,
            enums = Nil,
          )
          assertTrue(scala.files.head == expected)
        },
        test("empty request and response with accept and content-type headers and query parameters") {
          val endpoint = Endpoint(Method.GET / "api" / "v1" / "users")
            .header(HeaderCodec.accept)
            .header(HeaderCodec.contentType)
            .query(queryInt("limit"))
            .query(query("name"))
          val openAPI  = OpenAPIGen.fromEndpoints(endpoint)
          val scala    = EndpointGen.fromOpenAPI(openAPI)
          val expected = Code.File(
            List("api", "v1", "Users.scala"),
            pkgPath = List("api", "v1"),
            imports = List(Code.Import.FromBase(path = "component._")),
            objects = List(
              Code.Object(
                "Users",
                Map(
                  Code.Field("get") -> Code.EndpointCode(
                    Method.GET,
                    Code.PathPatternCode(segments =
                      List(Code.PathSegmentCode("api"), Code.PathSegmentCode("v1"), Code.PathSegmentCode("users")),
                    ),
                    queryParamsCode = Set(
                      Code.QueryParamCode("limit", Code.CodecType.Int),
                      Code.QueryParamCode("name", Code.CodecType.String),
                    ),
                    headersCode = Code.HeadersCode(List(Code.HeaderCode("accept"), Code.HeaderCode("content-type"))),
                    inCode = Code.InCode("Unit"),
                    outCodes = Nil,
                    errorsCode = Nil,
                  ),
                ),
              ),
            ),
            caseClasses = Nil,
            enums = Nil,
          )
          assertTrue(scala.files.head == expected)
        },
        test(
          "empty request and response with accept and content-type headers and query parameters and path parameters",
        ) {
          val endpoint = Endpoint(Method.GET / "api" / "v1" / "users" / int("userId"))
            .header(HeaderCodec.accept)
            .header(HeaderCodec.contentType)
            .query(queryInt("limit"))
            .query(query("name"))
          val openAPI  = OpenAPIGen.fromEndpoints(endpoint)
          val scala    = EndpointGen.fromOpenAPI(openAPI)
          val expected = Code.File(
            List("api", "v1", "users", "UserId.scala"),
            pkgPath = List("api", "v1", "users"),
            imports = List(Code.Import.FromBase(path = "component._")),
            objects = List(
              Code.Object(
                "UserId",
                Map(
                  Code.Field("get") -> Code.EndpointCode(
                    Method.GET,
                    Code.PathPatternCode(segments =
                      List(
                        Code.PathSegmentCode("api"),
                        Code.PathSegmentCode("v1"),
                        Code.PathSegmentCode("users"),
                        Code.PathSegmentCode("userId", Code.CodecType.Int),
                      ),
                    ),
                    queryParamsCode = Set(
                      Code.QueryParamCode("limit", Code.CodecType.Int),
                      Code.QueryParamCode("name", Code.CodecType.String),
                    ),
                    headersCode = Code.HeadersCode(List(Code.HeaderCode("accept"), Code.HeaderCode("content-type"))),
                    inCode = Code.InCode("Unit"),
                    outCodes = Nil,
                    errorsCode = Nil,
                  ),
                ),
              ),
            ),
            caseClasses = Nil,
            enums = Nil,
          )
          assertTrue(scala.files.head == expected)
        },
        test("request body and empty response") {
          val endpoint = Endpoint(Method.POST / "api" / "v1" / "users").in[User]
          val openAPI  = OpenAPIGen.fromEndpoints(endpoint)
          val scala    = EndpointGen.fromOpenAPI(openAPI)
          val expected = Code.File(
            List("api", "v1", "Users.scala"),
            pkgPath = List("api", "v1"),
            imports = List(Code.Import.FromBase(path = "component._")),
            objects = List(
              Code.Object(
                "Users",
                Map(
                  Code.Field("post") -> Code.EndpointCode(
                    Method.POST,
                    Code.PathPatternCode(segments =
                      List(Code.PathSegmentCode("api"), Code.PathSegmentCode("v1"), Code.PathSegmentCode("users")),
                    ),
                    queryParamsCode = Set.empty,
                    headersCode = Code.HeadersCode.empty,
                    inCode = Code.InCode("User"),
                    outCodes = Nil,
                    errorsCode = Nil,
                  ),
                ),
              ),
            ),
            caseClasses = Nil,
            enums = Nil,
          )
          assertTrue(scala.files.head == expected)
        },
        test("request body and empty response with int path parameter") {
          val endpoint = Endpoint(Method.POST / "api" / "v1" / "users" / int("userId")).in[User]
          val openAPI  = OpenAPIGen.fromEndpoints(endpoint)
          val scala    = EndpointGen.fromOpenAPI(openAPI)
          val expected = Code.File(
            List("api", "v1", "users", "UserId.scala"),
            pkgPath = List("api", "v1", "users"),
            imports = List(Code.Import.FromBase(path = "component._")),
            objects = List(
              Code.Object(
                "UserId",
                Map(
                  Code.Field("post") -> Code.EndpointCode(
                    Method.POST,
                    Code.PathPatternCode(segments =
                      List(
                        Code.PathSegmentCode("api"),
                        Code.PathSegmentCode("v1"),
                        Code.PathSegmentCode("users"),
                        Code.PathSegmentCode("userId", Code.CodecType.Int),
                      ),
                    ),
                    queryParamsCode = Set.empty,
                    headersCode = Code.HeadersCode.empty,
                    inCode = Code.InCode("User"),
                    outCodes = Nil,
                    errorsCode = Nil,
                  ),
                ),
              ),
            ),
            caseClasses = Nil,
            enums = Nil,
          )
          assertTrue(scala.files.head == expected)
        },
        test("request body and empty response with path parameter and query parameters") {
          val endpoint = Endpoint(Method.POST / "api" / "v1" / "users" / int("userId"))
            .in[User]
            .query(queryInt("limit"))
            .query(query("name"))
          val openAPI  = OpenAPIGen.fromEndpoints(endpoint)
          val scala    = EndpointGen.fromOpenAPI(openAPI)
          val expected = Code.File(
            List("api", "v1", "users", "UserId.scala"),
            pkgPath = List("api", "v1", "users"),
            imports = List(Code.Import.FromBase(path = "component._")),
            objects = List(
              Code.Object(
                "UserId",
                Map(
                  Code.Field("post") -> Code.EndpointCode(
                    Method.POST,
                    Code.PathPatternCode(segments =
                      List(
                        Code.PathSegmentCode("api"),
                        Code.PathSegmentCode("v1"),
                        Code.PathSegmentCode("users"),
                        Code.PathSegmentCode("userId", Code.CodecType.Int),
                      ),
                    ),
                    queryParamsCode = Set(
                      Code.QueryParamCode("limit", Code.CodecType.Int),
                      Code.QueryParamCode("name", Code.CodecType.String),
                    ),
                    headersCode = Code.HeadersCode.empty,
                    inCode = Code.InCode("User"),
                    outCodes = Nil,
                    errorsCode = Nil,
                  ),
                ),
              ),
            ),
            caseClasses = Nil,
            enums = Nil,
          )
          assertTrue(scala.files.head == expected)
        },
        test("request body and empty response with path parameter and query parameters and headers") {
          val endpoint = Endpoint(Method.POST / "api" / "v1" / "users" / int("userId"))
            .in[User]
            .query(queryInt("limit"))
            .query(query("name"))
            .header(HeaderCodec.accept)
            .header(HeaderCodec.contentType)
          val openAPI  = OpenAPIGen.fromEndpoints(endpoint)
          val scala    = EndpointGen.fromOpenAPI(openAPI)
          val expected = Code.File(
            List("api", "v1", "users", "UserId.scala"),
            pkgPath = List("api", "v1", "users"),
            imports = List(Code.Import.FromBase(path = "component._")),
            objects = List(
              Code.Object(
                "UserId",
                Map(
                  Code.Field("post") -> Code.EndpointCode(
                    Method.POST,
                    Code.PathPatternCode(segments =
                      List(
                        Code.PathSegmentCode("api"),
                        Code.PathSegmentCode("v1"),
                        Code.PathSegmentCode("users"),
                        Code.PathSegmentCode("userId", Code.CodecType.Int),
                      ),
                    ),
                    queryParamsCode = Set(
                      Code.QueryParamCode("limit", Code.CodecType.Int),
                      Code.QueryParamCode("name", Code.CodecType.String),
                    ),
                    headersCode = Code.HeadersCode(List(Code.HeaderCode("accept"), Code.HeaderCode("content-type"))),
                    inCode = Code.InCode("User"),
                    outCodes = Nil,
                    errorsCode = Nil,
                  ),
                ),
              ),
            ),
            caseClasses = Nil,
            enums = Nil,
          )
          assertTrue(scala.files.head == expected)
        },
        test("response and empty request") {
          val endpoint = Endpoint(Method.GET / "api" / "v1" / "users").out[User]
          val openAPI  = OpenAPIGen.fromEndpoints(endpoint)
          val scala    = EndpointGen.fromOpenAPI(openAPI)
          val expected = Code.File(
            List("api", "v1", "Users.scala"),
            pkgPath = List("api", "v1"),
            imports = List(Code.Import.FromBase(path = "component._")),
            objects = List(
              Code.Object(
                "Users",
                Map(
                  Code.Field("get") -> Code.EndpointCode(
                    Method.GET,
                    Code.PathPatternCode(segments =
                      List(Code.PathSegmentCode("api"), Code.PathSegmentCode("v1"), Code.PathSegmentCode("users")),
                    ),
                    queryParamsCode = Set.empty,
                    headersCode = Code.HeadersCode.empty,
                    inCode = Code.InCode("Unit"),
                    outCodes = List(Code.OutCode.json("User", Status.Ok)),
                    errorsCode = Nil,
                  ),
                ),
              ),
            ),
            caseClasses = Nil,
            enums = Nil,
          )
          assertTrue(scala.files.head == expected)
        },
        test("seq request") {
          val endpoint = Endpoint(Method.GET / "api" / "v1" / "users").in[Chunk[User]]
          val openAPI  = OpenAPIGen.fromEndpoints(endpoint)
          val scala    = EndpointGen.fromOpenAPI(openAPI)
          val expected = Code.File(
            List("api", "v1", "Users.scala"),
            pkgPath = List("api", "v1"),
            imports = List(Code.Import.FromBase(path = "component._")),
            objects = List(
              Code.Object(
                "Users",
                Map(
                  Code.Field("get") -> Code.EndpointCode(
                    Method.GET,
                    Code.PathPatternCode(segments =
                      List(Code.PathSegmentCode("api"), Code.PathSegmentCode("v1"), Code.PathSegmentCode("users")),
                    ),
                    queryParamsCode = Set.empty,
                    headersCode = Code.HeadersCode.empty,
                    inCode = Code.InCode("Chunk[User]"),
                    outCodes = Nil,
                    errorsCode = Nil,
                  ),
                ),
              ),
            ),
            caseClasses = Nil,
            enums = Nil,
          )
          assertTrue(scala.files.head == expected)
        },
        test("seq response") {
          val endpoint = Endpoint(Method.GET / "api" / "v1" / "users").out[Chunk[User]]
          val openAPI  = OpenAPIGen.fromEndpoints(endpoint)
          val scala    = EndpointGen.fromOpenAPI(openAPI)
          val expected = Code.File(
            List("api", "v1", "Users.scala"),
            pkgPath = List("api", "v1"),
            imports = List(Code.Import.FromBase(path = "component._")),
            objects = List(
              Code.Object(
                "Users",
                Map(
                  Code.Field("get") -> Code.EndpointCode(
                    Method.GET,
                    Code.PathPatternCode(segments =
                      List(Code.PathSegmentCode("api"), Code.PathSegmentCode("v1"), Code.PathSegmentCode("users")),
                    ),
                    queryParamsCode = Set.empty,
                    headersCode = Code.HeadersCode.empty,
                    inCode = Code.InCode("Unit"),
                    outCodes = List(Code.OutCode.json("Chunk[User]", Status.Ok)),
                    errorsCode = Nil,
                  ),
                ),
              ),
            ),
            caseClasses = Nil,
            enums = Nil,
          )
          assertTrue(scala.files.head == expected)
        },
      ),
      suite("data gen spec")(
        test("generates case class, companion object and schema") {
          val endpoint = Endpoint(Method.GET / "api" / "v1" / "users").out[User]
          val openAPI  = OpenAPIGen.fromEndpoints(endpoint)
          val scala    = EndpointGen.fromOpenAPI(openAPI)
          val expected = Code.File(
            List("component", "User.scala"),
            pkgPath = List("component"),
            imports = List(Code.Import(name = "zio.schema._")),
            objects = List.empty,
            caseClasses = List(
              Code.CaseClass(
                "User",
                fields = List(
                  Code.Field("id", Code.Primitive.ScalaInt),
                  Code.Field("name", Code.Primitive.ScalaString),
                ),
                companionObject = Some(Code.Object.schemaCompanion("User")),
              ),
            ),
            Nil,
          )
          assertTrue(scala.files.tail.head == expected)
        },
        test("generates simple enum and schema") {
          val endpoint = Endpoint(Method.GET / "api" / "v1" / "users").out[Direction]
          val openAPI  = OpenAPIGen.fromEndpoints(endpoint)
          val scala    = EndpointGen.fromOpenAPI(openAPI)
          val expected = Code.File(
            List("component", "Direction.scala"),
            pkgPath = List("component"),
            imports = List(Code.Import(name = "zio.schema._")),
            objects = List.empty,
            caseClasses = List.empty,
            enums = List(
              Code.Enum(
                "Direction",
                List(
                  Code.CaseClass("North"),
                  Code.CaseClass("South"),
                  Code.CaseClass("East"),
                  Code.CaseClass("West"),
                ),
                schema = true,
              ),
            ),
          )
          assertTrue(scala.files.tail.head == expected)
        },
        test("generates enum with values and schema") {
          val endpoint = Endpoint(Method.GET / "api" / "v1" / "users").out[Payment]
          val openAPI  = OpenAPIGen.fromEndpoints(endpoint)
          val scala    = EndpointGen.fromOpenAPI(openAPI)
          val expected = Code.File(
            List("component", "Payment.scala"),
            pkgPath = List("component"),
            imports = List(
              Code.Import(name = "zio.schema._"),
              Code.Import(name = "zio.schema.annotation._"),
            ),
            objects = List.empty,
            caseClasses = List.empty,
            enums = List(
              Code.Enum(
                name = "Payment",
                cases = List(
                  Code.CaseClass(
                    "Card",
                    fields = List(
                      Code.Field("number", Code.Primitive.ScalaString),
                      Code.Field("cvv", Code.Primitive.ScalaString),
                    ),
                    companionObject = Some(Code.Object.schemaCompanion("Card")),
                  ),
                  Code.CaseClass(
                    "Cash",
                    fields = List(
                      Code.Field("amount", Code.Primitive.ScalaInt),
                    ),
                    companionObject = Some(Code.Object.schemaCompanion("Cash")),
                  ),
                ),
                caseNames = List("Card", "cash"),
                schema = true,
              ),
            ),
          )
          assertTrue(scala.files.last == expected)
        },
        test("generates enum with values and schema with named discriminator") {
          val endpoint = Endpoint(Method.GET / "api" / "v1" / "users").out[PaymentNamedDiscriminator]
          val openAPI  = OpenAPIGen.fromEndpoints(endpoint)
          val scala    = EndpointGen.fromOpenAPI(openAPI)
          val expected = Code.File(
            List("component", "PaymentNamedDiscriminator.scala"),
            pkgPath = List("component"),
            imports = List(
              Code.Import(name = "zio.schema._"),
              Code.Import(name = "zio.schema.annotation._"),
            ),
            objects = List.empty,
            caseClasses = List.empty,
            enums = List(
              Code.Enum(
                name = "PaymentNamedDiscriminator",
                cases = List(
                  Code.CaseClass(
                    "Card",
                    fields = List(
                      Code.Field("number", Code.Primitive.ScalaString),
                      Code.Field("cvv", Code.Primitive.ScalaString),
                    ),
                    companionObject = Some(Code.Object.schemaCompanion("Card")),
                  ),
                  Code.CaseClass(
                    "Cash",
                    fields = List(
                      Code.Field("amount", Code.Primitive.ScalaInt),
                    ),
                    companionObject = Some(Code.Object.schemaCompanion("Cash")),
                  ),
                ),
                caseNames = List("Card", "cash"),
                discriminator = Some("type"),
                noDiscriminator = false,
                schema = true,
              ),
            ),
          )
          assertTrue(scala.files.last == expected)
        },
        test("generates enum with values and schema with no discriminator") {
          val endpoint = Endpoint(Method.GET / "api" / "v1" / "users").out[PaymentNoDiscriminator]
          val openAPI  = OpenAPIGen.fromEndpoints(endpoint)
          val scala    = EndpointGen.fromOpenAPI(openAPI)
          val expected = Code.File(
            List("component", "PaymentNoDiscriminator.scala"),
            pkgPath = List("component"),
            imports = List(
              Code.Import(name = "zio.schema._"),
              Code.Import(name = "zio.schema.annotation._"),
            ),
            objects = List.empty,
            caseClasses = List.empty,
            enums = List(
              Code.Enum(
                name = "PaymentNoDiscriminator",
                cases = List(
                  Code.CaseClass(
                    "Card",
                    fields = List(
                      Code.Field("number", Code.Primitive.ScalaString),
                      Code.Field("cvv", Code.Primitive.ScalaString),
                    ),
                    companionObject = Some(Code.Object.schemaCompanion("Card")),
                  ),
                  Code.CaseClass(
                    "Cash",
                    fields = List(
                      Code.Field("amount", Code.Primitive.ScalaInt),
                    ),
                    companionObject = Some(Code.Object.schemaCompanion("Cash")),
                  ),
                ),
                caseNames = Nil,
                discriminator = None,
                noDiscriminator = true,
                schema = true,
              ),
            ),
          )
          assertTrue(scala.files.last == expected)
        },
        test("generates case class for request with inlined schema") {
          val endpoint = Endpoint(Method.POST / "api" / "v1" / "users").in[User]
          val openAPI  = OpenAPIGen.fromEndpoints("", "", Inline, endpoint).copy(components = None)
          val scala    = EndpointGen.fromOpenAPI(openAPI)
          val fields   = List(
            Code.Field("id", Code.Primitive.ScalaInt),
            Code.Field("name", Code.Primitive.ScalaString),
          )
          val expected = Code.File(
            List("api", "v1", "Users.scala"),
            pkgPath = List("api", "v1"),
            imports = List(Code.Import.FromBase(path = "component._")),
            objects = List(
              Code.Object(
                "Users",
                schema = false,
                endpoints = Map(
                  Code.Field("post") -> Code.EndpointCode(
                    Method.POST,
                    Code.PathPatternCode(segments =
                      List(Code.PathSegmentCode("api"), Code.PathSegmentCode("v1"), Code.PathSegmentCode("users")),
                    ),
                    queryParamsCode = Set.empty,
                    headersCode = Code.HeadersCode.empty,
                    inCode = Code.InCode("POST.RequestBody"),
                    outCodes = Nil,
                    errorsCode = Nil,
                  ),
                ),
                objects = List(
                  Code.Object(
                    "POST",
                    schema = false,
                    endpoints = Map.empty,
                    objects = Nil,
                    caseClasses = List(
                      Code.CaseClass(
                        "RequestBody",
                        fields = fields,
                        companionObject = Some(Code.Object.schemaCompanion("RequestBody")),
                      ),
                    ),
                    enums = Nil,
                  ),
                ),
                caseClasses = Nil,
                enums = Nil,
              ),
            ),
            caseClasses = Nil,
            enums = Nil,
          )

          assertTrue(scala.files.head == expected)
        },
        test("generates case class for response with inlined schema") {
          val endpoint = Endpoint(Method.GET / "api" / "v1" / "users").out[User]
          val openAPI  = OpenAPIGen.fromEndpoints("", "", Inline, endpoint).copy(components = None)
          val scala    = EndpointGen.fromOpenAPI(openAPI)
          val fields   = List(
            Code.Field("id", Code.Primitive.ScalaInt),
            Code.Field("name", Code.Primitive.ScalaString),
          )
          val expected = Code.File(
            List("api", "v1", "Users.scala"),
            pkgPath = List("api", "v1"),
            imports = List(Code.Import.FromBase(path = "component._")),
            objects = List(
              Code.Object(
                "Users",
                schema = false,
                endpoints = Map(
                  Code.Field("get") -> Code.EndpointCode(
                    Method.GET,
                    Code.PathPatternCode(segments =
                      List(Code.PathSegmentCode("api"), Code.PathSegmentCode("v1"), Code.PathSegmentCode("users")),
                    ),
                    queryParamsCode = Set.empty,
                    headersCode = Code.HeadersCode.empty,
                    inCode = Code.InCode("Unit"),
                    outCodes = List(Code.OutCode.json("GET.ResponseBody", Status.Ok)),
                    errorsCode = Nil,
                  ),
                ),
                objects = List(
                  Code.Object(
                    "GET",
                    schema = false,
                    endpoints = Map.empty,
                    objects = Nil,
                    caseClasses = List(
                      Code.CaseClass(
                        "ResponseBody",
                        fields = fields,
                        companionObject = Some(Code.Object.schemaCompanion("ResponseBody")),
                      ),
                    ),
                    enums = Nil,
                  ),
                ),
                caseClasses = Nil,
                enums = Nil,
              ),
            ),
            caseClasses = Nil,
            enums = Nil,
          )

          assertTrue(scala.files.head == expected)
        },
        test("generates case class for request and response with inlined schema") {
          val endpoint = Endpoint(Method.POST / "api" / "v1" / "users").in[User].out[User]
          val openAPI  = OpenAPIGen.fromEndpoints("", "", Inline, endpoint).copy(components = None)
          val scala    = EndpointGen.fromOpenAPI(openAPI)
          val fields   = List(
            Code.Field("id", Code.Primitive.ScalaInt),
            Code.Field("name", Code.Primitive.ScalaString),
          )
          val expected = Code.File(
            List("api", "v1", "Users.scala"),
            pkgPath = List("api", "v1"),
            imports = List(Code.Import.FromBase(path = "component._")),
            objects = List(
              Code.Object(
                "Users",
                schema = false,
                endpoints = Map(
                  Code.Field("post") -> Code.EndpointCode(
                    Method.POST,
                    Code.PathPatternCode(segments =
                      List(Code.PathSegmentCode("api"), Code.PathSegmentCode("v1"), Code.PathSegmentCode("users")),
                    ),
                    queryParamsCode = Set.empty,
                    headersCode = Code.HeadersCode.empty,
                    inCode = Code.InCode("POST.RequestBody"),
                    outCodes = List(Code.OutCode.json("POST.ResponseBody", Status.Ok)),
                    errorsCode = Nil,
                  ),
                ),
                objects = List(
                  Code.Object(
                    "POST",
                    schema = false,
                    endpoints = Map.empty,
                    objects = Nil,
                    caseClasses = List(
                      Code.CaseClass(
                        "RequestBody",
                        fields = fields,
                        companionObject = Some(Code.Object.schemaCompanion("RequestBody")),
                      ),
                      Code.CaseClass(
                        "ResponseBody",
                        fields = fields,
                        companionObject = Some(Code.Object.schemaCompanion("ResponseBody")),
                      ),
                    ),
                    enums = Nil,
                  ),
                ),
                caseClasses = Nil,
                enums = Nil,
              ),
            ),
            caseClasses = Nil,
            enums = Nil,
          )

          assertTrue(scala.files.head == expected)
        },
        test("generates case class with seq field for request") {
          val endpoint = Endpoint(Method.POST / "api" / "v1" / "users").in[UserNameArray].out[User]
          val openAPI  = OpenAPIGen.fromEndpoints("", "", endpoint)
          val scala    = EndpointGen.fromOpenAPI(openAPI)
          println(openAPI.toJsonPretty)
          val expected = Code.File(
            List("api", "v1", "Users.scala"),
            pkgPath = List("api", "v1"),
            imports = List(Code.Import.FromBase(path = "component._")),
            objects = List(
              Code.Object(
                "Users",
                schema = false,
                endpoints = Map(
                  Code.Field("post") -> Code.EndpointCode(
                    Method.POST,
                    Code.PathPatternCode(segments =
                      List(Code.PathSegmentCode("api"), Code.PathSegmentCode("v1"), Code.PathSegmentCode("users")),
                    ),
                    queryParamsCode = Set.empty,
                    headersCode = Code.HeadersCode.empty,
                    inCode = Code.InCode("UserNameArray"),
                    outCodes = List(Code.OutCode.json("User", Status.Ok)),
                    errorsCode = Nil,
                  ),
                ),
                objects = Nil,
                caseClasses = Nil,
                enums = Nil,
              ),
            ),
            caseClasses = Nil,
            enums = Nil,
          )
          assertTrue(scala.files.head == expected)
        },
        test("generates code from openapi with examples") {
          val openapiJson = """{
                              |  "openapi": "3.0.3",
                              |  "info": {
                              |    "title": "Example",
                              |    "description": "Example API documentation",
                              |    "version": "1.0.0"
                              |  },
                              |  "paths": {
                              |    "/foo": {
                              |      "post": {
                              |        "requestBody": {
                              |          "content": {
                              |            "application/json": {
                              |              "schema": {
                              |                "$ref": "#/components/schemas/Bar"
                              |              }
                              |            }
                              |          }
                              |        },
                              |        "responses": {
                              |          "200": {
                              |            "description": "Success"
                              |          }
                              |        }
                              |      }
                              |    }
                              |  },
                              |  "components": {
                              |    "schemas": {
                              |      "Bar": {
                              |        "type": "object",
                              |        "properties": {
                              |          "stringField": {
                              |            "type": "string",
                              |            "example": "abc"
                              |          }
                              |        }
                              |      }
                              |    }
                              |  }
                              |}
  """.stripMargin
          val openAPI     = OpenAPI.fromJson(openapiJson).toOption.get
          val scala       = EndpointGen.fromOpenAPI(openAPI)
          val expected    = Code.File(
            List("", "Foo.scala"),
            pkgPath = List(""),
            imports = List(Code.Import.FromBase(path = "component._")),
            objects = List(
              Code.Object(
                "Foo",
                schema = false,
                endpoints = Map(
                  Code.Field("post") -> Code.EndpointCode(
                    Method.POST,
                    Code.PathPatternCode(segments = List(Code.PathSegmentCode("foo"))),
                    queryParamsCode = Set.empty,
                    headersCode = Code.HeadersCode.empty,
                    inCode = Code.InCode("Bar"),
                    outCodes = List(Code.OutCode.json("Unit", Status.Ok)),
                    errorsCode = Nil,
                  ),
                ),
                objects = Nil,
                caseClasses = Nil,
                enums = Nil,
              ),
            ),
            caseClasses = Nil,
            enums = Nil,
          )
          assertTrue(scala.files.head == expected)
        },
      ),
    )

}
