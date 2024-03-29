package example.endpoint

import zio._

import zio.schema.DeriveSchema

import zio.http._
import zio.http.codec.PathCodec
import zio.http.endpoint.Endpoint
import zio.http.endpoint.EndpointMiddleware.None

object EndpointWithError extends ZIOAppDefault {

  case class Book(title: String, authors: List[String])

  object Book {
    implicit val schema = DeriveSchema.gen[Book]
  }
  case class NotFoundError(error: String, message: String)

  object NotFoundError {
    implicit val schema = DeriveSchema.gen[NotFoundError]
  }

  object BookRepo {
    def find(id: Int): ZIO[Any, String, Book] = {
      if (id == 1)
        ZIO.succeed(Book("Zionomicon", List("John A. De Goes", "Adam Fraser")))
      else
        ZIO.fail("Not found")
    }
  }

  val endpoint: Endpoint[Int, Int, NotFoundError, Book, None] =
    Endpoint(RoutePattern.GET / "books" / PathCodec.int("id"))
      .out[Book]
      .outError[NotFoundError](Status.NotFound)

  val getBookHandler: Handler[Any, NotFoundError, Int, Book] =
    handler { (id: Int) =>
      BookRepo
        .find(id)
        .mapError(err => NotFoundError(err, "The requested book was not found. Please try using a different ID."))
    }

  val app = endpoint.implement(getBookHandler).toHttpApp @@ Middleware.debug

  def run = Server.serve(app).provide(Server.default, Scope.default)
}
