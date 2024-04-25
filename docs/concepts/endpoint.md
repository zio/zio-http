---
id: endpoint
title: Endpoints
---

In ZIO HTTP, an `Endpoint` is a fundamental building block that represents a single HTTP route or operation. It provides a declarative way to define the contract of your API, including the HTTP method, path pattern, input parameters, output types, and potential errors.


## Defining an Endpoint

An `Endpoint` is typically defined using the `Endpoint` constructor or builder methods. The constructor takes various parameters to define the endpoint's characteristics, such as the HTTP method, path pattern, query parameters, request body, and response types.

Example:

```scala mdoc:silent 
import zio._
import zio.http._
import zio.http.codec._
import zio.http.endpoint._
import zio.schema.annotation.description
import zio.schema.{DeriveSchema, Schema}

  case class Book(
    @description("Title of the book")
    title: String,
    @description("List of the authors of the book")
    authors: List[String],
  )
  object Book {
    implicit val schema: Schema[Book] = DeriveSchema.gen
  }

val endpoint =
  Endpoint((RoutePattern.GET / "books") ?? Doc.p("Route for querying books"))
    .query(
      QueryCodec.queryTo[String]("q").examples(("example1", "scala"), ("example2", "zio")) ?? Doc.p(
        "Query parameter for searching books",
      ),
    )
    .out[List[Book]](Doc.p("List of books matching the query")) ?? Doc.p(
    "Endpoint to query books based on a search query",
  )
```

In this example, the endpoint is defined for the `GET` method on the `/books` path. It accepts a query parameter `q` of type `String` and returns a list of `Book` objects.

## Implementing an Endpoint

After defining an endpoint, you need to implement it by providing a handler function that processes the incoming request and produces the desired response or error.

```scala mdoc:silent

  object BookRepo {
    val book1 = Book("Programming in Scala", List("Martin Odersky", "Lex Spoon", "Bill Venners", "Frank Sommers"))
    val book2 = Book("Zionomicon", List("John A. De Goes", "Adam Fraser"))
    val book3 = Book("Effect-Oriented Programming", List("Bill Frasure", "Bruce Eckel", "James Ward"))
    def find(q: String): List[Book] = {
      if (q.toLowerCase == "scala") List(book1, book2, book3)
      else if (q.toLowerCase == "zio") List(book2, book3)
      else List.empty
    }
  }

val booksRoute = endpoint.implement(handler((query: String) => BookRepo.find(query)))
```

Here, the `implement` method takes a `Handler` that maps the input (in this case, the `query` string) to the output (a list of `Book` objects).

## Handling Errors

ZIO HTTP provides various ways to handle errors in endpoints. You can define specific error types for your endpoint and map them to appropriate HTTP status codes.

```scala mdoc:reset

import zio._
import zio.schema.{DeriveSchema, Schema}
import zio.http._
import zio.http.codec.PathCodec
import zio.http.endpoint.Endpoint
import zio.http.endpoint.EndpointMiddleware.None


case class Book(title: String, authors: List[String])

  object Book {
    implicit val schema: Schema[Book] = DeriveSchema.gen
  }
  case class NotFoundError(error: String, message: String)

  object NotFoundError {
    implicit val schema: Schema[NotFoundError] = DeriveSchema.gen
  }


val endpoint: Endpoint[Int, Int, NotFoundError, Book, None] =
  Endpoint(RoutePattern.GET / "books" / PathCodec.int("id"))
    .out[Book]
    .outError[NotFoundError](Status.NotFound)
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

case class Book(title: String, authors: List[String])

object Book {
  implicit val schema: Schema[Book] = DeriveSchema.gen
}

case class BookNotFound(message: String, bookId: Int)

object BookNotFound {
    implicit val schema: Schema[BookNotFound] = DeriveSchema.gen
  }
  
case class AuthenticationError(message: String, userId: Int)

object AuthenticationError {
    implicit val schema: Schema[AuthenticationError] = DeriveSchema.gen
  }

val endpoint: Endpoint[Int, (Int, Header.Authorization), Either[AuthenticationError, BookNotFound], Book, None] =
  Endpoint(RoutePattern.GET / "books" / PathCodec.int("id"))
    .header(HeaderCodec.authorization)
    .out[Book]
    .outError[BookNotFound](Status.NotFound)
    .outError[AuthenticationError](Status.Unauthorized)
```

