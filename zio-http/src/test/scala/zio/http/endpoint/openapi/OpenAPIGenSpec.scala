package zio.http.endpoint.openapi

import zio.Scope
import zio.json.ast.Json
import zio.test._

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


  def minify(str: String): String                          =
    Json.encoder.encodeJson(Json.decoder.decodeJson(str).toOption.get, None).toString
  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("OpenAPIGenSpec")(
      test("simple endpoint to OpenAPI") {
        val generated    = OpenAPIGen.fromEndpoints("Simple Endpoint", "1.0", simpleEndpoint)
        val json         = generated.toJson
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
        assertTrue(json == minify(expectedJson))
      },
      test("with query parameter") {
        val generated    = OpenAPIGen.fromEndpoints("Simple Endpoint", "1.0", queryParamEndpoint)
        val json         = generated.toJson
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
        assertTrue(json == minify(expectedJson))
      },
      test("alternative input") {
        val generated    = OpenAPIGen.fromEndpoints("Simple Endpoint", "1.0", alternativeInputEndpoint)
        val json         = generated.toJson
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
        assertTrue(json == minify(expectedJson))
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
        val json         = generated.toJson
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
        assertTrue(json == minify(expectedJson))
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
        val json         = generated.toJson
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
        assertTrue(json == minify(expectedJson))
      },
      test("with query parameter, alternative input, alternative output and examples"){
        val endpoint =
          Endpoint(GET / "static")
            .inCodec(
              HttpCodec
                .content[OtherSimpleInputBody] ?? Doc.p("other input") |
                HttpCodec
                  .content[SimpleInputBody] ?? Doc.p("simple input")
            )
            .query(QueryCodec.paramStr("query"))
            .outCodec(
              HttpCodec
                .content[SimpleOutputBody] ?? Doc.p("simple output") |
                HttpCodec
                  .content[NotFoundError] ?? Doc.p("not found")
            )

        val generated    = OpenAPIGen.fromEndpoints("Simple Endpoint", "1.0", endpoint)
        val json         = generated.toJson
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
        assertTrue(json == minify(expectedJson))
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
        val json      = generated.toJson
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
        assertTrue(json == minify(expected))
      },
      test("multiple endpoint definitions") {
        val generated =
          OpenAPIGen.fromEndpoints(
            "Simple Endpoint",
            "1.0",
            simpleEndpoint,
            queryParamEndpoint,
            alternativeInputEndpoint
          )
        val json      = generated.toJson
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
        assertTrue(json == minify(expected))
      }
    )

}
