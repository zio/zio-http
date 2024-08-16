package zio.http.endpoint.openapi

import zio.json.ast.Json
import zio.test._
import zio.{Scope, ZIO}

import zio.schema.annotation._
import zio.schema.{DeriveSchema, Schema}

import zio.http.Method.{GET, POST}
import zio.http._
import zio.http.codec.PathCodec.string
import zio.http.codec.{ContentCodec, Doc, HttpCodec, HttpContentCodec, QueryCodec}
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

  final case class WithTransientField(name: String, @transientField age: Int = 42)
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

  final case class NestedProduct(imageMetadata: ImageMetadata, withOptionalField: WithOptionalField)
  implicit val nestedProductSchema: Schema[NestedProduct] =
    DeriveSchema.gen[NestedProduct]

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

  @description("A recursive structure")
  case class Recursive(
    nestedOption: Option[Recursive],
    nestedList: List[Recursive],
    nestedMap: Map[String, Recursive],
    nestedSet: Set[Recursive],
    nestedEither: Either[Recursive, Recursive],
    nestedTuple: (Recursive, Recursive),
    nestedOverAnother: NestedRecursive,
  )
  object Recursive               {
    implicit val schema: Schema[Recursive] = DeriveSchema.gen[Recursive]
  }
  case class NestedRecursive(next: Recursive)
  object NestedRecursive         {
    implicit val schema: Schema[NestedRecursive] = DeriveSchema.gen[NestedRecursive]
  }

  @description("A simple payload")
  case class Payload(content: String)

  object Payload {
    implicit val schema: Schema[Payload] = DeriveSchema.gen[Payload]
  }

  object Lazy {
    case class A(b: B)

    object A {
      implicit val schema: Schema[A] = DeriveSchema.gen
    }
    case class B(i: Int)
  }

  case class WithGenericPayload[A](a: A)

  object WithGenericPayload {
    implicit def schema[T: Schema]: Schema[WithGenericPayload[T]] = DeriveSchema.gen
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
      .query(HttpCodec.query[String]("query"))
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
        val generated    = OpenAPIGen.fromEndpoints("Simple Endpoint", "1.0", simpleEndpoint.tag("simple", "endpoint"))
        val json         = toJsonAst(generated)
        val expectedJson = """{
                             |  "openapi" : "3.1.0",
                             |  "info" : {
                             |    "title" : "Simple Endpoint",
                             |    "version" : "1.0"
                             |  },
                             |  "paths" : {
                             |    "/static/{id}/{uuid}/{name}" : {
                             |      "description" : "- simple\n- endpoint\n",
                             |      "get" : {
                             |        "tags" : [
                             |          "simple",
                             |          "endpoint"
                             |        ],
                             |        "description" : "get path\n\n",
                             |        "parameters" : [
                             |          {
                             |            "name" : "id",
                             |            "in" : "path",
                             |            "required" : true,
                             |            "schema" : {
                             |              "type" : "integer",
                             |              "format" : "int32"
                             |            },
                             |            "style" : "simple"
                             |          },
                             |          {
                             |            "name" : "uuid",
                             |            "in" : "path",
                             |            "description" : "user id\n\n",
                             |            "required" : true,
                             |            "schema" : {
                             |              "type" : "string",
                             |              "format" : "uuid"
                             |            },
                             |            "style" : "simple"
                             |          },
                             |          {
                             |            "name" : "name",
                             |            "in" : "path",
                             |            "required" : true,
                             |            "schema" : {
                             |              "type" : "string"
                             |            },
                             |            "style" : "simple"
                             |          }
                             |        ],
                             |        "requestBody" : {
                             |          "content" : {
                             |            "application/json" : {
                             |              "schema" : {
                             |                "$ref" : "#/components/schemas/SimpleInputBody",
                             |                "description" : "input body\n\n"
                             |              }
                             |            }
                             |          },
                             |          "required" : true
                             |        },
                             |        "responses" : {
                             |          "200" : {
                             |            "content" : {
                             |              "application/json" : {
                             |                "schema" : {
                             |                  "$ref" : "#/components/schemas/SimpleOutputBody",
                             |                  "description" : "output body\n\n"
                             |                }
                             |              }
                             |            }
                             |          },
                             |          "404" : {
                             |            "content" : {
                             |              "application/json" : {
                             |                "schema" : {
                             |                  "$ref" : "#/components/schemas/NotFoundError",
                             |                  "description" : "not found\n\n"
                             |                }
                             |              }
                             |            }
                             |          }
                             |        }
                             |      }
                             |    }
                             |  },
                             |  "components" : {
                             |    "schemas" : {
                             |      "NotFoundError" : {
                             |        "type" : "object",
                             |        "properties" : {
                             |          "message" : {
                             |            "type" : "string"
                             |          }
                             |        },
                             |        "required" : [
                             |          "message"
                             |        ]
                             |      },
                             |      "SimpleInputBody" : {
                             |        "type" : "object",
                             |        "properties" : {
                             |          "name" : {
                             |            "type" : "string"
                             |          },
                             |          "age" : {
                             |            "type" : "integer",
                             |            "format" : "int32"
                             |          }
                             |        },
                             |        "required" : [
                             |          "name",
                             |          "age"
                             |        ]
                             |      },
                             |      "SimpleOutputBody" : {
                             |        "type" : "object",
                             |        "properties" : {
                             |          "userName" : {
                             |            "type" : "string"
                             |          },
                             |          "score" : {
                             |            "type" : "integer",
                             |            "format" : "int32"
                             |          }
                             |        },
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
                             |
                             |            {
                             |            "name" : "query",
                             |            "in" : "query",
                             |            "required" : true,
                             |            "schema" :
                             |              {
                             |              "type" :
                             |                "string"
                             |            },
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
                             |            "content" : {
                             |              "application/json" : {
                             |                "schema" :
                             |                  {
                             |                  "$ref" : "#/components/schemas/NotFoundError"
                             |                }
                             |              }
                             |            }
                             |          }
                             |        }
                             |      }
                             |    }
                             |  },
                             |  "components" : {
                             |    "schemas" : {
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
                             |        "required" : [
                             |          "message"
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
            |            "content" : {
            |              "application/json" : {
            |                "schema" :
            |                  {
            |                  "$ref" : "#/components/schemas/NotFoundError"
            |                }
            |              }
            |            }
            |          }
            |        }
            |      }
            |    }
            |  },
            |  "components" : {
            |    "schemas" : {
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
            |        "required" : [
            |          "message"
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
            |              "schema" :
            |                {
            |                "$ref" : "#/components/schemas/SimpleInputBody"
            |              }
            |            }
            |          },
            |          "required" : true
            |        },
            |        "responses" : {
            |          "default" :
            |            {
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
            |                  "description" : "alternative outputs\n\n"
            |                }
            |              }
            |            }
            |          }
            |        }
            |      }
            |    }
            |  },
            |  "components" : {
            |    "schemas" : {
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
            |        "required" : [
            |          "message"
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
            .examplesOut("other" -> Right(NotFoundError("other")))

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
            |        "requestBody" : {
            |          "content" : {
            |            "application/json" : {
            |              "schema" : {
            |                "$ref" : "#/components/schemas/SimpleInputBody"
            |              },
            |              "examples" : {
            |                "john" : {
            |                  "value" : {
            |                    "name" : "John",
            |                    "age" : 42
            |                  }
            |                },
            |                "jane" : {
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
            |          "default" : {
            |            "content" : {
            |              "application/json" : {
            |                "schema" : {
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
            |                  "john" : {
            |                    "value" : {
            |                      "userName" : "John",
            |                      "score" : 42
            |                    }
            |                  },
            |                  "jane" : {
            |                    "value" : {
            |                      "userName" : "Jane",
            |                      "score" : 43
            |                    }
            |                  },
            |                  "other" : {
            |                    "value" : {
            |                      "message" : "other"
            |                    }
            |                  },
            |                  "not found" : {
            |                    "value" : {
            |                      "message" : "not found"
            |                    }
            |                  }
            |                }
            |              }
            |            }
            |          }
            |        }
            |      }
            |    }
            |  },
            |  "components" : {
            |    "schemas" : {
            |      "NotFoundError" : {
            |        "type" : "object",
            |        "properties" : {
            |          "message" : {
            |            "type" : "string"
            |          }
            |        },
            |        "required" : [
            |          "message"
            |        ]
            |      },
            |      "SimpleInputBody" : {
            |        "type" : "object",
            |        "properties" : {
            |          "name" : {
            |            "type" : "string"
            |          },
            |          "age" : {
            |            "type" : "integer",
            |            "format" : "int32"
            |          }
            |        },
            |        "required" : [
            |          "name",
            |          "age"
            |        ]
            |      },
            |      "SimpleOutputBody" : {
            |        "type" : "object",
            |        "properties" : {
            |          "userName" : {
            |            "type" : "string"
            |          },
            |          "score" : {
            |            "type" : "integer",
            |            "format" : "int32"
            |          }
            |        },
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
      test("with query parameter, alternative input, alternative output and examples") {
        val endpoint =
          Endpoint(GET / "static")
            .inCodec(
              HttpCodec
                .content[OtherSimpleInputBody] ?? Doc.p("other input") |
                HttpCodec
                  .content[SimpleInputBody] ?? Doc.p("simple input"),
            )
            .query(HttpCodec.query[String]("query"))
            .outCodec(
              HttpCodec
                .content[SimpleOutputBody] ?? Doc.p("simple output") |
                HttpCodec.content[NotFoundError] ?? Doc.p("not found"),
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
            |            "schema" :
            |              {
            |              "type" :
            |                "string"
            |            },
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
            |        }
            |      }
            |    }
            |  },
            |  "components" : {
            |    "schemas" : {
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
            |        "required" : [
            |          "message"
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
      test("multipart") {
        val endpoint  = Endpoint(GET / "test-form")
          .outCodec(
            (HttpCodec.binaryStream("image", MediaType.image.png) ++
              HttpCodec.content[String]("title").optional ?? Doc.p("Test doc")) ++
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
                         |        "responses" : {
                         |          "default" :
                         |            {
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
                         |                      ],
                         |                      "description" : "Test doc\n\n"
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
                         |        }
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
            |            "content" : {
            |              "application/json" : {
            |                "schema" :
            |                  {
            |                  "$ref" : "#/components/schemas/NotFoundError"
            |                }
            |              }
            |            }
            |          }
            |        }
            |      }
            |    },
            |    "/static/{id}/{uuid}/{name}" : {
            |      "get" : {
            |        "description" : "get path\n\n",
            |        "parameters" : [
            |
            |            {
            |            "name" : "id",
            |            "in" : "path",
            |            "required" : true,
            |            "schema" :
            |              {
            |              "type" :
            |                "integer",
            |              "format" : "int32"
            |            },
            |            "style" : "simple"
            |          },
            |
            |            {
            |            "name" : "uuid",
            |            "in" : "path",
            |            "description" : "user id\n\n",
            |            "required" : true,
            |            "schema" :
            |              {
            |              "type" :
            |                "string",
            |              "format" : "uuid"
            |            },
            |            "style" : "simple"
            |          },
            |
            |            {
            |            "name" : "name",
            |            "in" : "path",
            |            "required" : true,
            |            "schema" :
            |              {
            |              "type" :
            |                "string"
            |            },
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
            |        }
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
            |            "schema" :
            |              {
            |              "type" :
            |                "string"
            |            },
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
            |            "content" : {
            |              "application/json" : {
            |                "schema" :
            |                  {
            |                  "$ref" : "#/components/schemas/NotFoundError"
            |                }
            |              }
            |            }
            |          }
            |        }
            |      }
            |    }
            |  },
            |  "components" : {
            |    "schemas" : {
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
            |        "required" : [
            |          "message"
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
            |        "required" : [
            |          "userName",
            |          "score"
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
                         |        }
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
            |        }
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
            |        }
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
            |        }
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
            |        "required" : [
            |          "name",
            |          "size"
            |        ]
            |      },
            |      "WithComplexDefaultValue" :
            |        {
            |        "type" :
            |          "object",
            |        "properties" : {
            |          "data" : {
            |            "$ref" : "#/components/schemas/ImageMetadata",
            |            "description" : "If not set, this field defaults to the value of the default annotation.",
            |            "default" : {
            |              "name" : "default",
            |              "size" : 42
            |            }
            |          }
            |        }
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
                         |        }
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
                         |        "required" : [
                         |          "name"
                         |        ]
                         |      }
                         |    }
                         |  }
                         |}""".stripMargin
        assertTrue(json == toJsonAst(expected))
      },
      test("nested product") {
        val endpoint  = Endpoint(GET / "static").in[NestedProduct]
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
                         |                "$ref" : "#/components/schemas/NestedProduct"
                         |              }
                         |            }
                         |          },
                         |          "required" : true
                         |        }
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
                         |        "required" : [
                         |          "name",
                         |          "size"
                         |        ]
                         |      },
                         |      "NestedProduct" :
                         |        {
                         |        "type" :
                         |          "object",
                         |        "properties" : {
                         |          "imageMetadata" : {
                         |            "$ref" : "#/components/schemas/ImageMetadata"
                         |          },
                         |          "withOptionalField" : {
                         |            "$ref" : "#/components/schemas/WithOptionalField"
                         |          }
                         |        },
                         |        "required" : [
                         |          "imageMetadata",
                         |          "withOptionalField"
                         |        ]
                         |      },
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
                         |        }
                         |      }
                         |    }
                         |  },
                         |  "components" : {
                         |    "schemas" : {
                         |      "SimpleEnum" :
                         |        {
                         |        "type" :
                         |          "string",
                         |        "enum" : [
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
            |        }
            |      }
            |    }
            |  },
            |  "components" : {
            |    "schemas" : {
            |      "One" :
            |        {
            |        "type" :
            |          "object",
            |        "properties" : {}
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
            |        "required" : [
            |          "name"
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
            |        }
            |      }
            |    }
            |  },
            |  "components" : {
            |    "schemas" : {
            |      "One" :
            |        {
            |        "type" :
            |          "object",
            |        "properties" : {}
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
            |        "required" : [
            |          "name"
            |        ]
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
                         |        }
                         |      }
                         |    }
                         |  },
                         |  "components" : {
                         |    "schemas" : {
                         |      "One" :
                         |        {
                         |        "type" :
                         |          "object",
                         |        "properties" : {}
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
                         |        "required" : [
                         |          "name"
                         |        ]
                         |      }
                         |    }
                         |  }
                         |}""".stripMargin
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
            |        }
            |      }
            |    }
            |  },
            |  "components" : {
            |    "schemas" : {
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
            |      },
            |      "NestedOne" :
            |        {
            |        "type" :
            |          "object",
            |        "properties" : {}
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
            |            "$ref" : "#/components/schemas/SealedTraitNoDiscriminator"
            |          }
            |        },
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
            |        "required" : [
            |          "name"
            |        ]
            |      },
            |      "One" :
            |        {
            |        "type" :
            |          "object",
            |        "properties" : {}
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
      test("multiple methods on same path") {
        val getEndpoint  = Endpoint(GET / "test")
          .out[String](MediaType.text.`plain`)
        val postEndpoint = Endpoint(POST / "test")
          .in[String]
          .out[String](Status.Created, MediaType.text.`plain`)
        val generated    = OpenAPIGen.fromEndpoints(
          "Multiple Methods on Same Path",
          "1.0",
          getEndpoint,
          postEndpoint,
        )
        val json         = toJsonAst(generated)
        for {
          expectedJson <- ZIO.acquireReleaseWith(
            ZIO.attemptBlockingIO(scala.io.Source.fromResource("endpoint/openapi/multiple-methods-on-same-path.json")),
          )(buf => ZIO.attemptBlockingIO(buf.close()).orDie)(buf => ZIO.attemptBlockingIO(buf.mkString))
        } yield assertTrue(json == toJsonAst(expectedJson))
      },
      test("examples for combined input") {
        val endpoint =
          Endpoint(Method.GET / "root" / string("name"))
            .in[Payload]
            .out[String]
            .examplesIn(("hi", ("name_value", Payload("input"))))

        val openApi      =
          OpenAPIGen.fromEndpoints(
            title = "Combined input examples",
            version = "1.0",
            endpoint,
          )
        val json         = toJsonAst(openApi)
        val expectedJson = """{
                             |  "openapi" : "3.1.0",
                             |  "info" : {
                             |    "title" : "Combined input examples",
                             |    "version" : "1.0"
                             |  },
                             |  "paths" : {
                             |    "/root/{name}" : {
                             |      "get" : {
                             |        "parameters" : [
                             |          {
                             |            "name" : "name",
                             |            "in" : "path",
                             |            "required" : true,
                             |            "schema" : {
                             |              "type" : "string"
                             |            },
                             |            "examples" : {
                             |              "hi" : {
                             |                "value" : "name_value"
                             |              }
                             |            },
                             |            "style" : "simple"
                             |          }
                             |        ],
                             |        "requestBody" : {
                             |          "content" : {
                             |            "application/json" : {
                             |              "schema" : {
                             |                "$ref" : "#/components/schemas/Payload"
                             |              },
                             |              "examples" : {
                             |                "hi" : {
                             |                  "value" : {
                             |                    "content" : "input"
                             |                  }
                             |                }
                             |              }
                             |            }
                             |          },
                             |          "required" : true
                             |        },
                             |        "responses" : {
                             |          "200" : {
                             |            "content" : {
                             |              "application/json" : {
                             |                "schema" : {
                             |                  "type" : "string"
                             |                }
                             |              }
                             |            }
                             |          }
                             |        }
                             |      }
                             |    }
                             |  },
                             |  "components" : {
                             |    "schemas" : {
                             |      "Payload" : {
                             |        "type" : "object",
                             |        "properties" : {
                             |          "content" : {
                             |            "type" : "string"
                             |          }
                             |        },
                             |        "required" : [
                             |          "content"
                             |        ],
                             |        "description" : "A simple payload"
                             |      }
                             |    }
                             |  }
                             |}""".stripMargin
        assertTrue(json == toJsonAst(expectedJson))
      },
      test("example for alternated input") {
        val endpoint     =
          Endpoint(Method.GET / "root" / string("name"))
            .inCodec(ContentCodec.content[Payload] | ContentCodec.content[String])
            .out[String]
            .examplesIn(("hi", ("name_value", Left(Payload("input")))), ("ho", ("name_value2", Right("input"))))
        val openApi      =
          OpenAPIGen.fromEndpoints(
            title = "Alternated input examples",
            version = "1.0",
            endpoint,
          )
        val json         = toJsonAst(openApi)
        val expectedJson = """{
                             |  "openapi" : "3.1.0",
                             |  "info" : {
                             |    "title" : "Alternated input examples",
                             |    "version" : "1.0"
                             |  },
                             |  "paths" : {
                             |    "/root/{name}" : {
                             |      "get" : {
                             |        "parameters" : [
                             |          {
                             |            "name" : "name",
                             |            "in" : "path",
                             |            "required" : true,
                             |            "schema" : {
                             |              "type" : "string"
                             |            },
                             |            "examples" : {
                             |              "hi" : {
                             |                "value" : "name_value"
                             |              },
                             |              "ho" : {
                             |                "value" : "name_value2"
                             |              }
                             |            },
                             |            "style" : "simple"
                             |          }
                             |        ],
                             |        "requestBody" : {
                             |          "content" : {
                             |            "application/json" : {
                             |              "schema" : {
                             |                "anyOf" : [
                             |                  {
                             |                    "$ref" : "#/components/schemas/Payload"
                             |                  },
                             |                  {
                             |                    "type" : "string"
                             |                  }
                             |                ],
                             |                "description" : ""
                             |              },
                             |              "examples" : {
                             |                "hi" : {
                             |                  "value" : {
                             |                    "content" : "input"
                             |                  }
                             |                },
                             |                "ho" : {
                             |                  "value" : "input"
                             |                }
                             |              }
                             |            }
                             |          },
                             |          "required" : true
                             |        },
                             |        "responses" : {
                             |          "200" : {
                             |            "content" : {
                             |              "application/json" : {
                             |                "schema" : {
                             |                  "type" : "string"
                             |                }
                             |              }
                             |            }
                             |          }
                             |        }
                             |      }
                             |    }
                             |  },
                             |  "components" : {
                             |    "schemas" : {
                             |      "Payload" : {
                             |        "type" : "object",
                             |        "properties" : {
                             |          "content" : {
                             |            "type" : "string"
                             |          }
                             |        },
                             |        "required" : [
                             |          "content"
                             |        ],
                             |        "description" : "A simple payload"
                             |      }
                             |    }
                             |  }
                             |}""".stripMargin
        assertTrue(json == toJsonAst(expectedJson))
      },
      test("example with safe characters in path") {
        val endpoint = Endpoint(Method.GET / "simple/api/v1.0/$name/plus+some_stuff-etc")
        val openApi  =
          OpenAPIGen.fromEndpoints(
            title = "Safe path examples",
            version = "1.0",
            endpoint,
          )
        val json     = toJsonAst(openApi)
        assertTrue(json == toJsonAst("""{
                                       |  "openapi" : "3.1.0",
                                       |    "info" : {
                                       |      "title" : "Safe path examples",
                                       |      "version" : "1.0"
                                       |    },
                                       |    "paths" : {
                                       |      "/simple/api/v1.0/$name/plus+some_stuff-etc" : {
                                       |        "get" : {}
                                       |      }
                                       |    },
                                       |    "components" : {}
                                       |}""".stripMargin))
      },
      test("Non content codecs are ignored when building multipart schema") {
        // We only test there is no exception when building the schema
        val endpoint =
          Endpoint(RoutePattern.POST / "post")
            .in[Int]("foo")
            .in[Boolean]("bar")
            .query(HttpCodec.query[String]("q"))
            .out[Unit]

        SwaggerUI.routes("docs/openapi", OpenAPIGen.fromEndpoints(endpoint))
        assertCompletes
      },
      test("Recursive schema") {
        val endpoint     = Endpoint(RoutePattern.POST / "folder")
          .out[Recursive]
        val openApi      = OpenAPIGen.fromEndpoints(endpoint)
        val json         = toJsonAst(openApi)
        val expectedJson =
          """
            |{
            |  "openapi" : "3.1.0",
            |  "info" : {
            |    "title" : "",
            |    "version" : ""
            |  },
            |  "paths" : {
            |    "/folder" : {
            |      "post" : {
            |        "responses" : {
            |          "200" :
            |            {
            |            "content" : {
            |              "application/json" : {
            |                "schema" :
            |                  {
            |                  "$ref" : "#/components/schemas/Recursive"
            |                }
            |              }
            |            }
            |          }
            |        }
            |      }
            |    }
            |  },
            |  "components" : {
            |    "schemas" : {
            |      "NestedRecursive" :
            |        {
            |        "type" :
            |          "object",
            |        "properties" : {
            |          "next" : {
            |            "$ref" : "#/components/schemas/Recursive"
            |          }
            |        },
            |        "required" : [
            |          "next"
            |        ]
            |      },
            |      "Recursive" :
            |        {
            |        "type" :
            |          "object",
            |        "properties" : {
            |          "nestedSet" : {
            |            "type" :
            |              "array",
            |            "items" : {
            |              "$ref" : "#/components/schemas/Recursive"
            |            },
            |            "uniqueItems" : true
            |          },
            |          "nestedEither" : {
            |            "oneOf" : [
            |              {
            |                "$ref" : "#/components/schemas/Recursive"
            |              },
            |              {
            |                "$ref" : "#/components/schemas/Recursive"
            |              }
            |            ]
            |          },
            |          "nestedTuple" : {
            |            "allOf" : [
            |              {
            |                "$ref" : "#/components/schemas/Recursive"
            |              },
            |              {
            |                "$ref" : "#/components/schemas/Recursive"
            |              }
            |            ]
            |          },
            |          "nestedOption" : {
            |            "$ref" : "#/components/schemas/Recursive"
            |          },
            |          "nestedList" : {
            |            "type" :
            |              "array",
            |            "items" : {
            |              "$ref" : "#/components/schemas/Recursive"
            |            }
            |          },
            |          "nestedOverAnother" : {
            |            "$ref" : "#/components/schemas/NestedRecursive"
            |          }
            |        },
            |        "additionalProperties" :
            |          {
            |          "$ref" : "#/components/schemas/Recursive"
            |        },
            |        "required" : [
            |          "nestedOption",
            |          "nestedList",
            |          "nestedMap",
            |          "nestedSet",
            |          "nestedEither",
            |          "nestedTuple",
            |          "nestedOverAnother"
            |        ],
            |        "description" : "A recursive structure"
            |      }
            |    }
            |  }
            |}
            |""".stripMargin
        assertTrue(json == toJsonAst(expectedJson))
      },
      test("Lazy schema") {
        val endpoint     = Endpoint(RoutePattern.POST / "lazy")
          .in[Lazy.A]
          .out[Unit]
        val openApi      = OpenAPIGen.fromEndpoints(endpoint)
        val json         = toJsonAst(openApi)
        val expectedJson = """{
                             |  "openapi" : "3.1.0",
                             |  "info": {
                             |    "title": "",
                             |    "version": ""
                             |  },
                             |  "paths" : {
                             |    "/lazy" : {
                             |      "post" : {
                             |        "requestBody" : {
                             |          "content" : {
                             |            "application/json" : {
                             |              "schema" : {
                             |                "$ref" : "#/components/schemas/A"
                             |              }
                             |            }
                             |          },
                             |          "required" : true
                             |        },
                             |        "responses" : {
                             |          "200" : {
                             |            "content" : {
                             |              "application/json" : {
                             |                "schema": {
                             |                  "type" : "null"
                             |                }
                             |              }
                             |            }
                             |          }
                             |        }
                             |      }
                             |    }
                             |  },
                             |  "components" : {
                             |    "schemas" : {
                             |      "A" : {
                             |        "type" : "object",
                             |        "properties" : {
                             |          "b" : {
                             |            "$ref" : "#/components/schemas/B"
                             |          }
                             |        },
                             |        "required" : [
                             |          "b"
                             |        ]
                             |      },
                             |      "B" : {
                             |        "type" : "object",
                             |        "properties" : {
                             |          "i" : {
                             |            "type" : "integer",
                             |            "format" : "int32"
                             |          }
                             |        },
                             |        "required" : [
                             |          "i"
                             |        ]
                             |      }
                             |    }
                             |  }
                             |}""".stripMargin
        val expected     = toJsonAst(expectedJson)
        assertTrue(json == expected)
      },
      test("Generic payload") {
        // TODO: Currently, the applied types of generics are not saved in the schema correctly
        // Once this is fixed, we should generate the ref as `#/components/schemas/WithGenericPayloadSimpleInputBody`
        val endpoint     = Endpoint(RoutePattern.POST / "generic")
          .in[WithGenericPayload[SimpleInputBody]]
        val openApi      = OpenAPIGen.fromEndpoints(endpoint)
        val json         = toJsonAst(openApi)
        val expectedJson = """{
                             |  "openapi" : "3.1.0",
                             |  "info" : {
                             |    "title" : "",
                             |    "version" : ""
                             |  },
                             |  "paths" : {
                             |    "/generic" : {
                             |      "post" : {
                             |        "requestBody" :
                             |          {
                             |          "content" : {
                             |            "application/json" : {
                             |              "schema" :
                             |                {
                             |                "$ref" : "#/components/schemas/WithGenericPayload"
                             |              }
                             |            }
                             |          },
                             |          "required" : true
                             |        }
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
                             |        "required" : [
                             |          "name",
                             |          "age"
                             |        ]
                             |      },
                             |      "WithGenericPayload" :
                             |        {
                             |        "type" :
                             |          "object",
                             |        "properties" : {
                             |          "a" : {
                             |            "$ref" : "#/components/schemas/SimpleInputBody"
                             |          }
                             |        },
                             |        "required" : [
                             |          "a"
                             |        ]
                             |      }
                             |    }
                             |  }
                             |}
                             |""".stripMargin
        assertTrue(json == toJsonAst(expectedJson))
      },
    )

}
