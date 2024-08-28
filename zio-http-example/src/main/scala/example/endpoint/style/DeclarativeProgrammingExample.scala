package example.endpoint.style

import zio._

import zio.schema.{DeriveSchema, Schema}

import zio.http._
import zio.http.codec._
import zio.http.endpoint.{AuthType, Endpoint}

object DeclarativeProgrammingExample extends ZIOAppDefault {

  case class Book(title: String, authors: List[String])

  object Book {
    implicit val schema: Schema[Book] = DeriveSchema.gen
  }
  case class NotFoundError(message: String)

  object NotFoundError {
    implicit val schema: Schema[NotFoundError] = DeriveSchema.gen
  }

  object BookRepo {
    def find(id: String): ZIO[Any, NotFoundError, Book] = {
      if (id == "1")
        ZIO.succeed(Book("Zionomicon", List("John A. De Goes", "Adam Fraser")))
      else
        ZIO.fail(NotFoundError("The requested book was not found!"))
    }
  }

  val endpoint: Endpoint[Unit, String, NotFoundError, Book, AuthType.None] =
    Endpoint(RoutePattern.GET / "books")
      .query(HttpCodec.query[String]("id"))
      .out[Book]
      .outError[NotFoundError](Status.NotFound)

  val getBookHandler: Handler[Any, NotFoundError, String, Book] =
    handler(BookRepo.find(_))

  val routes = endpoint.implementHandler(getBookHandler).toRoutes @@ Middleware.debug

  def run = Server.serve(routes).provide(Server.default)
}
