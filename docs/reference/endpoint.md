---
id: endpoint
title: Endpoint
---

The `Endpoint` API in ZIO HTTP, is an alternative way to describe the endpoints but in a declarative way. It is a high-level API that allows us to describe the endpoints and their inputs, outputs, and how they should look. So we can think of it as a DSL for just describing the endpoints, and then we can implement them separately.

Using `Endpoint` API enables us to generate OpenAPI documentation, and also to generate clients for the endpoints.

## Overview

Before delving into the detailed description of the `Endpoint` API, let's begin with a simple example to demonstrate how we can define an endpoint using the `Endpoint` API:

```scala mdoc:compile-only
import zio._
import zio.http._
import zio.http.codec._
import zio.http.endpoint.Endpoint
import zio.schema.DeriveSchema

case class Book(title: String, authors: List[String])
object Book {
  implicit val schema = DeriveSchema.gen[Book]
}

val endpoint =
  Endpoint(RoutePattern.GET / "books")
    .query(QueryCodec.queryTo[String]("q") examples (("example1", "scala"), ("example2", "zio")))
    .out[List[Book]]
```

In the above example, we defined an endpoint on the path `/books` that accepts a query parameter `q` of type `String` and returns a list of `Book`.

After defining the endpoint, we are ready to implement it. We can implement it using the `Endpoint#implement` method, which takes a proper handler function that will be called when the endpoint is invoked and returns a `Route`:

```scala
val booksRoute = endpoint.implement(handler((query: String) => BookRepo.find(query)))
```

We can also generate OpenAPI documentation for our endpoint using the `OpenAPIGen.fromEndpoints` constructor:

```scala
val openAPI       = OpenAPIGen.fromEndpoints(title = "Library API", version = "1.0", endpoint)
val swaggerRoutes = SwaggerUI.routes("docs" / "openapi", openAPI)
```

And finally we are ready to serve all the routes. Let's see the complete example:

```scala mdoc:passthrough
import utils._

printSource("zio-http-example/src/main/scala/example/endpoint/BooksEndpointExample.scala")
```

By running the above example, other than the main `/books` route, we can also access the OpenAPI documentation using the SwaggerUI at the `/docs/openapi` route.

This was an overview of the `Endpoint` API in ZIO HTTP. Next, we will dive deeper into the `Endpoint` API and see how we can describe the endpoints in more detail.

## Describing Endpoints

Each endpoint is described by a set of properties, such as the path, query parameters, headers, and response type. The `Endpoint` API provides a set of methods to describe these properties. We can think of an endpoint as a function that takes some input and returns some output:

* **Input Properties**— They can be the HTTP method, path parameters, query parameters, request headers, and the request body.
* **Output Properties**­— They can be success or failure! Both success and failure can have a response body, media type, and status code.

Also, we can provide metadata for each property, such as documentation, examples, etc.

## Describing Input Properties

### Method and Path Parameters

We start describing an endpoint by specifying the HTTP method and the path. The default constructor of the `Endpoint` class takes a `RoutePattern` which is a combination of the HTTP method and the path:

```scala mdoc:compile-only
import zio._
import zio.http._
import zio.http.endpoint._
import zio.http.endpoint.EndpointMiddleware._
import zio.http.codec.PathCodec

val endpoint1: Endpoint[Unit, Unit, ZNothing, ZNothing, None] =
  Endpoint(RoutePattern.GET / "users")

val endpoint2: Endpoint[String, String, ZNothing, ZNothing, None] =
  Endpoint(RoutePattern.GET / "users" / PathCodec.string("user_name"))

val endpoint3: Endpoint[(String, Int), (String, Int), ZNothing, ZNothing, None] =
  Endpoint(RoutePattern.GET / "users" / PathCodec.string("user_name") / "posts" / PathCodec.int("post_id"))
```

In the above examples, we defined three endpoints. The first one is a simple endpoint that matches the GET method on the `/users` path. The second one matches the GET method on the `/users/:user_name` path, where `:user_name` is a path parameter of type `String`. The third one matches the GET method on the `/users/:user_name/posts/:post_id` path, where `:user_name` and `:post_id` are path parameters of type `String` and `Int`, respectively.

The `Endpoint` is a type-safe way to describe the endpoints. For example, if we try to implement the `endpoint3` with a handler that takes a different input type other than `(String, Int)`, the compiler will give us an error.

### Query Parameters

Query parameters can be described using the `Endpoint#query` method which takes a `QueryCodec[A]`:

```scala mdoc:invisible
import zio._
import zio.http._
import zio.http.endpoint._
import zio.http.endpoint.EndpointMiddleware._
import zio.http.codec.QueryCodec
import zio.http.RoutePattern
import zio.http.codec.PathCodec
import zio.http.codec._
```

