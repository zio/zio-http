package example.codecs

import zio._

import zio.schema.codec.JsonCodec.schemaBasedBinaryCodec
import zio.schema.{DeriveSchema, Schema}

import zio.http._

object ResponseBodyJsonSerializationExample extends ZIOAppDefault {

  case class Book(title: String, authors: List[String])

  object Book {
    implicit val schema: Schema[Book] = DeriveSchema.gen
  }

  val book1 = Book("Programming in Scala", List("Martin Odersky", "Lex Spoon", "Bill Venners", "Frank Sommers"))
  val book2 = Book("Zionomicon", List("John A. De Goes", "Adam Fraser"))
  val book3 = Book("Effect-Oriented Programming", List("Bill Frasure", "Bruce Eckel", "James Ward"))

  val routes: Routes[Any, Nothing] =
    Routes(
      Method.GET / "users" ->
        handler(Response(body = Body.from(List(book1, book2, book3)))),
    )

  def run = Server.serve(routes).provide(Server.default)
}
