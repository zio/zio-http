package example.endpoint.style

import zio._
import zio.http._
import zio.schema.codec.JsonCodec.schemaBasedBinaryCodec
import zio.schema.{DeriveSchema, Schema}

object ImperativeProgrammingExample extends ZIOAppDefault {

  case class Book(title: String, authors: List[String])

  object Book {
    implicit val schema: Schema[Book] = DeriveSchema.gen[Book]
  }

  object BookRepo {

    case class NotFoundError(message: String)

    def find(id: String): ZIO[Any, NotFoundError, Book] =
      if (id == "1")
        ZIO.succeed(Book("Zionomicon", List("John A. De Goes", "Adam Fraser")))
      else
        ZIO.fail(NotFoundError("The requested book was not found!"))
  }

  val route: Route[Any, Response] =
    Method.GET / "books" -> handler { (req: Request) =>
      for {
        id    <- ZIO.fromOption(req.queryParam("id")).orElseFail(Response.badRequest("Missing query parameter id"))
        books <- BookRepo.find(id).mapError(err => Response.notFound(err.message))
      } yield Response.ok.copy(body = Body.from(books))
    }

  def run = Server.serve(route.toRoutes).provide(Server.default)
}
