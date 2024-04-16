package example.endpoint

import zio.http.endpoint.openapi.OpenAPI
import zio.http.gen.openapi.EndpointGen
import zio.http.gen.scala.CodeGen

object GenerateEndpointFromOpenAPIExample extends App {
  val userOpenAPI = OpenAPI.fromJson(
    """|{
       |  "openapi": "3.0.0",
       |  "info": {
       |    "title": "User API",
       |    "version": "1.0.0"
       |  },
       |  "paths": {
       |    "/users/{userId}": {
       |      "get": {
       |        "parameters": [
       |          {
       |            "name": "userId",
       |            "in": "path",
       |            "required": true,
       |            "schema": {
       |              "type": "integer"
       |            }
       |          }
       |        ],
       |        "responses": {
       |          "200": {
       |            "content": {
       |              "application/json": {
       |                "schema": {
       |                  "type": "object",
       |                  "properties": {
       |                    "userId": {
       |                      "type": "integer"
       |                    },
       |                    "username": {
       |                      "type": "string"
       |                    }
       |                  }
       |                }
       |              }
       |            }
       |          },
       |          "404": {
       |            "description": "User not found"
       |          }
       |        }
       |      }
       |    }
       |  }
       |}
       |""".stripMargin,
  )

  CodeGen.writeFiles(
    EndpointGen.fromOpenAPI(userOpenAPI.toOption.get),
    basePath = java.nio.file.Path.of("./users/src/main/scala"),
    basePackage = "org.example",
    scalafmtPath = None,
  )
}
