package example.endpoint

import zio._

import zio.schema.DeriveSchema

import zio.http._
import zio.http.codec.{HeaderCodec, PathCodec}
import zio.http.endpoint.Endpoint
import zio.http.endpoint.EndpointMiddleware.None

object EndpointWithMultipleErrorsUsingEither extends ZIOAppDefault {

  case class Book(title: String, authors: List[String])

  object Book {
    implicit val schema = DeriveSchema.gen[Book]
  }

  case class BookNotFound(message: String, bookId: Int)

  object BookNotFound {
    implicit val schema = DeriveSchema.gen[BookNotFound]
  }

  case class AuthenticationError(message: String, userId: Int)

  object AuthenticationError {
    implicit val schema = DeriveSchema.gen[AuthenticationError]
  }

  object BookRepo {
    def find(id: Int): ZIO[Any, BookNotFound, Book] = {
      if (id == 1)
        ZIO.succeed(Book("Zionomicon", List("John A. De Goes", "Adam Fraser")))
      else
        ZIO.fail(BookNotFound("The requested book was not found.", id))
    }
  }

  val endpoint: Endpoint[Int, (Int, Header.Authorization), Either[BookNotFound, AuthenticationError], Book, None] =
    Endpoint(RoutePattern.GET / "books" / PathCodec.int("id"))
      .header(HeaderCodec.authorization)
      .out[Book]
      .outError[BookNotFound](Status.NotFound)
      .outError[AuthenticationError](Status.Unauthorized)

  def isUserAuthorized(authHeader: Header.Authorization) = false

  val getBookHandler
    : Handler[Any, Either[BookNotFound, AuthenticationError], (RuntimeFlags, Header.Authorization), Book] =
    handler { (id: Int, authHeader: Header.Authorization) =>
      if (isUserAuthorized(authHeader))
        BookRepo.find(id).mapError(Left(_))
      else
        ZIO.fail(Right(AuthenticationError("User is not authenticated", 123)))
    }

  val app = endpoint.implement(getBookHandler).toHttpApp @@ Middleware.debug

  def run = Server.serve(app).provide(Server.default, Scope.default)
}
