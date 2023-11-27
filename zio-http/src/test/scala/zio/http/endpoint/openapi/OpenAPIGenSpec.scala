package zio.http.endpoint.openapi

import zio.Scope
import zio.json.ast.Json
import zio.json.{EncoderOps, JsonEncoder}
import zio.test._

import zio.schema.annotation.{caseName, discriminatorName, noDiscriminator, optionalField, transientField}
import zio.schema.codec.JsonCodec
import zio.schema.{DeriveSchema, Schema}

import zio.http.Method.GET
import zio.http._
import zio.http.codec.{Doc, HttpCodec, QueryCodec}
import zio.http.endpoint._

object OpenAPIGenSpec extends ZIOSpecDefault {

  final case class SimpleInputBody(name: String, age: Int)
  implicit val simpleInputBodySchema: Schema[SimpleInputBody]           =
    DeriveSchema.gen[SimpleInputBody]
  final case class OtherSimpleInputBody(fullName: String, shoeSize: Int)
  implicit val otherSimpleInputBodySchema: Schema[OtherSimpleInputBody] =
    DeriveSchema.gen[OtherSimpleInputBody]
  final case class SimpleOutputBody(userName: String, score: Int)
  implicit val simpleOutputBodySchema: Schema[SimpleOutputBody]         =
    DeriveSchema.gen[SimpleOutputBody]
  final case class NotFoundError(message: String)
  implicit val notFoundErrorSchema: Schema[NotFoundError]               =
    DeriveSchema.gen[NotFoundError]
  final case class ImageMetadata(name: String, size: Int)
  implicit val imageMetadataSchema: Schema[ImageMetadata]               =
    DeriveSchema.gen[ImageMetadata]

  final case class WithTransientField(name: String, @transientField age: Int)
  implicit val withTransientFieldSchema: Schema[WithTransientField] =
    DeriveSchema.gen[WithTransientField]

  final case class WithDefaultValue(age: Int = 42)
  implicit val withDefaultValueSchema: Schema[WithDefaultValue]               =
    DeriveSchema.gen[WithDefaultValue]
  final case class WithComplexDefaultValue(data: ImageMetadata = ImageMetadata("default", 42))
  implicit val withDefaultComplexValueSchema: Schema[WithComplexDefaultValue] =
    DeriveSchema.gen[WithComplexDefaultValue]

  final case class WithOptionalField(name: String, @optionalField age: Int)
  implicit val withOptionalFieldSchema: Schema[WithOptionalField] =
    DeriveSchema.gen[WithOptionalField]

  sealed trait SimpleEnum
  object SimpleEnum {
    implicit val schema: Schema[SimpleEnum] = DeriveSchema.gen[SimpleEnum]
    case object One   extends SimpleEnum
    case object Two   extends SimpleEnum
    case object Three extends SimpleEnum
  }

  sealed trait SealedTraitDefaultDiscriminator

  object SealedTraitDefaultDiscriminator {
    implicit val schema: Schema[SealedTraitDefaultDiscriminator] =
      DeriveSchema.gen[SealedTraitDefaultDiscriminator]

    case object One extends SealedTraitDefaultDiscriminator

    case class Two(name: String) extends SealedTraitDefaultDiscriminator

    @caseName("three")
    case class Three(name: String) extends SealedTraitDefaultDiscriminator
  }

  @discriminatorName("type")
  sealed trait SealedTraitCustomDiscriminator

  object SealedTraitCustomDiscriminator {
    implicit val schema: Schema[SealedTraitCustomDiscriminator] = DeriveSchema.gen[SealedTraitCustomDiscriminator]

    case object One extends SealedTraitCustomDiscriminator

    case class Two(name: String) extends SealedTraitCustomDiscriminator

    @caseName("three")
    case class Three(name: String) extends SealedTraitCustomDiscriminator
  }

  @noDiscriminator
  sealed trait SealedTraitNoDiscriminator

  object SealedTraitNoDiscriminator {
    implicit val schema: Schema[SealedTraitNoDiscriminator] = DeriveSchema.gen[SealedTraitNoDiscriminator]

    case object One extends SealedTraitNoDiscriminator

    case class Two(name: String) extends SealedTraitNoDiscriminator

    @caseName("three")
    case class Three(name: String) extends SealedTraitNoDiscriminator
  }

  @noDiscriminator
  sealed trait SimpleNestedSealedTrait

  object SimpleNestedSealedTrait {
    implicit val schema: Schema[SimpleNestedSealedTrait] = DeriveSchema.gen[SimpleNestedSealedTrait]

    case object NestedOne extends SimpleNestedSealedTrait

    case class NestedTwo(name: SealedTraitNoDiscriminator) extends SimpleNestedSealedTrait

    case class NestedThree(name: String) extends SimpleNestedSealedTrait
  }

  private val simpleEndpoint =
    Endpoint(
      (GET / "static" / int("id") / uuid("uuid") ?? Doc.p("user id") / string("name")) ?? Doc.p("get path"),
    )
      .in[SimpleInputBody](Doc.p("input body"))
      .out[SimpleOutputBody](Doc.p("output body"))
      .outError[NotFoundError](Status.NotFound, Doc.p("not found"))

  private val queryParamEndpoint =
    Endpoint(GET / "withQuery")
      .in[SimpleInputBody]
      .query(QueryCodec.paramStr("query"))
      .out[SimpleOutputBody]
      .outError[NotFoundError](Status.NotFound)

  private val alternativeInputEndpoint =
    Endpoint(GET / "inputAlternative")
      .inCodec(
        (HttpCodec.content[OtherSimpleInputBody] ?? Doc.p("other input") | HttpCodec
          .content[SimpleInputBody] ?? Doc.p("simple input")) ?? Doc.p("takes either of the two input bodies"),
      )
      .out[SimpleOutputBody]
      .outError[NotFoundError](Status.NotFound)

  def toJsonAst(str: String): Json =
    Json.decoder.decodeJson(str).toOption.get