Alternatively, you can unify all error types into a single error type using a sealed trait or an enum.

```scala mdoc:reset
import zio._

import zio.schema.{DeriveSchema, Schema}

import zio.http._
import zio.http.codec.{HeaderCodec, HttpCodec, PathCodec}
import zio.http.endpoint.Endpoint
import zio.http.endpoint.EndpointMiddleware.None


  case class Book(title: String, authors: List[String])

  object Book {
    implicit val schema: Schema[Book] = DeriveSchema.gen
  }


abstract class AppError(message: String)

case class BookNotFound(message: String, bookId: Int) extends AppError(message)

object BookNotFound {
    implicit val schema: Schema[BookNotFound] = DeriveSchema.gen
  }

case class AuthenticationError(message: String, userId: Int) extends AppError(message)

object AuthenticationError {
    implicit val schema: Schema[AuthenticationError] = DeriveSchema.gen
  }

val endpoint: Endpoint[Int, (Int, Header.Authorization), AppError, Book, None] =
  Endpoint(RoutePattern.GET / "books" / PathCodec.int("id"))
    .header(HeaderCodec.authorization)
    .out[Book]
    .outErrors[AppError](
      HttpCodec.error[BookNotFound](Status.NotFound),
      HttpCodec.error[AuthenticationError](Status.Unauthorized),
    )
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


case class Book(
    @description("Title of the book")
    title: String,
    @description("List of the authors of the book")
    authors: List[String],
  )
  object Book {
    implicit val schema: Schema[Book] = DeriveSchema.gen
  }

  object BookRepo {
    val book1 = Book("Programming in Scala", List("Martin Odersky", "Lex Spoon", "Bill Venners", "Frank Sommers"))
    val book2 = Book("Zionomicon", List("John A. De Goes", "Adam Fraser"))
    val book3 = Book("Effect-Oriented Programming", List("Bill Frasure", "Bruce Eckel", "James Ward"))
    def find(q: String): List[Book] = {
      if (q.toLowerCase == "scala") List(book1, book2, book3)
      else if (q.toLowerCase == "zio") List(book2, book3)
      else List.empty
    }
  }


val endpoint =
    Endpoint((RoutePattern.GET / "books") ?? Doc.p("Route for querying books"))
      .query(
        QueryCodec.queryTo[String]("q").examples(("example1", "scala"), ("example2", "zio")) ?? Doc.p(
          "Query parameter for searching books",
        ),
      )
      .out[List[Book]](Doc.p("List of books matching the query")) ?? Doc.p(
      "Endpoint to query books based on a search query",
    )
    
val booksRoute    = endpoint.implement(handler((query: String) => BookRepo.find(query)))
val openAPI = OpenAPIGen.fromEndpoints(title = "Library API", version = "1.0", endpoint)
val swaggerRoutes = SwaggerUI.routes("docs" / "openapi", openAPI)
val routes = Routes(booksRoute) ++ swaggerRoutes
```

## Generating Endpoints from OpenAPI Spec

You can also generate `Endpoint` code from an existing OpenAPI specification using the `EndpointGen.fromOpenAPI` constructor.

```scala 
val userOpenAPI = OpenAPI.fromJson(/* OpenAPI JSON definition */)

CodeGen.writeFiles(
  EndpointGen.fromOpenAPI(userOpenAPI.toOption.get),
  basePath = Paths.get("./users/src/main/scala"),
  basePackage = "org.example",
  scalafmtPath = None,
)
```

## Generating ZIO CLI App from Endpoint API

ZIO HTTP allows you to generate a ZIO CLI client application from `Endpoint` definitions using the `HttpCliApp.fromEndpoints` constructor.

```scala mdoc:fail


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
      footer = HelpDoc.p("Copyright 2023"),
      host = "localhost",
      port = 8080,
      endpoints = Chunk(getUser, getUserPosts, createUser),
      cliStyle = true,
    )
    .cliApp
```

This allows you to interact with your API endpoints through a command-line interface.

In summary, the `Endpoint` concept in ZIO HTTP provides a declarative and type-safe way to define your API's contract, handle various scenarios such as input and output types, handle errors, generate OpenAPI documentation, and create client applications, all while adhering to the principles of functional programming and type safety. 

[Reference codes](https://github.com/zio/zio-http/tree/main/zio-http-example/src/main/scala/example/endpoint)