package zio.http.gen.scala

import java.nio.file._

import scala.annotation.nowarn
import scala.jdk.CollectionConverters._
import scala.meta._
import scala.meta.parsers._
import scala.util.{Failure, Success, Try}

import zio.json.JsonDecoder
import zio.test.Assertion.{hasSameElements, isFailure, isSuccess, throws}
import zio.test.TestAspect.{blocking, flaky}
import zio.test.TestFailure.fail
import zio.test._
import zio.{Scope, ZIO}

import zio.schema.codec.JsonCodec

import zio.http._
import zio.http.codec._
import zio.http.endpoint.Endpoint
import zio.http.endpoint.openapi.{OpenAPI, OpenAPIGen}
import zio.http.gen.model._
import zio.http.gen.openapi.{Config, EndpointGen}

@nowarn("msg=missing interpolator")
object CodeGenSpec extends ZIOSpecDefault {

  private def fileShouldBe(dir: java.nio.file.Path, subPath: String, expectedFile: String): TestResult = {
    val filePath  = dir.resolve(Paths.get(subPath))
    val generated = Files.readAllLines(filePath).asScala.mkString("\n")
    isValidScala(generated) && {
      val url           = getClass.getResource(expectedFile)
      val expected      = java.nio.file.Paths.get(url.toURI.getPath)
      val expectedLines = Files.readAllLines(expected).asScala.mkString("\n")
      assertTrue(generated == expectedLines)
    }
  }

  private def isValidScala(code: String): TestResult =
    assert(Try(code.parse[Source]))(Assertion[Try[Parsed[Source]]](TestArrow.make[Try[Parsed[Source]], Boolean] {
      case Failure(failed) => TestTrace.fail(ErrorMessage.throwable(failed))
      case Success(parsed) =>
        parsed.fold(
          e => TestTrace.fail(s"Invalid Scala syntax: ${e.message}"),
          _ => TestTrace.succeed(true),
        )
    }))

  private def allFilesShouldBe(base: java.io.File, expectedSubPaths: List[String]): TestResult = {
    def recurse(cd: java.io.File, acc: List[String]): List[String] =
      cd.listFiles()
        .toList
        .foldLeft(acc) { (subPaths, file) =>
          if (file.isDirectory) recurse(file, subPaths)
          else file.getAbsolutePath.drop(base.getAbsolutePath.length + 1) :: subPaths
        }

    assert(recurse(base, Nil))(hasSameElements(expectedSubPaths))
  }

  private val java11OrNewer = {
    val version = System.getProperty("java.version")
    if (version.takeWhile(_ != '.').toInt >= 11) TestAspect.identity else TestAspect.ignore
  }

