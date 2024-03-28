package example

import zio._
import zio.http._
import zio.http.codec.PathCodec
import zio.http.endpoint.Endpoint
import zio.http.endpoint.EndpointMiddleware.None
import zio.schema.DeriveSchema

object EndpointWithError extends ZIOAppDefault {

  case class Book(title: String, authors: List[String])

  object Book {
    implicit val schema = DeriveSchema.gen[Book]
  }

  object BookRepo {
    def find(id: Int): ZIO[Any, String, Book] = {
      if (id == 1)
        ZIO.succeed(Book("Zionomicon", List("John A. De Goes", "Adam Fraser")))
      else
        ZIO.fail("Not found")
    }
  }

  val endpoint: Endpoint[Int, Int, ErrorMessage, Book, None] =
    Endpoint(RoutePattern.GET / "books" / PathCodec.int("id"))
      .out[Book]
      .outError[ErrorMessage](Status.NotFound)

  def run =
    Server
      .serve(
        endpoint
          .implement(
            handler { (id: Int) =>
              BookRepo
                .find(id)
                .mapError(err =>
                  ErrorMessage(err, "The requested book was not found. Please try using a different ID."),
                )
            },
          )
          .toHttpApp @@ Middleware.debug,
      )
      .provide(Server.default, Scope.default)
}
