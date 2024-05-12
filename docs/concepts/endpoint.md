---
id: endpoint
title: Endpoints
---

# Endpoint

In ZIO HTTP, an `Endpoint` is a fundamental building block that represents a single HTTP route or operation. It offers a declarative approach to defining your API's contract, encompassing the HTTP method, path pattern, input parameters, output types and potential errors.


## Defining an Endpoint

An `Endpoint` is typically defined using the `Endpoint` constructor or builder methods. The constructor accepts parameters to specify the endpoint's characteristics like the HTTP method, path pattern, query parameters, request body and response types.

Example:

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

// Endpoint to create a new user
val createUserEndpoint =
  Endpoint(RoutePattern.POST / "users")
    .body[User](Doc.p("User data to be created"))
    .out[User](Doc.p("Details of the newly created user")) ?? Doc.p(
      "Endpoint to create a new user with the provided information"
    )
```

In this example, two endpoints are defined:

* **getUserEndpoint:** Retrieves a user's details using a GET request on the `/users/{id}` path, where `{id}` is a path variable representing the user's unique identifier. The response includes the `User` object containing the user's information.
* **createUserEndpoint:** Creates a new user using a POST request on the `/users` path. The request body contains a JSON representation of the `User` object with the user's details. The response includes the newly created `User` object.

## Implementing an Endpoint

After defining an endpoint, you need to provide a handler function to process incoming requests and produce desired responses or handle errors. This handler function is attached using the `implement` method.



```scala mdoc:silent

object UserRepository {
  val users = List(
    User(1, "John Doe", "john.doe@example.com"),
    User(2, "Jane Smith", "jane.smith@example.com")
  )

  def getUser(id: Int): Option[User] = users.find(_.id == id)

  def createUser(user: User): User = {
    val newId = users.map(_.id).maxOption.getOrElse(0) + 1
    user.copy(id = newId)
  }
}

val getUserHandler = handler((id: Int) => UserRepository.getUser(id).getOrElse(throw new Exception("User not found")))
val createUserHandler = handler((user: User) => UserRepository.createUser(user))

val getUserRoute = getUserEndpoint.implement(getUserHandler)
val createUserRoute = createUserEndpoint.implement(createUserHandler)
```

Here, the `implement` method takes a `Handler` that maps the input (in this case, the `query` string) to the output (a list of `User` objects).

## Handling Errors

ZIO HTTP provides various ways to handle errors in endpoints. You can define specific error types for your endpoint and map them to appropriate HTTP status codes.

```scala mdoc:reset

import zio._
import zio.schema.{DeriveSchema, Schema}
import zio.http._
import zio.http.codec.PathCodec
import zio.http.endpoint.Endpoint
import zio.http.endpoint.EndpointMiddleware.None


case class UserNotFoundError(error: String, message: String)

object UserNotFoundError {
  implicit val schema: Schema[UserNotFoundError] = DeriveSchema.gen
}

val endpoint: Endpoint[Int, Int, UserNotFoundError, User, None] =
  Endpoint(RoutePattern.GET / "users" / PathCodec.int("id"))
    .out[User]
    .outError[UserNotFoundError](Status.NotFound)

```

In this example, the `outError` method defines the `NotFoundError` as a potential error for the endpoint, mapped to the 404 status code.

## Multiple Errors

ZIO HTTP supports handling multiple error types by using either `Either` or a common base class for all errors.

```scala mdoc:compile-only


import zio._

import zio.schema.{DeriveSchema, Schema}

import zio.http._
import zio.http.codec.{HeaderCodec, PathCodec}
import zio.http.endpoint.Endpoint
import zio.http.endpoint.EndpointMiddleware.None

case class UserAlreadyExistsError(error: String, message: String)

object UserAlreadyExistsError {
  implicit val schema: Schema[UserAlreadyExistsError] = DeriveSchema.gen
}

case class AuthenticationError(message: String, userId: Int)

object AuthenticationError {
  implicit val schema: Schema[AuthenticationError] = DeriveSchema.gen
}

val endpoint: Endpoint[Int, (Int, Header.Authorization), Either[AuthenticationError, UserAlreadyExistsError], User, None] =
  Endpoint(RoutePattern.GET / "users" / PathCodec.int("id"))
    .header(HeaderCodec.authorization)
    .out[User]
    .outError[UserAlreadyExistsError](Status.Conflict)
    .outError[AuthenticationError](Status.Unauthorized)

```

Alternatively, you can unify all error types into a single error type using a sealed trait or an Enum.

```scala mdoc:reset
import zio._

import zio.schema.{DeriveSchema, Schema}

import zio.http._
import zio.http.codec.{HeaderCodec, HttpCodec, PathCodec}
import zio.http.endpoint.Endpoint
import zio.http.endpoint.EndpointMiddleware.None


case class UserAuthenticationError(message: String, userId: Int)

object UserAuthenticationError {
  implicit val schema: Schema[UserAuthenticationError] = DeriveSchema.gen
}

val endpoint: Endpoint[Int, (Int, Header.Authorization), UserAuthenticationError, User, None] =
  Endpoint(RoutePattern.GET / "users" / PathCodec.int("id"))
    .header(HeaderCodec.authorization)
    .out[User]
    .outError[UserAuthenticationError](Status.Unauthorized)
```

## OpenAPI Documentation

ZIO HTTP allows us to generate OpenAPI documentation from `Endpoint` definitions, which can be used to create Swagger UI routes.

```scala mdoc:reset
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

## Generating ZIO CLI App from Endpoint API

ZIO HTTP allows you to generate a ZIO CLI client application from `Endpoint` definitions using the `HttpCliApp.fromEndpoints` constructor.

```scala mdoc:reset

import zio._
import zio.cli._

import zio.http._
import zio.http.codec._
import zio.http.endpoint.cli._

val cliApp =
  HttpCliApp
    .fromEndpoints(
      name = "users-mgmt",
      version = "0.0.1",
      summary = HelpDoc.Span.text("Users management CLI"),
      footer = HelpDoc.p("Copyright 2024"),
      host = "localhost",
      port = 8080,
      endpoints = Chunk(getUserRoute, createUserRoute),
      cliStyle = true,
    )
    .cliApp
```

This code will generate a ZIO CLI application named `users-mgmt` with version `0.0.1`, providing a command-line interface for interacting with your API endpoints.

- [Reference codes](https://github.com/zio/zio-http/tree/main/zio-http-example/src/main/scala/)