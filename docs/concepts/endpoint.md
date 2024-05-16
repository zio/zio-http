---
id: endpoint
title: "Endpoint"
---

# Endpoint

In ZIO HTTP, an `Endpoint` is a fundamental building block that represents a single HTTP route or operation. It offers a declarative approach to defining your API's contract, encompassing the HTTP method, path pattern, input parameters, output types and potential errors.

## Key Concepts of Endpoint:


### Defining an Endpoint

An `Endpoint` is typically defined using the `Endpoint` constructor or builder methods. The constructor accepts parameters to specify the endpoint's characteristics like the HTTP method, path pattern, query parameters, request body and response types.

### Implementing an Endpoint

After defining an endpoint, you need to provide a handler function to process incoming requests and produce desired responses or handle errors. This handler function is attached using the `implement` method.

### Handling Errors

ZIO HTTP provides various ways to handle errors in endpoints. You can define specific error types for your endpoint and map them to appropriate HTTP status codes.

## Optional

### OpenAPI Documentation

ZIO HTTP allows us to generate OpenAPI documentation from `Endpoint` definitions, which can be used to create Swagger UI routes.


### Generating ZIO CLI App from Endpoint API

ZIO HTTP allows you to generate a ZIO CLI client application from `Endpoint` definitions using the `HttpCliApp.fromEndpoints` constructor.

## Simple Endpoint Example

```scala mdoc:silent
import zio._
import zio.http._
import zio.http.codec._
import zio.http.endpoint._
import zio.schema.annotation.description
import zio.schema.{DeriveSchema, Schema}

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

// Endpoint to retrieve a user by ID
val getUserEndpoint =
  Endpoint(RoutePattern.GET / "users" / PathCodec.int("id"))
    .out[User](Doc.p("Details of the user with the specified ID")) ?? Doc.p(
      "Endpoint to retrieve a user based on their unique identifier"
    )

val getUserHandler = handler((id: Int) => UserRepository.getUser(id).getOrElse(throw new Exception("User not found")))

val getUserRoute = getUserEndpoint.implement(getUserHandler)
```

In this example, we defined an endpoint to retrieve a user's details using a GET request on the /users/{id} path, where {id} is a path variable representing the user's unique identifier. The response includes the User object containing the user's information.

- [Reference codes](https://github.com/zio/zio-http/tree/main/zio-http-example/src/main/scala/)