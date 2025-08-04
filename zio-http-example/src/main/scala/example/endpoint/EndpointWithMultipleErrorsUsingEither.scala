//> using dep "dev.zio::zio-http:3.3.3"
//> using dep "dev.zio::zio-schema:1.7.2"
//> using dep "dev.zio::zio-schema-derivation:1.7.3"

package example.endpoint

import zio._

import zio.schema.{DeriveSchema, Schema}

import zio.http._
import zio.http.codec._
import zio.http.endpoint.{AuthType, Endpoint}

object EndpointWithMultipleErrorsUsingEither extends ZIOAppDefault {

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

  object BookRepo {
    def find(id: Int): ZIO[Any, BookNotFound, Book] = {
      if (id == 1)
        ZIO.succeed(Book("Zionomicon", List("John A. De Goes", "Adam Fraser")))
      else
        ZIO.fail(BookNotFound("The requested book was not found.", id))
    }
  }

  val endpoint
    : Endpoint[Int, (Int, Header.Authorization), Either[AuthenticationError, BookNotFound], Book, AuthType.None] =
    Endpoint(RoutePattern.GET / "books" / PathCodec.int("id"))
      .header(HeaderCodec.authorization)
      .out[Book]
      .outError[BookNotFound](Status.NotFound)
      .outError[AuthenticationError](Status.Unauthorized)

  def isUserAuthorized(authHeader: Header.Authorization) = false

  val getBookHandler
    : Handler[Any, Either[AuthenticationError, BookNotFound], (RuntimeFlags, Header.Authorization), Book] =
    handler { (id: Int, authHeader: Header.Authorization) =>
      if (isUserAuthorized(authHeader))
        BookRepo.find(id).mapError(Right(_))
      else
        ZIO.fail(Left(AuthenticationError("User is not authenticated", 123)))
    }

  val routes = endpoint.implementHandler(getBookHandler).toRoutes @@ Middleware.debug

  def run = Server.serve(routes).provide(Server.default)
}
