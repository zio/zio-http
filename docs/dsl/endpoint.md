---
id: endpoint
title: Endpoint
---

The `Endpoint` API in ZIO HTTP, is an alternative way to describe the endpoints but in a declarative way. It is a high-level API that allows us to describe the endpoints and their inputs, outputs, and how they should look like. So we can think of it as a DSL for just describing the endpoints, and then we can implement them separately.

Using `Endpoint` API enables us to generate OpenAPI documentation, and also to generate clients for the endpoints.

Here is a simple example of how to define an endpoint using the `Endpoint` API:

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
val swaggerRoute = SwaggerUI.routes("docs" / "openapi", openAPI)
```

And finally we are ready to serve all the routes. Let's see the complete example:

```scala mdoc:compile-only
import zio._
import zio.http._
import zio.http.codec.PathCodec._
import zio.http.codec._
import zio.http.endpoint._
import zio.http.endpoint.openapi._
import zio.schema.DeriveSchema

object BooksEndpointExample extends ZIOAppDefault {
  case class Book(title: String, authors: List[String])
  object Book {
    implicit val schema = DeriveSchema.gen[Book]
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
    Endpoint(RoutePattern.GET / "books")
      .query(QueryCodec.queryTo[String]("q") examples (("example1", "scala"), ("example2", "zio")))
      .out[List[Book]]

  val booksRoute = endpoint.implement(handler((query: String) => BookRepo.find(query)))
  val openAPI    = OpenAPIGen.fromEndpoints(title = "Library API", version = "1.0", endpoint)
  val routes     = Routes(booksRoute) ++ SwaggerUI.routes("docs" / "openapi", openAPI)

  def run = Server.serve(routes.toHttpApp).provide(Server.default, Scope.default)
}
```

By running the above example, other than the main `/books` route, we can also access the OpenAPI documentation using the SwaggerUI at the `/docs/openapi` route.

This was an overview of the `Endpoint` API in ZIO HTTP. Next, we will dive deeper into the `Endpoint` API and see how we can describe the endpoints in more detail.