  def toJsonAst(api: OpenAPI): Json =
    toJsonAst(api.toJson)

  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("OpenAPIGenSpec")(
      test("simple endpoint to OpenAPI") {
        val generated    = OpenAPIGen.fromEndpoints("Simple Endpoint", "1.0", simpleEndpoint)
        val json         = toJsonAst(generated)
        val expectedJson = """{
                             |  "openapi" : "3.1.0",
                             |  "info" : {
                             |    "title" : "Simple Endpoint",
                             |    "version" : "1.0"
                             |  },
                             |  "paths" : {
                             |    "/static/{id}/{uuid}/{name}" : {
                             |      "get" : {
                             |        "parameters" : [
                             |
                             |            {
                             |            "name" : "id",
                             |            "in" : "path",
                             |            "required" : true,
                             |            "deprecated" : false,
                             |            "schema" :
                             |              {
                             |              "type" :
                             |                "integer",
                             |              "format" : "int32"
                             |            },
                             |            "explode" : false,
                             |            "style" : "simple"
                             |          },
                             |
                             |            {
                             |            "name" : "uuid",
                             |            "in" : "path",
                             |            "description" : "user id\n\n",
                             |            "required" : true,
                             |            "deprecated" : false,
                             |            "schema" :
                             |              {
                             |              "type" :
                             |                "string"
                             |            },
                             |            "explode" : false,
                             |            "style" : "simple"
                             |          },
                             |
                             |            {
                             |            "name" : "name",
                             |            "in" : "path",
                             |            "required" : true,
                             |            "deprecated" : false,
                             |            "schema" :
                             |              {
                             |              "type" :
                             |                "string"
                             |            },
                             |            "explode" : false,
                             |            "style" : "simple"
                             |          }
                             |        ],
                             |        "requestBody" :
                             |          {
                             |          "content" : {
                             |            "application/json" : {
                             |              "schema" : {
                             |              "$ref": "#/components/schemas/SimpleInputBody",
                             |              "description" : "input body\n\n"
                             |              }
                             |            }
                             |          },
                             |          "required" : true
                             |        },
                             |        "responses" : {
                             |          "200" :
                             |            {
                             |            "description" : "",
                             |            "content" : {
                             |              "application/json" : {
                             |                "schema" : {
                             |                "$ref": "#/components/schemas/SimpleOutputBody",
                             |                "description" : "output body\n\n"
                             |                }
                             |              }
                             |            }
                             |          },
                             |          "404" :
                             |            {
                             |            "description" : "",
                             |            "content" : {
                             |              "application/json" : {
                             |                "schema" : {
                             |                "$ref": "#/components/schemas/NotFoundError",
                             |                "description" : "not found\n\n"
                             |              }
                             |            }
                             |          }
                             |        }
                             |      },
                             |      "deprecated" : false
                             |    }
                             |  }
                             |  },
                             |  "components" : {
                             |    "schemas" : {
                             |      "SimpleInputBody" :
                             |        {
                             |        "type" :
                             |          "object",
                             |        "properties" : {
                             |          "name" : {
                             |            "type" :
                             |              "string"
                             |          },
                             |          "age" : {
                             |            "type" :
                             |              "integer",
                             |            "format" : "int32"
                             |          }
                             |        },
                             |        "additionalProperties" :
                             |          true,
                             |        "required" : [
                             |          "name",
                             |          "age"
                             |        ]
                             |      },
                             |      "NotFoundError" :
                             |        {
                             |        "type" :
                             |          "object",
                             |        "properties" : {
                             |          "message" : {
                             |            "type" :
                             |              "string"
                             |          }
                             |        },
                             |        "additionalProperties" :
                             |          true,
                             |        "required" : [
                             |          "message"
                             |        ]
                             |      },
                             |      "SimpleOutputBody" :
                             |        {
                             |        "type" :
                             |          "object",
                             |        "properties" : {
                             |          "userName" : {
                             |            "type" :
                             |              "string"
                             |          },
                             |          "score" : {
                             |            "type" :
                             |              "integer",
                             |            "format" : "int32"
                             |          }
                             |        },
                             |        "additionalProperties" :
                             |          true,
                             |        "required" : [
                             |          "userName",
                             |          "score"
                             |        ]
                             |      }
                             |    }
                             |  }
                             |}""".stripMargin
        assertTrue(json == toJsonAst(expectedJson))
      },
      test("with query parameter") {
        val generated    = OpenAPIGen.fromEndpoints("Simple Endpoint", "1.0", queryParamEndpoint)
        val json         = toJsonAst(generated)
        val expectedJson = """{
                             |  "openapi" : "3.1.0",
                             |  "info" : {
                             |    "title" : "Simple Endpoint",
                             |    "version" : "1.0"
                             |  },
                             |  "paths" : {
                             |    "/withQuery" : {
                             |      "get" : {
                             |        "parameters" : [
                             |          {
                             |            "name" : "query",
                             |            "in" : "query",
                             |            "required" : true,
                             |            "deprecated" : false,
                             |            "schema" :
                             |              {
                             |                "type" :
                             |                  "string"
                             |              },
                             |            "explode" : false,
                             |            "allowReserved" : false,
                             |            "style" : "form"
                             |          }
                             |        ],
                             |        "requestBody" :
                             |          {
                             |          "content" : {
                             |            "application/json" : {
                             |              "schema" : {"$ref": "#/components/schemas/SimpleInputBody"}
                             |            }
                             |          },
                             |          "required" : true
                             |        },
                             |        "responses" : {
                             |          "200" :
                             |            {
                             |            "description" : "",
                             |            "content" : {
                             |              "application/json" : {
                             |                "schema" : {"$ref": "#/components/schemas/SimpleOutputBody"}
                             |              }
                             |            }
                             |          },
                             |          "404" :
                             |            {
                             |            "description" : "",
                             |            "content" : {
                             |              "application/json" : {
                             |                "schema" : {"$ref": "#/components/schemas/NotFoundError"}
                             |            }
                             |          }
                             |        }
                             |      },
                             |      "deprecated" : false
                             |    }
                             |  }
                             |  },
                             |  "components" : {
                             |    "schemas" : {
                             |      "SimpleInputBody" :
                             |        {
                             |        "type" :
                             |          "object",
                             |        "properties" : {
                             |          "name" : {
                             |            "type" :
                             |              "string"
                             |          },
                             |          "age" : {
                             |            "type" :
                             |              "integer",
                             |            "format" : "int32"
                             |          }
                             |        },
                             |        "additionalProperties" :
                             |          true,
                             |        "required" : [
                             |          "name",
                             |          "age"
                             |        ]
                             |      },
                             |      "NotFoundError" :
                             |        {
                             |        "type" :
                             |          "object",
                             |        "properties" : {
                             |          "message" : {
                             |            "type" :
                             |              "string"
                             |          }
                             |        },
                             |        "additionalProperties" :
                             |          true,
                             |        "required" : [
                             |          "message"
                             |        ]
                             |      },
                             |      "SimpleOutputBody" :
                             |        {
                             |        "type" :
                             |          "object",
                             |        "properties" : {
                             |          "userName" : {
                             |            "type" :
                             |              "string"
                             |          },
                             |          "score" : {
                             |            "type" :
                             |              "integer",
                             |            "format" : "int32"
                             |          }
                             |        },
                             |        "additionalProperties" :
                             |          true,
                             |        "required" : [
                             |          "userName",
                             |          "score"
                             |        ]
                             |      }
                             |    }
                             |  }
                             |}""".stripMargin
        assertTrue(json == toJsonAst(expectedJson))
      },
      test("alternative input") {
        val generated    = OpenAPIGen.fromEndpoints("Simple Endpoint", "1.0", alternativeInputEndpoint)
        val json         = toJsonAst(generated)
        val expectedJson =
          """{
            |  "openapi" : "3.1.0",
            |  "info" : {
            |    "title" : "Simple Endpoint",
            |    "version" : "1.0"
            |  },
            |  "paths" : {
            |    "/inputAlternative" : {
            |      "get" : {
            |        "requestBody" :
            |          {
            |          "content" : {
            |            "application/json" : {
            |              "schema" : {
            |               "anyOf" : [
            |               {
            |               "$ref": "#/components/schemas/OtherSimpleInputBody",
            |               "description" : "other input\n\n"
            |               },
            |               {
            |               "$ref": "#/components/schemas/SimpleInputBody",
            |               "description" : "simple input\n\n"
            |               }
            |               ],
            |               "description" : "takes either of the two input bodies\n\n"
            |               }
            |            }
            |          },
            |          "required" : true
            |        },
            |        "responses" : {
            |          "200" :
            |            {
            |            "description" : "",
            |            "content" : {
            |              "application/json" : {
            |                "schema" : {"$ref": "#/components/schemas/SimpleOutputBody"}
            |              }
            |            }
            |          },
            |          "404" :
            |            {
            |            "description" : "",
            |            "content" : {
            |              "application/json" : {
            |                "schema" : {"$ref": "#/components/schemas/NotFoundError"}
            |            }
            |          }
            |        }
            |      },
            |      "deprecated" : false
            |    }
            |  }
            |  },
            |  "components" : {
            |    "schemas" : {
            |      "OtherSimpleInputBody" :
            |        {
            |        "type" :
            |          "object",
            |        "properties" : {
            |          "fullName" : {
            |            "type" :
            |              "string"
            |          },
            |          "shoeSize" : {
            |            "type" :
            |              "integer",
            |            "format" : "int32"
            |          }
            |        },
            |        "additionalProperties" :
            |          true,
            |        "required" : [
            |          "fullName",
            |          "shoeSize"
            |        ]
            |      },
            |      "SimpleInputBody" :
            |        {
            |        "type" :
            |          "object",
            |        "properties" : {
            |          "name" : {
            |            "type" :
            |              "string"
            |          },
            |          "age" : {
            |            "type" :
            |              "integer",
            |            "format" : "int32"
            |          }
            |        },
            |        "additionalProperties" :
            |          true,
            |        "required" : [
            |          "name",
            |          "age"
            |        ]
            |      },
            |      "NotFoundError" :
            |        {
            |        "type" :
            |          "object",
            |        "properties" : {
            |          "message" : {
            |            "type" :
            |              "string"
            |          }
            |        },
            |        "additionalProperties" :
            |          true,
            |        "required" : [
            |          "message"
            |        ]
            |      },
            |      "SimpleOutputBody" :
            |        {
            |        "type" :
            |          "object",
            |        "properties" : {
            |          "userName" : {
            |            "type" :
            |              "string"
            |          },
            |          "score" : {
            |            "type" :
            |              "integer",
            |            "format" : "int32"
            |          }
            |        },
            |        "additionalProperties" :
            |          true,
            |        "required" : [
            |          "userName",
            |          "score"
            |        ]
            |      }
            |    }
            |  }
            |}""".stripMargin
        assertTrue(json == toJsonAst(expectedJson))
      },
      test("alternative output") {
        val endpoint     =
          Endpoint(GET / "static")
            .in[SimpleInputBody]
            .outCodec(
              (HttpCodec.content[SimpleOutputBody] ?? Doc.p("simple output") | HttpCodec
                .content[NotFoundError] ?? Doc.p("not found")) ?? Doc.p("alternative outputs"),
            )
        val generated    = OpenAPIGen.fromEndpoints("Simple Endpoint", "1.0", endpoint)
        val json         = toJsonAst(generated)
        val expectedJson =
          """{
            |  "openapi" : "3.1.0",
            |  "info" : {
            |    "title" : "Simple Endpoint",
            |    "version" : "1.0"
            |  },
            |  "paths" : {
            |    "/static" : {
            |      "get" : {
            |        "requestBody" :
            |          {
            |          "content" : {
            |            "application/json" : {
            |              "schema" : {"$ref": "#/components/schemas/SimpleInputBody"}
            |            }
            |          },
            |          "required" : true
            |        },
            |        "responses" : {
            |          "default" :
            |            {
            |            "description" : "",
            |            "content" : {
            |              "application/json" : {
            |                "schema" : { "anyOf" : [
            |                 {
            |                 "$ref": "#/components/schemas/SimpleOutputBody",
            |                 "description" : "simple output\n\n"
            |                 },
            |                 {
            |                 "$ref": "#/components/schemas/NotFoundError",
            |                 "description" : "not found\n\n"
            |                 }
            |                 ],
            |                 "description" : "alternative outputs\n\n"
            |                 }
            |              }
            |            }
            |          }
            |        },
            |        "deprecated" : false
            |      }
            |    }
            |  },
            |  "components" : {
            |    "schemas" : {
            |      "SimpleInputBody" :
            |        {
            |        "type" :
            |          "object",
            |        "properties" : {
            |          "name" : {
            |            "type" :
            |              "string"
            |          },
            |          "age" : {
            |            "type" :
            |              "integer",
            |            "format" : "int32"
            |          }
            |        },
            |        "additionalProperties" :
            |          true,
            |        "required" : [
            |          "name",
            |          "age"
            |        ]
            |      },
            |      "SimpleOutputBody" :
            |        {
            |        "type" :
            |          "object",
            |        "properties" : {
            |          "userName" : {
            |            "type" :
            |              "string"
            |          },
            |          "score" : {
            |            "type" :
            |              "integer",
            |            "format" : "int32"
            |          }
            |        },
            |        "additionalProperties" :
            |          true,
            |        "required" : [
            |          "userName",
            |          "score"
            |        ]
            |      },
            |      "NotFoundError" :
            |        {
            |        "type" :
            |          "object",
            |        "properties" : {
            |          "message" : {
            |            "type" :
            |              "string"
            |          }
            |        },
            |        "additionalProperties" :
            |          true,
            |        "required" : [
            |          "message"
            |        ]
            |      }
            |    }
            |  }
            |}""".stripMargin
        assertTrue(json == toJsonAst(expectedJson))
      },
      test("with examples") {
        val endpoint =
          Endpoint(GET / "static")
            .inCodec(
              HttpCodec
                .content[SimpleInputBody]
                .examples("john" -> SimpleInputBody("John", 42), "jane" -> SimpleInputBody("Jane", 43)),
            )
            .outCodec(
              HttpCodec
                .content[SimpleOutputBody]
                .examples("john" -> SimpleOutputBody("John", 42), "jane" -> SimpleOutputBody("Jane", 43)) |
                HttpCodec
                  .content[NotFoundError]
                  .examples("not found" -> NotFoundError("not found")),
            )

        val generated    = OpenAPIGen.fromEndpoints("Simple Endpoint", "1.0", endpoint)
        val json         = toJsonAst(generated)
        val expectedJson =
          """{
            |  "openapi" : "3.1.0",
            |  "info" : {
            |    "title" : "Simple Endpoint",
            |    "version" : "1.0"
            |  },
            |  "paths" : {
            |    "/static" : {
            |      "get" : {
            |        "requestBody" :
            |          {
            |          "content" : {
            |            "application/json" : {
            |              "schema" :
            |                {
            |                "$ref" : "#/components/schemas/SimpleInputBody"
            |              },
            |              "examples" : {
            |                "john" :
            |                  {
            |                  "value" : {
            |                    "name" : "John",
            |                    "age" : 42
            |                  }
            |                },
            |                "jane" :
            |                  {
            |                  "value" : {
            |                    "name" : "Jane",
            |                    "age" : 43
            |                  }
            |                }
            |              }
            |            }
            |          },
            |          "required" : true
            |        },
            |        "responses" : {
            |          "default" :
            |            {
            |            "description" : "",
            |            "content" : {
            |              "application/json" : {
            |                "schema" :
            |                  {
            |                  "anyOf" : [
            |                    {
            |                      "$ref" : "#/components/schemas/SimpleOutputBody"
            |                    },
            |                    {
            |                      "$ref" : "#/components/schemas/NotFoundError"
            |                    }
            |                  ],
            |                  "description" : ""
            |                },
            |                "examples" : {
            |                  "john" :
            |                    {
            |                    "value" : {
            |                      "userName" : "John",
            |                      "score" : 42
            |                    }
            |                  },
            |                  "jane" :
            |                    {
            |                    "value" : {
            |                      "userName" : "Jane",
            |                      "score" : 43
            |                    }
            |                  },
            |                  "not found" :
            |                    {
            |                    "value" : {
            |                      "message" : "not found"
            |                    }
            |                  }
            |                }
            |              }
            |            }
            |          }
            |        },
            |        "deprecated" : false
            |      }
            |    }
            |  },
            |  "components" : {
            |    "schemas" : {
            |      "SimpleInputBody" :
            |        {
            |        "type" :
            |          "object",
            |        "properties" : {
            |          "name" : {
            |            "type" :
            |              "string"
            |          },
            |          "age" : {
            |            "type" :
            |              "integer",
            |            "format" : "int32"
            |          }
            |        },
            |        "additionalProperties" :
            |          true,
            |        "required" : [
            |          "name",
            |          "age"
            |        ]
            |      },
            |      "SimpleOutputBody" :
            |        {
            |        "type" :
            |          "object",
            |        "properties" : {
            |          "userName" : {
            |            "type" :
            |              "string"
            |          },
            |          "score" : {
            |            "type" :
            |              "integer",
            |            "format" : "int32"
            |          }
            |        },
            |        "additionalProperties" :
            |          true,
            |        "required" : [
            |          "userName",
            |          "score"
            |        ]
            |      },
            |      "NotFoundError" :
            |        {
            |        "type" :
            |          "object",
            |        "properties" : {
            |          "message" : {
            |            "type" :
            |              "string"
            |          }
            |        },
            |        "additionalProperties" :
            |          true,
            |        "required" : [
            |          "message"
            |        ]
            |      }
            |    }
            |  }
            |}""".stripMargin
        assertTrue(json == toJsonAst(expectedJson))
      },
      test("with query parameter, alternative input, alternative output and examples") {
        val endpoint =
          Endpoint(GET / "static")
            .inCodec(
              HttpCodec
                .content[OtherSimpleInputBody] ?? Doc.p("other input") |
                HttpCodec
                  .content[SimpleInputBody] ?? Doc.p("simple input"),
            )
            .query(QueryCodec.paramStr("query"))
            .outCodec(
              HttpCodec
                .content[SimpleOutputBody] ?? Doc.p("simple output") |
                HttpCodec
                  .content[NotFoundError] ?? Doc.p("not found"),
            )

        val generated    = OpenAPIGen.fromEndpoints("Simple Endpoint", "1.0", endpoint)
        val json         = toJsonAst(generated)
        val expectedJson =
          """{
            |  "openapi" : "3.1.0",
            |  "info" : {
            |    "title" : "Simple Endpoint",
            |    "version" : "1.0"
            |  },
            |  "paths" : {
            |    "/static" : {
            |      "get" : {
            |        "parameters" : [
            |
            |            {
            |            "name" : "query",
            |            "in" : "query",
            |            "required" : true,
            |            "deprecated" : false,
            |            "schema" :
            |              {
            |              "type" :
            |                "string"
            |            },
            |            "explode" : false,
            |            "allowReserved" : false,
            |            "style" : "form"
            |          }
            |        ],
            |        "requestBody" :
            |          {
            |          "content" : {
            |            "application/json" : {
            |              "schema" :
            |                {
            |                "anyOf" : [
            |                  {
            |                    "$ref" : "#/components/schemas/OtherSimpleInputBody",
            |                    "description" : "other input\n\n"
            |                  },
            |                  {
            |                    "$ref" : "#/components/schemas/SimpleInputBody",
            |                    "description" : "simple input\n\n"
            |                  }
            |                ],
            |                "description" : ""
            |              }
            |            }
            |          },
            |          "required" : true
            |        },
            |        "responses" : {
            |          "default" :
            |            {
            |            "description" : "",
            |            "content" : {
            |              "application/json" : {
            |                "schema" :
            |                  {
            |                  "anyOf" : [
            |                    {
            |                      "$ref" : "#/components/schemas/SimpleOutputBody",
            |                      "description" : "simple output\n\n"
            |                    },
            |                    {
            |                      "$ref" : "#/components/schemas/NotFoundError",
            |                      "description" : "not found\n\n"
            |                    }
            |                  ],
            |                  "description" : ""
            |                }
            |              }
            |            }
            |          }
            |        },
            |        "deprecated" : false
            |      }
            |    }
            |  },
            |  "components" : {
            |    "schemas" : {
            |      "OtherSimpleInputBody" :
            |        {
            |        "type" :
            |          "object",
            |        "properties" : {
            |          "fullName" : {
            |            "type" :
            |              "string"
            |          },
            |          "shoeSize" : {
            |            "type" :
            |              "integer",
            |            "format" : "int32"
            |          }
            |        },
            |        "additionalProperties" :
            |          true,
            |        "required" : [
            |          "fullName",
            |          "shoeSize"
            |        ]
            |      },
            |      "SimpleInputBody" :
            |        {
            |        "type" :
            |          "object",
            |        "properties" : {
            |          "name" : {
            |            "type" :
            |              "string"
            |          },
            |          "age" : {
            |            "type" :
            |              "integer",
            |            "format" : "int32"
            |          }
            |        },
            |        "additionalProperties" :
            |          true,
            |        "required" : [
            |          "name",
            |          "age"
            |        ]
            |      },
            |      "SimpleOutputBody" :
            |        {
            |        "type" :
            |          "object",
            |        "properties" : {
            |          "userName" : {
            |            "type" :
            |              "string"
            |          },
            |          "score" : {
            |            "type" :
            |              "integer",
            |            "format" : "int32"
            |          }
            |        },
            |        "additionalProperties" :
            |          true,
            |        "required" : [
            |          "userName",
            |          "score"
            |        ]
            |      },
            |      "NotFoundError" :
            |        {
            |        "type" :
            |          "object",
            |        "properties" : {
            |          "message" : {
            |            "type" :
            |              "string"
            |          }
            |        },
            |        "additionalProperties" :
            |          true,
            |        "required" : [
            |          "message"
            |        ]
            |      }
            |    }
            |  }
            |}""".stripMargin
        assertTrue(json == toJsonAst(expectedJson))
      },
      test("multipart") {
        val endpoint  = Endpoint(GET / "test-form")
          .outCodec(
            (HttpCodec.contentStream[Byte]("image", MediaType.image.png) ++
              HttpCodec.content[String]("title").optional) ?? Doc.p("Test doc") ++
              HttpCodec.content[Int]("width") ++
              HttpCodec.content[Int]("height") ++
              HttpCodec.content[ImageMetadata]("metadata"),
          )
        val generated = OpenAPIGen.fromEndpoints("Simple Endpoint", "1.0", endpoint)
        val json      = toJsonAst(generated)
        val expected  = """{
                         |  "openapi" : "3.1.0",
                         |  "info" : {
                         |    "title" : "Simple Endpoint",
                         |    "version" : "1.0"
                         |  },
                         |  "paths" : {
                         |    "/test-form" : {
                         |      "get" : {
                         |        "requestBody" :
                         |          {
                         |          "content" : {
                         |            "application/json" : {
                         |              "schema" :
                         |                {
                         |                "type" :
                         |                  "null"
                         |              }
                         |            }
                         |          },
                         |          "required" : false
                         |        },
                         |        "responses" : {
                         |          "default" :
                         |            {
                         |            "description" : "",
                         |            "content" : {
                         |              "multipart/form-data" : {
                         |                "schema" :
                         |                  {
                         |                  "type" :
                         |                    "object",
                         |                  "properties" : {
                         |                    "image" : {
                         |                      "type" :
                         |                        "string",
                         |                      "contentEncoding" : "binary",
                         |                      "contentMediaType" : "application/octet-stream"
                         |                    },
                         |                    "height" : {
                         |                      "type" :
                         |                        "integer",
                         |                      "format" : "int32"
                         |                    },
                         |                    "metadata" : {
                         |                      "$ref" : "#/components/schemas/ImageMetadata"
                         |                    },
                         |                    "title" : {
                         |                      "type" :
                         |                        [
                         |                        "string",
                         |                        "null"
                         |                      ]
                         |                    },
                         |                    "width" : {
                         |                      "type" :
                         |                        "integer",
                         |                      "format" : "int32"
                         |                    }
                         |                  },
                         |                  "additionalProperties" :
                         |                    false,
                         |                  "required" : [
                         |                    "image",
                         |                    "width",
                         |                    "height",
                         |                    "metadata"
                         |                  ],
                         |                  "description" : "Test doc\n\n"
                         |                }
                         |              }
                         |            }
                         |          }
                         |        },
                         |        "deprecated" : false
                         |      }
                         |    }
                         |  },
                         |  "components" : {
                         |    "schemas" : {
                         |      "ImageMetadata" :
                         |        {
                         |        "type" :
                         |          "object",
                         |        "properties" : {
                         |          "name" : {
                         |            "type" :
                         |              "string"
                         |          },
                         |          "size" : {
                         |            "type" :
                         |              "integer",
                         |            "format" : "int32"
                         |          }
                         |        },
                         |        "additionalProperties" :
                         |          true,
                         |        "required" : [
                         |          "name",
                         |          "size"
                         |        ]
                         |      }
                         |    }
                         |  }
                         |}""".stripMargin
        assertTrue(json == toJsonAst(expected))
      },
      test("multiple endpoint definitions") {
        val generated =
          OpenAPIGen.fromEndpoints(
            "Simple Endpoint",
            "1.0",
            simpleEndpoint,
            queryParamEndpoint,
            alternativeInputEndpoint,
          )
        val json      = toJsonAst(generated)
        val expected  =
          """{
            |  "openapi" : "3.1.0",
            |  "info" : {
            |    "title" : "Simple Endpoint",
            |    "version" : "1.0"
            |  },
            |  "paths" : {
            |    "/static/{id}/{uuid}/{name}" : {
            |      "get" : {
            |        "parameters" : [
            |
            |            {
            |            "name" : "id",
            |            "in" : "path",
            |            "required" : true,
            |            "deprecated" : false,
            |            "schema" :
            |              {
            |              "type" :
            |                "integer",
            |              "format" : "int32"
            |            },
            |            "explode" : false,
            |            "style" : "simple"
            |          },
            |
            |            {
            |            "name" : "uuid",
            |            "in" : "path",
            |            "description" : "user id\n\n",
            |            "required" : true,
            |            "deprecated" : false,
            |            "schema" :
            |              {
            |              "type" :
            |                "string"
            |            },
            |            "explode" : false,
            |            "style" : "simple"
            |          },
            |
            |            {
            |            "name" : "name",
            |            "in" : "path",
            |            "required" : true,
            |            "deprecated" : false,
            |            "schema" :
            |              {
            |              "type" :
            |                "string"
            |            },
            |            "explode" : false,
            |            "style" : "simple"
            |          }
            |        ],
            |        "requestBody" :
            |          {
            |          "content" : {
            |            "application/json" : {
            |              "schema" :
            |                {
            |                "$ref" : "#/components/schemas/SimpleInputBody",
            |                "description" : "input body\n\n"
            |              }
            |            }
            |          },
            |          "required" : true
            |        },
            |        "responses" : {
            |          "200" :
            |            {
            |            "description" : "",
            |            "content" : {
            |              "application/json" : {
            |                "schema" :
            |                  {
            |                  "$ref" : "#/components/schemas/SimpleOutputBody",
            |                  "description" : "output body\n\n"
            |                }
            |              }
            |            }
            |          },
            |          "404" :
            |            {
            |            "description" : "",
            |            "content" : {
            |              "application/json" : {
            |                "schema" :
            |                  {
            |                  "$ref" : "#/components/schemas/NotFoundError",
            |                  "description" : "not found\n\n"
            |                }
            |              }
            |            }
            |          }
            |        },
            |        "deprecated" : false
            |      }
            |    },
            |    "/withQuery" : {
            |      "get" : {
            |        "parameters" : [
            |
            |            {
            |            "name" : "query",
            |            "in" : "query",
            |            "required" : true,
            |            "deprecated" : false,
            |            "schema" :
            |              {
            |              "type" :
            |                "string"
            |            },
            |            "explode" : false,
            |            "allowReserved" : false,
            |            "style" : "form"
            |          }
            |        ],
            |        "requestBody" :
            |          {
            |          "content" : {
            |            "application/json" : {
            |              "schema" :
            |                {
            |                "$ref" : "#/components/schemas/SimpleInputBody"
            |              }
            |            }
            |          },
            |          "required" : true
            |        },
            |        "responses" : {
            |          "200" :
            |            {
            |            "description" : "",
            |            "content" : {
            |              "application/json" : {
            |                "schema" :
            |                  {
            |                  "$ref" : "#/components/schemas/SimpleOutputBody"
            |                }
            |              }
            |            }
            |          },
            |          "404" :
            |            {
            |            "description" : "",
            |            "content" : {
            |              "application/json" : {
            |                "schema" :
            |                  {
            |                  "$ref" : "#/components/schemas/NotFoundError"
            |                }
            |              }
            |            }
            |          }
            |        },
            |        "deprecated" : false
            |      }
            |    },
            |    "/inputAlternative" : {
            |      "get" : {
            |        "requestBody" :
            |          {
            |          "content" : {
            |            "application/json" : {
            |              "schema" :
            |                {
            |                "anyOf" : [
            |                  {
            |                    "$ref" : "#/components/schemas/OtherSimpleInputBody",
            |                    "description" : "other input\n\n"
            |                  },
            |                  {
            |                    "$ref" : "#/components/schemas/SimpleInputBody",
            |                    "description" : "simple input\n\n"
            |                  }
            |                ],
            |                "description" : "takes either of the two input bodies\n\n"
            |              }
            |            }
            |          },
            |          "required" : true
            |        },
            |        "responses" : {
            |          "200" :
            |            {
            |            "description" : "",
            |            "content" : {
            |              "application/json" : {
            |                "schema" :
            |                  {
            |                  "$ref" : "#/components/schemas/SimpleOutputBody"
            |                }
            |              }
            |            }
            |          },
            |          "404" :
            |            {
            |            "description" : "",
            |            "content" : {
            |              "application/json" : {
            |                "schema" :
            |                  {
            |                  "$ref" : "#/components/schemas/NotFoundError"
            |                }
            |              }
            |            }
            |          }
            |        },
            |        "deprecated" : false
            |      }
            |    }
            |  },
            |  "components" : {
            |    "schemas" : {
            |      "SimpleInputBody" :
            |        {
            |        "type" :
            |          "object",
            |        "properties" : {
            |          "name" : {
            |            "type" :
            |              "string"
            |          },
            |          "age" : {
            |            "type" :
            |              "integer",
            |            "format" : "int32"
            |          }
            |        },
            |        "additionalProperties" :
            |          true,
            |        "required" : [
            |          "name",
            |          "age"
            |        ]
            |      },
            |      "NotFoundError" :
            |        {
            |        "type" :
            |          "object",
            |        "properties" : {
            |          "message" : {
            |            "type" :
            |              "string"
            |          }
            |        },
            |        "additionalProperties" :
            |          true,
            |        "required" : [
            |          "message"
            |        ]
            |      },
            |      "SimpleOutputBody" :
            |        {
            |        "type" :
            |          "object",
            |        "properties" : {
            |          "userName" : {
            |            "type" :
            |              "string"
            |          },
            |          "score" : {
            |            "type" :
            |              "integer",
            |            "format" : "int32"
            |          }
            |        },
            |        "additionalProperties" :
            |          true,
            |        "required" : [
            |          "userName",
            |          "score"
            |        ]
            |      },
            |      "OtherSimpleInputBody" :
            |        {
            |        "type" :
            |          "object",
            |        "properties" : {
            |          "fullName" : {
            |            "type" :
            |              "string"
            |          },
            |          "shoeSize" : {
            |            "type" :
            |              "integer",
            |            "format" : "int32"
            |          }
            |        },
            |        "additionalProperties" :
            |          true,
            |        "required" : [
            |          "fullName",
            |          "shoeSize"
            |        ]
            |      }
            |    }
            |  }
            |}""".stripMargin
        assertTrue(json == toJsonAst(expected))
      },
      test("transient field") {
        val endpoint  = Endpoint(GET / "static").in[WithTransientField]
        val generated = OpenAPIGen.fromEndpoints("Simple Endpoint", "1.0", endpoint)
        val json      = toJsonAst(generated)
        val expected  = """{
                         |  "openapi" : "3.1.0",
                         |  "info" : {
                         |    "title" : "Simple Endpoint",
                         |    "version" : "1.0"
                         |  },
                         |  "paths" : {
                         |    "/static" : {
                         |      "get" : {
                         |        "requestBody" :
                         |          {
                         |          "content" : {
                         |            "application/json" : {
                         |              "schema" :
                         |                {
                         |                "$ref" : "#/components/schemas/WithTransientField"
                         |              }
                         |            }
                         |          },
                         |          "required" : true
                         |        },
                         |        "deprecated" : false
                         |      }
                         |    }
                         |  },
                         |  "components" : {
                         |    "schemas" : {
                         |      "WithTransientField" :
                         |        {
                         |        "type" :
                         |          "object",
                         |        "properties" : {
                         |          "name" : {
                         |            "type" :
                         |              "string"
                         |          }
                         |        },
                         |        "additionalProperties" :
                         |          true,
                         |        "required" : [
                         |          "name"
                         |        ]
                         |      }
                         |    }
                         |  }
                         |}""".stripMargin
        assertTrue(json == toJsonAst(expected))
      },
      test("primitive default value") {
        val endpoint  = Endpoint(GET / "static").in[WithDefaultValue]
        val generated = OpenAPIGen.fromEndpoints("Simple Endpoint", "1.0", endpoint)
        val json      = toJsonAst(generated)
        val expected  =
          """{
            |  "openapi" : "3.1.0",
            |  "info" : {
            |    "title" : "Simple Endpoint",
            |    "version" : "1.0"
            |  },
            |  "paths" : {
            |    "/static" : {
            |      "get" : {
            |        "requestBody" :
            |          {
            |          "content" : {
            |            "application/json" : {
            |              "schema" :
            |                {
            |                "$ref" : "#/components/schemas/WithDefaultValue"
            |              }
            |            }
            |          },
            |          "required" : true
            |        },
            |        "deprecated" : false
            |      }
            |    }
            |  },
            |  "components" : {
            |    "schemas" : {
            |      "WithDefaultValue" :
            |        {
            |        "type" :
            |          "object",
            |        "properties" : {
            |          "age" : {
            |            "type" :
            |              "integer",
            |            "format" : "int32",
            |            "description" : "If not set, this field defaults to the value of the default annotation.",
            |            "default" : 42
            |          }
            |        },
            |        "additionalProperties" :
            |          true
            |      }
            |    }
            |  }
            |}""".stripMargin
        assertTrue(json == toJsonAst(expected))
      },
      test("complex default value") {
        val endpoint  = Endpoint(GET / "static").in[WithComplexDefaultValue]
        val generated = OpenAPIGen.fromEndpoints("Simple Endpoint", "1.0", endpoint)
        val json      = toJsonAst(generated)
        val expected  =
          """{
            |  "openapi" : "3.1.0",
            |  "info" : {
            |    "title" : "Simple Endpoint",
            |    "version" : "1.0"
            |  },
            |  "paths" : {
            |    "/static" : {
            |      "get" : {
            |        "requestBody" :
            |          {
            |          "content" : {
            |            "application/json" : {
            |              "schema" :
            |                {
            |                "$ref" : "#/components/schemas/WithComplexDefaultValue"
            |              }
            |            }
            |          },
            |          "required" : true
            |        },
            |        "deprecated" : false
            |      }
            |    }
            |  },
            |  "components" : {
            |    "schemas" : {
            |      "WithComplexDefaultValue" :
            |        {
            |        "type" :
            |          "object",
            |        "properties" : {
            |          "data" : {
            |            "type" :
            |              "object",
            |            "properties" : {
            |              "name" : {
            |                "type" :
            |                  "string"
            |              },
            |              "size" : {
            |                "type" :
            |                  "integer",
            |                "format" : "int32"
            |              }
            |            },
            |            "additionalProperties" :
            |              true,
            |            "required" : [
            |              "name",
            |              "size"
            |            ],
            |            "description" : "If not set, this field defaults to the value of the default annotation.",
            |            "default" : {
            |              "name" : "default",
            |              "size" : 42
            |            }
            |          }
            |        },
            |        "additionalProperties" :
            |          true
            |      }
            |    }
            |  }
            |}""".stripMargin
        assertTrue(json == toJsonAst(expected))
      },
      test("optional field") {
        val endpoint  = Endpoint(GET / "static").in[WithOptionalField]
        val generated = OpenAPIGen.fromEndpoints("Simple Endpoint", "1.0", endpoint)
        val json      = toJsonAst(generated)
        val expected  = """{
                         |  "openapi" : "3.1.0",
                         |  "info" : {
                         |    "title" : "Simple Endpoint",
                         |    "version" : "1.0"
                         |  },
                         |  "paths" : {
                         |    "/static" : {
                         |      "get" : {
                         |        "requestBody" :
                         |          {
                         |          "content" : {
                         |            "application/json" : {
                         |              "schema" :
                         |                {
                         |                "$ref" : "#/components/schemas/WithOptionalField"
                         |              }
                         |            }
                         |          },
                         |          "required" : true
                         |        },
                         |        "deprecated" : false
                         |      }
                         |    }
                         |  },
                         |  "components" : {
                         |    "schemas" : {
                         |      "WithOptionalField" :
                         |        {
                         |        "type" :
                         |          "object",
                         |        "properties" : {
                         |          "name" : {
                         |            "type" :
                         |              "string"
                         |          },
                         |          "age" : {
                         |            "type" :
                         |              "integer",
                         |            "format" : "int32"
                         |          }
                         |        },
                         |        "additionalProperties" :
                         |          true,
                         |        "required" : [
                         |          "name"
                         |        ]
                         |      }
                         |    }
                         |  }
                         |}""".stripMargin
        assertTrue(json == toJsonAst(expected))
      },
      test("enum") {
        val endpoint  = Endpoint(GET / "static").in[SimpleEnum]
        val generated = OpenAPIGen.fromEndpoints("Simple Endpoint", "1.0", endpoint)
        val json      = toJsonAst(generated)
        val expected  = """{
                         |  "openapi" : "3.1.0",
                         |  "info" : {
                         |    "title" : "Simple Endpoint",
                         |    "version" : "1.0"
                         |  },
                         |  "paths" : {
                         |    "/static" : {
                         |      "get" : {
                         |        "requestBody" :
                         |          {
                         |          "content" : {
                         |            "application/json" : {
                         |              "schema" :
                         |                {
                         |                "$ref" : "#/components/schemas/SimpleEnum"
                         |              }
                         |            }
                         |          },
                         |          "required" : true
                         |        },
                         |        "deprecated" : false
                         |      }
                         |    }
                         |  },
                         |  "components" : {
                         |    "schemas" : {
                         |      "SimpleEnum" :
                         |        {
                         |        "type" :
                         |          "string",
                         |        "enumValues" : [
                         |          "One",
                         |          "Two",
                         |          "Three"
                         |        ]
                         |      }
                         |    }
                         |  }
                         |}""".stripMargin
        assertTrue(json == toJsonAst(expected))
      },
      test("sealed trait default discriminator") {
        val endpoint     = Endpoint(GET / "static").in[SealedTraitDefaultDiscriminator]
        val generated    = OpenAPIGen.fromEndpoints("Simple Endpoint", "1.0", endpoint)
        val json         = toJsonAst(generated)
        val expectedJson =
          """{
            |  "openapi" : "3.1.0",
            |  "info" : {
            |    "title" : "Simple Endpoint",
            |    "version" : "1.0"
            |  },
            |  "paths" : {
            |    "/static" : {
            |      "get" : {
            |        "requestBody" :
            |          {
            |          "content" : {
            |            "application/json" : {
            |              "schema" :
            |                {
            |                "$ref" : "#/components/schemas/SealedTraitDefaultDiscriminator"
            |              }
            |            }
            |          },
            |          "required" : true
            |        },
            |        "deprecated" : false
            |      }
            |    }
            |  },
            |  "components" : {
            |    "schemas" : {
            |      "One" :
            |        {
            |        "type" :
            |          "object",
            |        "properties" : {},
            |        "additionalProperties" :
            |          true
            |      },
            |      "Two" :
            |        {
            |        "type" :
            |          "object",
            |        "properties" : {
            |          "name" : {
            |            "type" :
            |              "string"
            |          }
            |        },
            |        "additionalProperties" :
            |          true,
            |        "required" : [
            |          "name"
            |        ]
            |      },
            |      "Three" :
            |        {
            |        "type" :
            |          "object",
            |        "properties" : {
            |          "name" : {
            |            "type" :
            |              "string"
            |          }
            |        },
            |        "additionalProperties" :
            |          true,
            |        "required" : [
            |          "name"
            |        ]
            |      },
            |      "SealedTraitDefaultDiscriminator" :
            |        {
            |        "oneOf" : [
            |          {
            |            "type" :
            |              "object",
            |            "properties" : {
            |              "One" : {
            |                "$ref" : "#/components/schemas/One"
            |              }
            |            },
            |            "additionalProperties" :
            |              false,
            |            "required" : [
            |              "One"
            |            ]
            |          },
            |          {
            |            "type" :
            |              "object",
            |            "properties" : {
            |              "Two" : {
            |                "$ref" : "#/components/schemas/Two"
            |              }
            |            },
            |            "additionalProperties" :
            |              false,
            |            "required" : [
            |              "Two"
            |            ]
            |          },
            |          {
            |            "type" :
            |              "object",
            |            "properties" : {
            |              "three" : {
            |                "$ref" : "#/components/schemas/Three"
            |              }
            |            },
            |            "additionalProperties" :
            |              false,
            |            "required" : [
            |              "three"
            |            ]
            |          }
            |        ]
            |      }
            |    }
            |  }
            |}""".stripMargin
        assertTrue(json == toJsonAst(expectedJson))
      },
      test("sealed trait custom discriminator") {
        val endpoint     = Endpoint(GET / "static").in[SealedTraitCustomDiscriminator]
        val generated    = OpenAPIGen.fromEndpoints("Simple Endpoint", "1.0", endpoint)
        val json         = toJsonAst(generated)
        val expectedJson =
          """{
            |  "openapi" : "3.1.0",
            |  "info" : {
            |    "title" : "Simple Endpoint",
            |    "version" : "1.0"
            |  },
            |  "paths" : {
            |    "/static" : {
            |      "get" : {
            |        "requestBody" :
            |          {
            |          "content" : {
            |            "application/json" : {
            |              "schema" :
            |                {
            |                "$ref" : "#/components/schemas/SealedTraitCustomDiscriminator"
            |              }
            |            }
            |          },
            |          "required" : true
            |        },
            |        "deprecated" : false
            |      }
            |    }
            |  },
            |  "components" : {
            |    "schemas" : {
            |      "One" :
            |        {
            |        "type" :
            |          "object",
            |        "properties" : {},
            |        "additionalProperties" :
            |          true
            |      },
            |      "Two" :
            |        {
            |        "type" :
            |          "object",
            |        "properties" : {
            |          "name" : {
            |            "type" :
            |              "string"
            |          }
            |        },
            |        "additionalProperties" :
            |          true,
            |        "required" : [
            |          "name"
            |        ]
            |      },
            |      "Three" :
            |        {
            |        "type" :
            |          "object",
            |        "properties" : {
            |          "name" : {
            |            "type" :
            |              "string"
            |          }
            |        },
            |        "additionalProperties" :
            |          true,
            |        "required" : [
            |          "name"
            |        ]
            |      },
            |      "SealedTraitCustomDiscriminator" :
            |        {
            |        "oneOf" : [
            |          {
            |            "$ref" : "#/components/schemas/One"
            |          },
            |          {
            |            "$ref" : "#/components/schemas/Two"
            |          },
            |          {
            |            "$ref" : "#/components/schemas/Three"
            |          }
            |        ],
            |        "discriminator" : {
            |          "propertyName" : "type",
            |          "mapping" : {
            |            "One" : "#/components/schemas/One}",
            |            "Two" : "#/components/schemas/Two}",
            |            "three" : "#/components/schemas/Three}"
            |          }
            |        }
            |      }
            |    }
            |  }
            |}""".stripMargin
        assertTrue(json == toJsonAst(expectedJson))
      },
      test("sealed trait no discriminator") {
        val endpoint  = Endpoint(GET / "static").in[SealedTraitNoDiscriminator]
        val generated = OpenAPIGen.fromEndpoints("Simple Endpoint", "1.0", endpoint)
        val json      = toJsonAst(generated)
        val expected  = """{
                         |  "openapi" : "3.1.0",
                         |  "info" : {
                         |    "title" : "Simple Endpoint",
                         |    "version" : "1.0"
                         |  },
                         |  "paths" : {
                         |    "/static" : {
                         |      "get" : {
                         |        "requestBody" :
                         |          {
                         |          "content" : {
                         |            "application/json" : {
                         |              "schema" :
                         |                {
                         |                "$ref" : "#/components/schemas/SealedTraitNoDiscriminator"
                         |              }
                         |            }
                         |          },
                         |          "required" : true
                         |        },
                         |        "deprecated" : false
                         |      }
                         |    }
                         |  },
                         |  "components" : {
                         |    "schemas" : {
                         |      "One" :
                         |        {
                         |        "type" :
                         |          "object",
                         |        "properties" : {},
                         |        "additionalProperties" :
                         |          true
                         |      },
                         |      "Two" :
                         |        {
                         |        "type" :
                         |          "object",
                         |        "properties" : {
                         |          "name" : {
                         |            "type" :
                         |              "string"
                         |          }
                         |        },
                         |        "additionalProperties" :
                         |          true,
                         |        "required" : [
                         |          "name"
                         |        ]
                         |      },
                         |      "Three" :
                         |        {
                         |        "type" :
                         |          "object",
                         |        "properties" : {
                         |          "name" : {
                         |            "type" :
                         |              "string"
                         |          }
                         |        },
                         |        "additionalProperties" :
                         |          true,
                         |        "required" : [
                         |          "name"
                         |        ]
                         |      },
                         |      "SealedTraitNoDiscriminator" :
                         |        {
                         |        "oneOf" : [
                         |          {
                         |            "$ref" : "#/components/schemas/One"
                         |          },
                         |          {
                         |            "$ref" : "#/components/schemas/Two"
                         |          },
                         |          {
                         |            "$ref" : "#/components/schemas/Three"
                         |          }
                         |        ]
                         |      }
                         |    }
                         |  }
                         |}
                         |""".stripMargin
        assertTrue(json == toJsonAst(expected))
      },
      test("sealed trait with nested sealed trait") {
        val endpoint     = Endpoint(GET / "static").in[SimpleNestedSealedTrait]
        val generated    = OpenAPIGen.fromEndpoints("Simple Endpoint", "1.0", endpoint)
        val json         = toJsonAst(generated)
        val expectedJson =
          """{
            |  "openapi" : "3.1.0",
            |  "info" : {
            |    "title" : "Simple Endpoint",
            |    "version" : "1.0"
            |  },
            |  "paths" : {
            |    "/static" : {
            |      "get" : {
            |        "requestBody" :
            |          {
            |          "content" : {
            |            "application/json" : {
            |              "schema" :
            |                {
            |                "$ref" : "#/components/schemas/SimpleNestedSealedTrait"
            |              }
            |            }
            |          },
            |          "required" : true
            |        },
            |        "deprecated" : false
            |      }
            |    }
            |  },
            |  "components" : {
            |    "schemas" : {
            |      "NestedOne" :
            |        {
            |        "type" :
            |          "object",
            |        "properties" : {},
            |        "additionalProperties" :
            |          true
            |      },
            |      "NestedThree" :
            |        {
            |        "type" :
            |          "object",
            |        "properties" : {
            |          "name" : {
            |            "type" :
            |              "string"
            |          }
            |        },
            |        "additionalProperties" :
            |          true,
            |        "required" : [
            |          "name"
            |        ]
            |      },
            |      "NestedTwo" :
            |        {
            |        "type" :
            |          "object",
            |        "properties" : {
            |          "name" : {
            |            "oneOf" : [
            |              {
            |                "$ref" : "#/components/schemas/One"
            |              },
            |              {
            |                "$ref" : "#/components/schemas/Two"
            |              },
            |              {
            |                "$ref" : "#/components/schemas/Three"
            |              }
            |            ]
            |          }
            |        },
            |        "additionalProperties" :
            |          true,
            |        "required" : [
            |          "name"
            |        ]
            |      },
            |      "Two" :
            |        {
            |        "type" :
            |          "object",
            |        "properties" : {
            |          "name" : {
            |            "type" :
            |              "string"
            |          }
            |        },
            |        "additionalProperties" :
            |          true,
            |        "required" : [
            |          "name"
            |        ]
            |      },
            |      "Three" :
            |        {
            |        "type" :
            |          "object",
            |        "properties" : {
            |          "name" : {
            |            "type" :
            |              "string"
            |          }
            |        },
            |        "additionalProperties" :
            |          true,
            |        "required" : [
            |          "name"
            |        ]
            |      },
            |      "One" :
            |        {
            |        "type" :
            |          "object",
            |        "properties" : {},
            |        "additionalProperties" :
            |          true
            |      },
            |      "SimpleNestedSealedTrait" :
            |        {
            |        "oneOf" : [
            |          {
            |            "$ref" : "#/components/schemas/NestedOne"
            |          },
            |          {
            |            "$ref" : "#/components/schemas/NestedTwo"
            |          },
            |          {
            |            "$ref" : "#/components/schemas/NestedThree"
            |          }
            |        ]
            |      }
            |    }
            |  }
            |}""".stripMargin
        assertTrue(json == toJsonAst(expectedJson))
      },
    )

}
