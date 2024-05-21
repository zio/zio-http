---
id: openapi-endpoint
title: "OpenAPI Documentation from Zio Endpoint API"
---
# OpenAPI Documentation from Zio Endpoint API

ZIO HTTP allows us to generate OpenAPI documentation from `Endpoint` definitions, which can be used to create Swagger UI routes.

## Example

```scala mdoc:passthrough
import zio._
import zio.schema.annotation.description
import zio.schema.{DeriveSchema, Schema}

import zio.http._
import zio.http.codec.PathCodec._
import zio.http.codec._

import zio.http.endpoint._
import zio.http.endpoint.openapi._

case class User(
  @description("Unique identifier for the user")
  id: Int,
  @description("Username of the user")
  name: String,
  @description("Email address of the user")
  email: String
)

object User {
  implicit val schema: Schema[User] = DeriveSchema.gen
}

object UserRepository {
  val users = List(
    User(1, "John Doe", "john.doe@example.com"),
    User(2, "Jane Smith", "jane.smith@example.com")
  )

  def findUser(id: Int): Option[User] = users.find(_.id == id)
}

val endpoint =
  Endpoint((RoutePattern.GET / "users" / PathCodec.int("id")) ?? Doc.p("Get user by ID"))
    .out[User](Doc.p("User details"))
    .errorOut(statusCode(Status.NotFound) and jsonBody[NotFoundError])

val userOpenAPI = OpenAPIGen.fromEndpoints(title = "User API", version = "1.0", endpoint)

```
