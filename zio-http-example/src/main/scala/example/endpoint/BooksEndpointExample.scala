package example.endpoint

import zio._

import zio.schema.annotation.description
import zio.schema.{DeriveSchema, Schema}

import zio.http._
import zio.http.codec.PathCodec._
import zio.http.codec._
import zio.http.endpoint._
import zio.http.endpoint.openapi._

object BooksEndpointExample extends ZIOAppDefault {
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
  val openAPI       = OpenAPIGen.fromEndpoints(title = "Library API", version = "1.0", endpoint)
  val swaggerRoutes = SwaggerUI.routes("docs" / "openapi", openAPI)
  val routes        = Routes(booksRoute) ++ swaggerRoutes

  def run = Server.serve(routes.toHttpApp).provide(Server.default, Scope.default)
}
