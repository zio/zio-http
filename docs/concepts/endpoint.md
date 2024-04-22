---
id: endpoint
title: Endpoints in ZIO HTTP
---

In ZIO HTTP, an `Endpoint` is a fundamental building block that represents a single HTTP route or operation. It provides a declarative way to define the contract of your API, including the HTTP method, path pattern, input parameters, output types, and potential errors.


## Defining an Endpoint

An `Endpoint` is typically defined using the `Endpoint` constructor or builder methods. The constructor takes various parameters to define the endpoint's characteristics, such as the HTTP method, path pattern, query parameters, request body, and response types.

Example:

```scala
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

```scala
val booksRoute = endpoint.implement(handler((query: String) => BookRepo.find(query)))
```

Here, the `implement` method takes a `Handler` that maps the input (in this case, the `query` string) to the output (a list of `Book` objects).

## Handling Errors

ZIO HTTP provides various ways to handle errors in endpoints. You can define specific error types for your endpoint and map them to appropriate HTTP status codes.

```scala
case class NotFoundError(error: String, message: String)

val endpoint: Endpoint[Int, Int, NotFoundError, Book, None] =
  Endpoint(RoutePattern.GET / "books" / PathCodec.int("id"))
    .out[Book]
    .outError[NotFoundError](Status.NotFound)
```

In this example, the `outError` method defines the `NotFoundError` as a potential error for the endpoint, mapped to the 404 status code.

## Multiple Errors

ZIO HTTP supports handling multiple error types by using either `Either` or a common base class for all errors.

```scala
case class BookNotFound(message: String, bookId: Int)
case class AuthenticationError(message: String, userId: Int)

val endpoint: Endpoint[Int, (Int, Header.Authorization), Either[AuthenticationError, BookNotFound], Book, None] =
  Endpoint(RoutePattern.GET / "books" / PathCodec.int("id"))
    .header(HeaderCodec.authorization)
    .out[Book]
    .outError[BookNotFound](Status.NotFound)
    .outError[AuthenticationError](Status.Unauthorized)
```

Alternatively, you can unify all error types into a single error type using a sealed trait or an enum.

```scala
abstract class AppError(message: String)
case class BookNotFound(message: String, bookId: Int) extends AppError(message)
case class AuthenticationError(message: String, userId: Int) extends AppError(message)

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

```scala
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

```scala
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