```scala mdoc:compile-only
val endpoint: Endpoint[Unit, String, ZNothing, ZNothing, None] =
  Endpoint(RoutePattern.GET / "books")
    .query(QueryCodec.queryTo[String]("q"))
```

QueryCodecs are composable, so we can combine multiple query parameters:

```scala mdoc:compile-only
val endpoint: Endpoint[Unit, (String, Int), ZNothing, ZNothing, None] =
  Endpoint(RoutePattern.GET / "books")
    .query(QueryCodec.queryTo[String]("q") ++ QueryCodec.queryTo[Int]("limit"))
```

Or we can use the `query` method multiple times:

```scala mdoc:compile-only
val endpoint: Endpoint[Unit, (String, Int), ZNothing, ZNothing, None] =
  Endpoint(RoutePattern.GET / "books")
    .query(QueryCodec.queryTo[String]("q"))
    .query(QueryCodec.queryTo[Int]("limit"))
```

Please note that as we add more properties to the endpoint, the input and output types of the endpoint change accordingly. For example, in the following example, we have an endpoint with a path parameter of type `String` and two query parameters of type `String` and `Int`. So the input type of the endpoint is `(String, String, Int)`:

```scala mdoc:compile-only
val endpoint: Endpoint[String, (String, String, Int), ZNothing, ZNothing, None] =
  Endpoint(RoutePattern.GET / "books" / PathCodec.string("genre"))
    .query(QueryCodec.queryTo[String]("q"))
    .query(QueryCodec.queryTo[Int]("limit"))
```

When we implement the endpoint, the handler function should take the input type of a tuple that the first element is the "genre" path parameter, and the second and third elements are the query parameters "q" and "limit" respectively.

### Headers

Headers can be described using the `Endpoint#header` method which takes a `HeaderCodec[A]` and specifies that the given header is required, for example:

```scala mdoc:compile-only
val endpoint: Endpoint[String, (String, Header.Authorization), ZNothing, ZNothing, None] =
  Endpoint(RoutePattern.GET / "books" / PathCodec.string("genre"))
    .header(HeaderCodec.authorization)
```

### Request Body

The request body can be described using the `Endpoint#in` method:

```scala mdoc:compile-only
import zio.schema._

case class Book(title: String, author: String)

object Book {
  implicit val schema: Schema[Book] = DeriveSchema.gen[Book]
}

val endpoint: Endpoint[Unit, Book, ZNothing, ZNothing, None] =
  Endpoint(RoutePattern.POST / "books" )
    .in[Book]
```

The above example describes an endpoint that accepts a `Book` object as the request body.

By default, the request body is not named and its media type is determined by the `Content-Type` header. But for multipart form data, we can have multiple request bodies, called parts:

```scala mdoc:compile-only
val endpoint =
  Endpoint(RoutePattern.POST / "submit-form")
    .header(HeaderCodec.contentType.expect(Header.ContentType(MediaType.multipart.`form-data`)))
    .in[String]("title")
    .in[String]("author")
```

In the above example, we have defined an endpoint that describes a multipart form data request body with two parts: `title` and `author`. Let's see what the request body might look like:

```http request
POST /submit-form HTTP/1.1
Content-Type: multipart/form-data; boundary=boundary1234567890

--boundary1234567890
Content-Disposition: form-data; name="title"

The Title of the Book
--boundary1234567890
Content-Disposition: form-data; name="author"

John Doe
--boundary1234567890--
```

The `Endpoint#in` method has multiple overloads that can be used to describe other properties of the request body, such as the media type and documentation.

## Describing Output Properties of Success Responses

The `Endpoint#out` method is used to describe the output properties of the success response:

```scala mdoc:compile-only
import zio.http._
import zio.schema._

case class Book(title: String, author: String)

object Book {
  implicit val schema: Schema[Book] = DeriveSchema.gen[Book]
}

val endpoint: Endpoint[Unit, String, ZNothing, List[Book], None] =
  Endpoint(RoutePattern.GET / "books")
    .query(QueryCodec.query("q"))
    .out[List[Book]]
```

In the above example, we defined an endpoint that describes a query parameter `q` as input and returns a list of `Book` as output. The `Endpoint#out` method has multiple overloads that can be used to describe other properties of the output, such as the status code, media type, and documentation.

Sometimes based on the condition, we might want to return different types of responses. We can use the `Endpoint#out` method multiple times to describe different output types:

```scala mdoc:compile-only
import zio._
import zio.http.{RoutePattern, _}
import zio.http.endpoint.Endpoint
import zio.http.endpoint.EndpointMiddleware.None
import zio.schema.DeriveSchema.gen
import zio.schema._

case class Book(title: String, author: String)

object Book {
  implicit val schema: Schema[Book] = DeriveSchema.gen
}

case class Article(title: String, author: String)

object Article {
  implicit val schema: Schema[Article] = DeriveSchema.gen
}

case class Course(title: String, price: Double)
object Course {
  implicit val schema = DeriveSchema.gen[Course]
}

case class Quiz(question: String, level: Int)
object Quiz {
  implicit val schema = DeriveSchema.gen[Quiz]
}

object EndpointWithMultipleOutputTypes extends ZIOAppDefault {
  val endpoint: Endpoint[Unit, Unit, ZNothing, Either[Quiz, Course], None] =
    Endpoint(RoutePattern.GET / "resources")
      .out[Course]
      .out[Quiz]

  def run = Server.serve(
    endpoint.implement(handler {
      ZIO.randomWith(_.nextBoolean)
        .map(r =>
          if (r) Right(Course("Introduction to Programming", 49.99))
          else Left(Quiz("What is the boiling point of water in Celsius?", 2)),
        )
    })
    .toHttpApp).provide(Server.default, Scope.default)
}
```

In the above example, we defined an endpoint that describes a path parameter `id` as input and returns either a `Book` or an `Article` as output.

Sometimes we might want more control over the output properties, in such cases, we can provide a custom `HttpCodec` that describes the output properties using the `Endpoint#outCodec` method.

## Describing Failures

For failure outputs, we can describe the output properties using the `Endpoint#outError*` methods. Let's see an example:

```scala mdoc:passthrough
import utils._

printSource("zio-http-example/src/main/scala/example/endpoint/EndpointWithError.scala")
```

In the above example, we defined an endpoint that describes a path parameter `id` as input and returns a `Book` as output. If the book is not found, the endpoint returns a `NotFound` status code with a custom error message.

### Multiple Failure Outputs Using `Endpoint#outError`

If we have multiple failure outputs, we can use the `Endpoint#outError` method multiple times to describe different error types. By specifying more error types, the type of the endpoint will be the union of all the error types (e.g., `Either[Error1, Error2]`):

```scala mdoc:compile-only
import zio._
import zio.http._
import zio.schema._
import zio.schema.DeriveSchema

case class Book(title: String, authors: List[String])
case class BookNotFound(message: String, bookId: Int)
case class AuthenticationError(message: String, userId: Int)

implicit val bookSchema     = DeriveSchema.gen[Book]
implicit val notFoundSchema = DeriveSchema.gen[BookNotFound]
implicit val authSchema     = DeriveSchema.gen[AuthenticationError]

val endpoint: Endpoint[Int, (Int, Header.Authorization), Either[AuthenticationError, BookNotFound], Book, None] =
  Endpoint(RoutePattern.GET / "books" / PathCodec.int("id"))
    .header(HeaderCodec.authorization)
    .out[Book]
    .outError[BookNotFound](Status.NotFound)
    .outError[AuthenticationError](Status.Unauthorized)
```

<details>
<summary><b>Full Implementation Showcase</b></summary>

```scala mdoc:passthrough
import utils._

printSource("zio-http-example/src/main/scala/example/endpoint/EndpointWithMultipleErrorsUsingEither.scala")
```

</details>

### Multiple Failure Outputs `Endpoint#outErrors`

Alternatively, the idiomatic way to describe multiple failure outputs is to unify all the error types into a single error type using a sealed trait or an enum, and then describe the output properties using the `Endpoint#outErrors` method:

```scala mdoc:compile-only
import zio.schema.DeriveSchema

case class Book(title: String, authors: List[String])
implicit val bookSchema = DeriveSchema.gen[Book]

abstract class AppError(message: String)
case class BookNotFound(message: String, bookId: Int)        extends AppError(message)
case class AuthenticationError(message: String, userId: Int) extends AppError(message)

implicit val notFoundSchema = DeriveSchema.gen[BookNotFound]
implicit val authSchema     = DeriveSchema.gen[AuthenticationError]

val endpoint: Endpoint[Int, (Int, Header.Authorization), AppError, Book, None] =
  Endpoint(RoutePattern.GET / "books" / PathCodec.int("id"))
    .header(HeaderCodec.authorization)
    .out[Book]
    .outErrors[AppError](
      HttpCodec.error[BookNotFound](Status.NotFound),
      HttpCodec.error[AuthenticationError](Status.Unauthorized),
    )
```

The `Endpoint#outErrors` method takes a list of `HttpCodec` that describes the error types and their corresponding status codes.

<details>
<summary><b>Full Implementation Showcase</b></summary>

```scala mdoc:passthrough
utils.printSource("zio-http-example/src/main/scala/example/endpoint/EndpointWithMultipleUnifiedErrors.scala")
```
</details>

## Transforming Endpoint Input/Output and Error Types

To transform the input, output, and error types of an endpoint, we can use the `Endpoint#transformIn`, `Endpoint#transformOut`, and `Endpoint#transformError` methods, respectively. Let's see an example:

```scala mdoc:compile-only
case class BookQuery(query: String, genre: String, title: String)

val endpoint: Endpoint[String, (String, String, String), ZNothing, ZNothing, None] =
  Endpoint(RoutePattern.POST / "books" / PathCodec.string("genre"))
    .query(QueryCodec.query("q"))
    .query(QueryCodec.query("title"))

val mappedEndpoint: Endpoint[String, BookQuery, ZNothing, ZNothing, None] =
  endpoint.transformIn[BookQuery] { case (genre, q, title) => BookQuery(q, genre, title) } { i =>
    (i.genre, i.query, i.title)
  }
```

In the above example, we mapped over the input type of the `endpoint` and transformed it into a single `BookQuery` object. The `Endpoint#transformIn` method takes two functions, the first one is used to map the input type to the new input type, and the second one is responsible for mapping the new input type back to the original input type.

The `transformOut` and `transformError` methods work similarly to the `transformIn` method.

## OpenAPI Documentation

Every property of an `Endpoint` API can be annotated with documentation, may be examples using methods like `??` and `example*`. We can use these metadata to generate OpenAPI documentation:

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

Also, we can use the `@description` annotation from the `zio.schema.annotation` package to annotate data models, which will enrich the OpenAPI documentation:

```scala mdoc:compile-only
import zio.schema.annotation.description

case class Book(
  @description("Title of the book")
  title: String,
  @description("List of the authors of the book")
  authors: List[String],
)
```

The `OpenAPIGen.fromEndpoints` constructor generates OpenAPI documentation from the endpoints. By having the OpenAPI documentation, we can easily generate Swagger UI routes using the `SwaggerUI.routes` constructor:

```scala
val booksRoute = endpoint.implement(handler((query: String) => BookRepo.find(query)))
val openAPI    = OpenAPIGen.fromEndpoints(title = "Library API", version = "1.0", endpoint)
val swaggerRoutes = SwaggerUI.routes("docs" / "openapi", openAPI)
val routes     = Routes(booksRoute) ++ swaggerRoutes
```

<details>
<summary><b>Full Implementation Showcase</b></summary>

```scala mdoc:passthrough
import utils._

printSource("zio-http-example/src/main/scala/example/endpoint/BooksEndpointExample.scala")
```
</details>

## Generating Endpoint from OpenAPI Spec

With ZIO HTTP, we can generate endpoints from an OpenAPI specification. To do this, first, we need to add the following line to the `build.sbt` file:

```scala
libraryDependencies += "dev.zio" %% "zio-http-gen" % "@VERSION@"
```

Then we can generate the endpoints from the OpenAPI specification using the `EndpointGen.fromOpenAPI` constructor:

```scala mdoc:passthrough
import utils._

printSource("zio-http-example/src/main/scala/example/endpoint/GenerateEndpointFromOpenAPIExample.scala")
```

## Generating ZIO CLI App from Endpoint API

The ZIO CLI is a ZIO library that provides a way to build command-line applications using ZIO facilities. With ZIO HTTP, we can generate a ZIO CLI client from the `Endpoint` API.

To do this, first, we need to add the following line to the `build.sbt` file:

```scala
libraryDependencies += "dev.zio" %% "zio-http-cli" % "@VERSION@"
```

Then we can generate the ZIO CLI client from the `Endpoint` API using the `HttpCliApp.fromEndpoints` constructor:

```scala
object TestCliApp extends zio.cli.ZIOCliDefault with TestCliEndpoints {
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
}
```

Using the above code, we can create the `users-mgmt` CLI application that can be used to interact with the `getUser`, `getUserPosts`, and `createUser` endpoints:

```shell
                                                             __ 
  __  __________  __________      ____ ___  ____ _____ ___  / /_
 / / / / ___/ _ \/ ___/ ___/_____/ __ `__ \/ __ `/ __ `__ \/ __/
/ /_/ (__  )  __/ /  (__  )_____/ / / / / / /_/ / / / / / / /_  
\__,_/____/\___/_/  /____/     /_/ /_/ /_/\__, /_/ /_/ /_/\__/  
                                         /____/                 

users-mgmt v0.0.1 -- Users management CLI

USAGE

  $ users-mgmt <command>

COMMANDS

  - get-users --userId integer --location text                               Get a user by ID

  - get-users-posts --postId integer --userId integer --user-name text       Get a user's posts by userId and postId

  - create-users -f file|-u text|--.id integer --.name text [--.email text]  Create a new user

Copyright 2023
```

<details>
<summary><b>Full Implementation Showcase</b></summary>

```scala mdoc:passthrough
import utils._

printSource("zio-http-example/src/main/scala/example/endpoint/CliExamples.scala")
```

</details>