  private val scalaFmtPath = java.nio.file.Paths.get(getClass.getResource("/scalafmt.conf").toURI)

  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("CodeGenSpec")(
      test("Simple endpoint without data structures") {
        val endpoint = Endpoint(Method.GET / "api" / "v1" / "users")
        val openAPI  = OpenAPIGen.fromEndpoints(endpoint)
        val code     = EndpointGen.fromOpenAPI(openAPI)

        val tempDir = Files.createTempDirectory("codegen")

        CodeGen.writeFiles(code, java.nio.file.Paths.get(tempDir.toString, "test"), "test", Some(scalaFmtPath))

        fileShouldBe(tempDir, "test/api/v1/Users.scala", "/UsersUnitInOut.scala")
      },
      test("Endpoint with path parameters") {
        val endpoint = Endpoint(Method.GET / "api" / "v1" / "users" / int("userId"))
        val openAPI  = OpenAPIGen.fromEndpoints(endpoint)
        val code     = EndpointGen.fromOpenAPI(openAPI)

        val tempDir = Files.createTempDirectory("codegen")

        CodeGen.writeFiles(code, java.nio.file.Paths.get(tempDir.toString, "test"), "test", Some(scalaFmtPath))

        fileShouldBe(tempDir, "test/api/v1/users/UserId.scala", "/UserIdUnitInOut.scala")
      },
      test("Endpoint with query parameters") {
        val endpoint = Endpoint(Method.GET / "api" / "v1" / "users")
          .query(QueryCodec.queryInt("limit"))
          .query(QueryCodec.query("name"))
        val openAPI  = OpenAPIGen.fromEndpoints(endpoint)
        val code     = EndpointGen.fromOpenAPI(openAPI)

        val tempDir = Files.createTempDirectory("codegen")

        CodeGen.writeFiles(code, java.nio.file.Paths.get(tempDir.toString, "test"), "test", Some(scalaFmtPath))

        fileShouldBe(tempDir, "test/api/v1/Users.scala", "/EndpointWithQueryParams.scala")
      },
      test("Endpoint with headers") {
        val endpoint =
          Endpoint(Method.GET / "api" / "v1" / "users").header(HeaderCodec.accept).header(HeaderCodec.contentType)
        val openAPI  = OpenAPIGen.fromEndpoints(endpoint)
        val code     = EndpointGen.fromOpenAPI(openAPI)

        val tempDir = Files.createTempDirectory("codegen")

        CodeGen.writeFiles(code, java.nio.file.Paths.get(tempDir.toString, "test"), "test", Some(scalaFmtPath))

        fileShouldBe(tempDir, "test/api/v1/Users.scala", "/EndpointWithHeaders.scala")
      },
      test("Endpoint with request body") {
        val endpoint = Endpoint(Method.POST / "api" / "v1" / "users").in[User]
        val openAPI  = OpenAPIGen.fromEndpoints(endpoint)
        val code     = EndpointGen.fromOpenAPI(openAPI)

        val tempDir = Files.createTempDirectory("codegen")

        CodeGen.writeFiles(code, java.nio.file.Paths.get(tempDir.toString, "test"), "test", Some(scalaFmtPath))

        fileShouldBe(tempDir, "test/api/v1/Users.scala", "/EndpointWithRequestBody.scala") &&
        fileShouldBe(tempDir, "test/component/User.scala", "/GeneratedUser.scala")
      },
      test("Endpoint with response body") {
        val endpoint = Endpoint(Method.POST / "api" / "v1" / "users").out[User]
        val openAPI  = OpenAPIGen.fromEndpoints(endpoint)
        val code     = EndpointGen.fromOpenAPI(openAPI)

        val tempDir = Files.createTempDirectory("codegen")

        CodeGen.writeFiles(code, java.nio.file.Paths.get(tempDir.toString, "test"), "test", Some(scalaFmtPath))

        fileShouldBe(tempDir, "test/api/v1/Users.scala", "/EndpointWithResponseBody.scala") &&
        fileShouldBe(tempDir, "test/component/User.scala", "/GeneratedUser.scala")
      },
      test("Endpoint with request and response body") {
        val endpoint = Endpoint(Method.POST / "api" / "v1" / "users").in[User].out[User]
        val openAPI  = OpenAPIGen.fromEndpoints(endpoint)
        val code     = EndpointGen.fromOpenAPI(openAPI)

        val tempDir = Files.createTempDirectory("codegen")

        CodeGen.writeFiles(code, java.nio.file.Paths.get(tempDir.toString, "test"), "test", Some(scalaFmtPath))

        fileShouldBe(tempDir, "test/api/v1/Users.scala", "/EndpointWithRequestResponseBody.scala") &&
        fileShouldBe(tempDir, "test/component/User.scala", "/GeneratedUser.scala")
      },
      test("OpenAPI spec with inline schema request and response body") {
        val openAPIString =
          Files.readAllLines(Paths.get(getClass.getResource("/inline_schema.json").toURI)).asScala.mkString("\n")
        val openAPI       = OpenAPI.fromJson(openAPIString).getOrElse(OpenAPI.empty)
        val code          = EndpointGen.fromOpenAPI(openAPI)

        val tempDir = Files.createTempDirectory("codegen")

        CodeGen.writeFiles(code, java.nio.file.Paths.get(tempDir.toString, "test"), "test", Some(scalaFmtPath))

        fileShouldBe(tempDir, "test/api/v1/Users.scala", "/EndpointWithRequestResponseBodyInline.scala")
      } @@ TestAspect.exceptScala3, // for some reason, the temp dir is empty in Scala 3
      test("OpenAPI spec with inline schema request and response body, with nested object schema") {
        val openAPIString =
          Files.readAllLines(Paths.get(getClass.getResource("/inline_schema_nested.json").toURI)).asScala.mkString("\n")
        val openAPI       = OpenAPI.fromJson(openAPIString).getOrElse(OpenAPI.empty)
        val code          = EndpointGen.fromOpenAPI(openAPI)

        val tempDir = Files.createTempDirectory("codegen")

        CodeGen.writeFiles(code, java.nio.file.Paths.get(tempDir.toString, "test"), "test", Some(scalaFmtPath))

        fileShouldBe(tempDir, "test/api/v1/Users.scala", "/EndpointWithRequestResponseBodyInlineNested.scala")
      } @@ TestAspect.exceptScala3, // for some reason, the temp dir is empty in Scala 3
      test("Endpoint with enum input") {
        val endpoint = Endpoint(Method.POST / "api" / "v1" / "users").in[Payment]
        val openAPI  = OpenAPIGen.fromEndpoints(endpoint)
        val code     = EndpointGen.fromOpenAPI(openAPI)

        val tempDir = Files.createTempDirectory("codegen")
        CodeGen.writeFiles(code, java.nio.file.Paths.get(tempDir.toString, "test"), "test", Some(scalaFmtPath))

        fileShouldBe(
          tempDir,
          "test/api/v1/Users.scala",
          "/EndpointWithEnumInput.scala",
        ) &&
        fileShouldBe(
          tempDir,
          "test/component/Payment.scala",
          "/GeneratedPayment.scala",
        )
      },
      test("Endpoint with enum input with named discriminator") {
        val endpoint = Endpoint(Method.POST / "api" / "v1" / "users").in[PaymentNamedDiscriminator]
        val openAPI  = OpenAPIGen.fromEndpoints(endpoint)
        val code     = EndpointGen.fromOpenAPI(openAPI)

        val tempDir = Files.createTempDirectory("codegen")
        CodeGen.writeFiles(code, java.nio.file.Paths.get(tempDir.toString, "test"), "test", Some(scalaFmtPath))

        fileShouldBe(
          tempDir,
          "test/api/v1/Users.scala",
          "/EndpointWithEnumInputNamedDiscriminator.scala",
        ) &&
        fileShouldBe(
          tempDir,
          "test/component/PaymentNamedDiscriminator.scala",
          "/GeneratedPaymentNamedDiscriminator.scala",
        )
      },
      test("Endpoint with enum input no discriminator") {
        val endpoint = Endpoint(Method.POST / "api" / "v1" / "users").in[PaymentNoDiscriminator]
        val openAPI  = OpenAPIGen.fromEndpoints(endpoint)
        val code     = EndpointGen.fromOpenAPI(openAPI)

        val tempDir = Files.createTempDirectory("codegen")
        CodeGen.writeFiles(code, java.nio.file.Paths.get(tempDir.toString, "test"), "test", Some(scalaFmtPath))

        fileShouldBe(
          tempDir,
          "test/api/v1/Users.scala",
          "/EndpointWithEnumInputNoDiscriminator.scala",
        ) &&
        fileShouldBe(
          tempDir,
          "test/component/PaymentNoDiscriminator.scala",
          "/GeneratedPaymentNoDiscriminator.scala",
        )
      },
      test("Endpoint with case class with field named 'value'") {
        val endpoint = Endpoint(Method.POST / "values").out[Values]
        val openAPI  = OpenAPIGen.fromEndpoints(endpoint)
        val code     = EndpointGen.fromOpenAPI(openAPI)

        val tempDir = Files.createTempDirectory("codegen")
        CodeGen.writeFiles(code, java.nio.file.Paths.get(tempDir.toString, "test"), "test", Some(scalaFmtPath))

        fileShouldBe(
          tempDir,
          "test/component/Values.scala",
          "/GeneratedValues.scala",
        )
      },
      test("OpenAPI spec with inline schema request and response body containing scala keywords") {
        val openAPIString =
          Files
            .readAllLines(Paths.get(getClass.getResource("/inline_schema_with_keywords.json").toURI))
            .asScala
            .mkString("\n")
        val openAPI       = OpenAPI.fromJson(openAPIString).getOrElse(OpenAPI.empty)
        val code          = EndpointGen.fromOpenAPI(openAPI)

        val tempDir = Files.createTempDirectory("codegen")

        CodeGen.writeFiles(code, java.nio.file.Paths.get(tempDir.toString, "test"), "test", Some(scalaFmtPath))

        fileShouldBe(tempDir, "test/api/v1/Keywords.scala", "/EndpointWithRequestResponseBodyWithKeywordsInline.scala")
      } @@ TestAspect.exceptScala3, // for some reason, the temp dir is empty in Scala 3
      test("OpenAPI spec with inline schema response body of reusable fields") {

        import zio.json.yaml.DecoderYamlOps
        implicit val decoder: JsonDecoder[OpenAPI] = JsonCodec.jsonDecoder(OpenAPI.schema)

        val openAPIString =
          Files
            .readAllLines(Paths.get(getClass.getResource("/inline_schema_sumtype_with_reusable_fields.yaml").toURI))
            .asScala
            .mkString("\n")

        openAPIString.fromYaml match {
          case Left(error) => TestResult(TestArrow.make(_ => TestTrace.fail(ErrorMessage.text(error))))
          case Right(oapi) =>
            val code = EndpointGen.fromOpenAPI(oapi)

            val tempDir = Files.createTempDirectory("codegen")
            val testDir = tempDir.resolve("test")

            CodeGen.writeFiles(code, testDir, "test", Some(scalaFmtPath))

            allFilesShouldBe(
              testDir.toFile,
              List(
                "api/v1/zoo/Animal.scala",
                "component/Animal.scala",
                "component/AnimalSharedFields.scala",
                "component/HttpError.scala",
              ),
            ) && fileShouldBe(
              testDir,
              "api/v1/zoo/Animal.scala",
              "/EndpointForZoo.scala",
            ) && fileShouldBe(
              testDir,
              "component/HttpError.scala",
              "/ComponentHttpError.scala",
            ) && fileShouldBe(
              testDir,
              "component/Animal.scala",
              "/ComponentAnimal.scala",
            )
        }
      } @@ TestAspect.exceptScala3, // for some reason, the temp dir is empty in Scala 3
      test("OpenAPI spec with inline schema response body of sum-type with reusable fields") {

        import zio.json.yaml.DecoderYamlOps
        implicit val decoder: JsonDecoder[OpenAPI] = JsonCodec.jsonDecoder(OpenAPI.schema)

        val openAPIString =
          Files
            .readAllLines(Paths.get(getClass.getResource("/inline_schema_sumtype_with_reusable_fields.yaml").toURI))
            .asScala
            .mkString("\n")

        openAPIString.fromYaml match {
          case Left(error) => TestResult(TestArrow.make(_ => TestTrace.fail(ErrorMessage.text(error))))
          case Right(oapi) =>
            val code = EndpointGen.fromOpenAPI(oapi, Config(commonFieldsOnSuperType = true))

            val tempDir = Files.createTempDirectory("codegen")
            val testDir = tempDir.resolve("test")

            CodeGen.writeFiles(code, testDir, "test", Some(scalaFmtPath))

            allFilesShouldBe(
              testDir.toFile,
              List(
                "api/v1/zoo/Animal.scala",
                "component/Animal.scala",
                "component/AnimalSharedFields.scala",
                "component/HttpError.scala",
              ),
            ) && fileShouldBe(
              testDir,
              "api/v1/zoo/Animal.scala",
              "/EndpointForZoo.scala",
            ) && fileShouldBe(
              testDir,
              "component/HttpError.scala",
              "/ComponentHttpError.scala",
            ) && fileShouldBe(
              testDir,
              "component/Animal.scala",
              "/ComponentAnimalWithAbstractMembers.scala",
            )
        }
      } @@ TestAspect.exceptScala3, // for some reason, the temp dir is empty in Scala 3
      test("OpenAPI spec with inline schema response body of sum-type with multiple reusable fields") {

        import zio.json.yaml.DecoderYamlOps
        implicit val decoder: JsonDecoder[OpenAPI] = JsonCodec.jsonDecoder(OpenAPI.schema)

        val openAPIString =
          Files
            .readAllLines(
              Paths.get(getClass.getResource("/inline_schema_sumtype_with_multiple_reusable_fields.yaml").toURI),
            )
            .asScala
            .mkString("\n")

        openAPIString.fromYaml match {
          case Left(error) => TestResult(TestArrow.make(_ => TestTrace.fail(ErrorMessage.text(error))))
          case Right(oapi) =>
            val code = EndpointGen.fromOpenAPI(oapi, Config(commonFieldsOnSuperType = true))

            val tempDir = Files.createTempDirectory("codegen")
            val testDir = tempDir.resolve("test")

            CodeGen.writeFiles(code, testDir, "test", Some(scalaFmtPath))

            allFilesShouldBe(
              testDir.toFile,
              List(
                "api/v1/zoo/Animal.scala",
                "component/Animal.scala",
                "component/HasAge.scala",
                "component/HasWeight.scala",
              ),
            ) && fileShouldBe(
              testDir,
              "api/v1/zoo/Animal.scala",
              "/EndpointForZooNoError.scala",
            ) && fileShouldBe(
              testDir,
              "component/Animal.scala",
              "/ComponentAnimalWithAbstractMembers.scala",
            )
        }
      } @@ TestAspect.exceptScala3, // for some reason, the temp dir is empty in Scala 3
      test("OpenAPI spec with inline schema response body of sum-type with multiple contradicting reusable fields") {

        import zio.json.yaml.DecoderYamlOps
        implicit val decoder: JsonDecoder[OpenAPI] = JsonCodec.jsonDecoder(OpenAPI.schema)

        val openAPIString =
          Files
            .readAllLines(
              Paths.get(
                getClass.getResource("/inline_schema_sumtype_with_multiple_contradicting_reusable_fields.yaml").toURI,
              ),
            )
            .asScala
            .mkString("\n")

        openAPIString.fromYaml match {
          case Left(error) => TestResult(TestArrow.make(_ => TestTrace.fail(ErrorMessage.text(error))))
          case Right(oapi) =>
            assert {
              Try(EndpointGen.fromOpenAPI(oapi))
            }(isFailure)
        }
      } @@ TestAspect.exceptScala3, // for some reason, the temp dir is empty in Scala 3
      test(
        "OpenAPI spec with inline schema response body of sum-type with multiple contradicting reusable fields and super type members",
      ) {

        import zio.json.yaml.DecoderYamlOps
        implicit val decoder: JsonDecoder[OpenAPI] = JsonCodec.jsonDecoder(OpenAPI.schema)

        val openAPIString =
          Files
            .readAllLines(
              Paths.get(
                getClass.getResource("/inline_schema_sumtype_with_multiple_contradicting_reusable_fields.yaml").toURI,
              ),
            )
            .asScala
            .mkString("\n")

        openAPIString.fromYaml match {
          case Left(error) => TestResult(TestArrow.make(_ => TestTrace.fail(ErrorMessage.text(error))))
          case Right(oapi) =>
            assert {
              Try(EndpointGen.fromOpenAPI(oapi, Config(commonFieldsOnSuperType = true)))
            }(isFailure)
        }
      } @@ TestAspect.exceptScala3, // for some reason, the temp dir is empty in Scala 3
      test(
        "OpenAPI spec with inline schema response body of sum-type with multiple non-contradicting reusable fields and super type members",
      ) {

        import zio.json.yaml.DecoderYamlOps
        implicit val decoder: JsonDecoder[OpenAPI] = JsonCodec.jsonDecoder(OpenAPI.schema)

        val openAPIString =
          Files
            .readAllLines(
              Paths.get(
                getClass
                  .getResource("/inline_schema_sumtype_with_multiple_non_contradicting_reusable_fields.yaml")
                  .toURI,
              ),
            )
            .asScala
            .mkString("\n")

        openAPIString.fromYaml match {
          case Left(error) => TestResult(TestArrow.make(_ => TestTrace.fail(ErrorMessage.text(error))))
          case Right(oapi) =>
            val t = Try(EndpointGen.fromOpenAPI(oapi, Config(commonFieldsOnSuperType = true)))
            assert(t)(isSuccess) && {
              val tempDir = Files.createTempDirectory("codegen")
              val testDir = tempDir.resolve("test")

              CodeGen.writeFiles(t.get, testDir, "test", Some(scalaFmtPath))

              allFilesShouldBe(
                testDir.toFile,
                List(
                  "api/v1/zoo/Animal.scala",
                  "component/Animal.scala",
                  "component/HasAgeAndWeight.scala",
                  "component/HasWeight.scala",
                ),
              ) && fileShouldBe(
                testDir,
                "api/v1/zoo/Animal.scala",
                "/EndpointForZooNoError.scala",
              ) && fileShouldBe(
                testDir,
                "component/Animal.scala",
                "/ComponentAnimalWithAbstractMembers.scala",
              )
            }
        }
      } @@ TestAspect.exceptScala3, // for some reason, the temp dir is empty in Scala 3
      test("OpenAPI spec with inline schema response body of sum-type whose concrete subtype is referenced directly") {

        import zio.json.yaml.DecoderYamlOps
        implicit val decoder: JsonDecoder[OpenAPI] = JsonCodec.jsonDecoder(OpenAPI.schema)

        val openAPIString =
          Files
            .readAllLines(
              Paths.get(
                getClass
                  .getResource("/inline_schema_sumtype_with_subtype_referenced_directly.yaml")
                  .toURI,
              ),
            )
            .asScala
            .mkString("\n")

        openAPIString.fromYaml match {
          case Left(error) => TestResult(TestArrow.make(_ => TestTrace.fail(ErrorMessage.text(error))))
          case Right(oapi) =>
            val t = Try(EndpointGen.fromOpenAPI(oapi, Config(commonFieldsOnSuperType = true)))
            assert(t)(isSuccess) && {
              val tempDir = Files.createTempDirectory("codegen")
              val testDir = tempDir.resolve("test")

              CodeGen.writeFiles(t.get, testDir, "test", Some(scalaFmtPath))

              allFilesShouldBe(
                testDir.toFile,
                List(
                  "api/v1/zoo/Animal.scala",
                  "component/Animal.scala",
                  "component/AnimalSharedFields.scala",
                  "component/Lion.scala",
                ),
              ) && fileShouldBe(
                testDir,
                "api/v1/zoo/Animal.scala",
                "/EndpointForZooNoError.scala",
              ) && fileShouldBe(
                testDir,
                "component/Animal.scala",
                "/ComponentAnimalWithFieldsReferencingSubs.scala",
              ) && fileShouldBe(
                testDir,
                "component/Lion.scala",
                "/ComponentLion.scala",
              )
            }
        }
      } @@ TestAspect.exceptScala3, // for some reason, the temp dir is empty in Scala 3
      test("Endpoint with array field in input") {
        val endpoint = Endpoint(Method.POST / "api" / "v1" / "users").in[UserNameArray].out[User]
        val openAPI  = OpenAPIGen.fromEndpoints("", "", endpoint)
        val code     = EndpointGen.fromOpenAPI(openAPI)

        val tempDir = Files.createTempDirectory("codegen")
        CodeGen.writeFiles(code, java.nio.file.Paths.get(tempDir.toString, "test"), "test", Some(scalaFmtPath))

        fileShouldBe(
          tempDir,
          "test/component/UserNameArray.scala",
          "/GeneratedUserNameArray.scala",
        )
      },
      test("Endpoints with common prefix") {
        val json    = """{
                     |  "openapi": "3.0.0",
                     |  "info": {
                     |    "version": "1.0.0",
                     |    "title": "Swagger Petstore",
                     |    "license": {
                     |      "name": "MIT"
                     |    }
                     |  },
                     |  "servers": [
                     |    {
                     |      "url": "http://petstore.swagger.io/v1"
                     |    }
                     |  ],
                     |  "paths": {
                     |    "/pets": {
                     |      "get": {
                     |        "summary": "List all pets",
                     |        "operationId": "listPets",
                     |        "tags": [
                     |          "pets"
                     |        ],
                     |        "parameters": [
                     |          {
                     |            "name": "limit",
                     |            "in": "query",
                     |            "description": "How many items to return at one time (max 100)",
                     |            "required": false,
                     |            "schema": {
                     |              "type": "integer",
                     |              "maximum": 100,
                     |              "format": "int32"
                     |            }
                     |          }
                     |        ],
                     |        "responses": {
                     |          "200": {
                     |            "description": "A paged array of pets",
                     |            "headers": {
                     |              "x-next": {
                     |                "description": "A link to the next page of responses",
                     |                "schema": {
                     |                  "type": "string"
                     |                }
                     |              }
                     |            },
                     |            "content": {
                     |              "application/json": {
                     |                "schema": {
                     |                  "$ref": "#/components/schemas/Pets"
                     |                }
                     |              }
                     |            }
                     |          },
                     |          "default": {
                     |            "description": "unexpected error",
                     |            "content": {
                     |              "application/json": {
                     |                "schema": {
                     |                  "$ref": "#/components/schemas/Error"
                     |                }
                     |              }
                     |            }
                     |          }
                     |        }
                     |      },
                     |      "post": {
                     |        "summary": "Create a pet",
                     |        "operationId": "createPets",
                     |        "tags": [
                     |          "pets"
                     |        ],
                     |        "requestBody": {
                     |          "content": {
                     |            "application/json": {
                     |              "schema": {
                     |                "$ref": "#/components/schemas/Pet"
                     |              }
                     |            }
                     |          },
                     |          "required": true
                     |        },
                     |        "responses": {
                     |          "201": {
                     |            "description": "Null response"
                     |          },
                     |          "default": {
                     |            "description": "unexpected error",
                     |            "content": {
                     |              "application/json": {
                     |                "schema": {
                     |                  "$ref": "#/components/schemas/Error"
                     |                }
                     |              }
                     |            }
                     |          }
                     |        }
                     |      }
                     |    }
                     |  },
                     |  "components": {
                     |    "schemas": {
                     |      "Pet": {
                     |        "type": "object",
                     |        "required": [
                     |          "id",
                     |          "name"
                     |        ],
                     |        "properties": {
                     |          "id": {
                     |            "type": "integer",
                     |            "format": "int64"
                     |          },
                     |          "name": {
                     |            "type": "string",
                     |            "minLength": 3
                     |          },
                     |          "tag": {
                     |            "type": "string"
                     |          }
                     |        }
                     |      },
                     |      "Pets": {
                     |        "type": "array",
                     |        "maxItems": 100,
                     |        "items": {
                     |          "$ref": "#/components/schemas/Pet"
                     |        }
                     |      },
                     |      "Error": {
                     |        "type": "object",
                     |        "required": [
                     |          "code",
                     |          "message"
                     |        ],
                     |        "properties": {
                     |          "code": {
                     |            "type": "integer",
                     |            "format": "int32"
                     |          },
                     |          "message": {
                     |            "type": "string"
                     |          }
                     |        }
                     |      }
                     |    }
                     |  }
                     |}""".stripMargin
        val openAPI = OpenAPI.fromJson(json).toOption.get
        val code    = EndpointGen.fromOpenAPI(openAPI)
        val tempDir = Files.createTempDirectory("codegen")

        CodeGen.writeFiles(code, java.nio.file.Paths.get(tempDir.toString, "test"), "test", Some(scalaFmtPath))

        fileShouldBe(
          tempDir,
          "test/Pets.scala",
          "/EndpointsWithOverlappingPath.scala",
        )
      },
      test("Additional properties") {
        val json    = """{
                     |  "info": {
                     |    "title": "Animals Service",
                     |    "version": "0.0.1"
                     |  },
                     |  "servers": [
                     |    {
                     |      "url": "http://127.0.0.1:5000/"
                     |    }
                     |  ],
                     |  "tags": [
                     |    {
                     |      "name": "Animals_API"
                     |    }
                     |  ],
                     |  "paths": {
                     |    "/api/v1/zoo": {
                     |      "get": {
                     |        "operationId": "get_animals",
                     |        "tags": [
                     |          "Animals_API"
                     |        ],
                     |        "description": "Get all animals count",
                     |        "responses": {
                     |          "200": {
                     |            "content": {
                     |              "application/json": {
                     |                "schema": {
                     |                  "$ref": "#/components/schemas/Animals"
                     |                }
                     |              }
                     |            }
                     |          }
                     |        }
                     |      }
                     |    }
                     |  },
                     |  "openapi": "3.0.3",
                     |  "components": {
                     |    "schemas": {
                     |      "Animals": {
                     |        "type": "object",
                     |        "required": [
                     |          "total",
                     |          "counts"
                     |        ],
                     |        "properties": {
                     |          "total": {
                     |            "type": "integer",
                     |            "format": "int32"
                     |          },
                     |          "counts": {
                     |            "type": "object",
                     |            "additionalProperties": {
                     |              "type": "integer",
                     |              "format": "int32"
                     |            }
                     |          }
                     |        }
                     |      }
                     |    }
                     |  }
                     |}""".stripMargin
        val openAPI = OpenAPI.fromJson(json).toOption.get
        val code    = EndpointGen.fromOpenAPI(openAPI)
        val tempDir = Files.createTempDirectory("codegen")

        CodeGen.writeFiles(code, java.nio.file.Paths.get(tempDir.toString, "test"), "test", Some(scalaFmtPath))

        fileShouldBe(
          tempDir,
          "test/component/Animals.scala",
          "/AnimalWithMap.scala",
        )
      },
    ) @@ java11OrNewer @@ flaky @@ blocking // Downloading scalafmt on CI is flaky
}
