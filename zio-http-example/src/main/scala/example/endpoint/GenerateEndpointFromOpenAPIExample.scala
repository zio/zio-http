//> using dep "dev.zio::zio-http:3.4.1"
//> using dep "dev.zio::zio-http-gen:3.4.1"

package example.endpoint

import java.nio.file._

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
    basePath = Paths.get("./users/src/main/scala"),
    basePackage = "org.example",
    scalafmtPath = None,
  )
}
