//> using dep "dev.zio::zio-http:3.4.1"
//> using dep "dev.zio::zio-schema:1.7.2"
//> using dep "dev.zio::zio-schema-json:1.7.2"
//> using dep "dev.zio::zio-schema-derivation:1.7.4"

package example.codecs

import zio._

import zio.schema.codec.JsonCodec.schemaBasedBinaryCodec
import zio.schema.{DeriveSchema, Schema}

import zio.http._

object RequestBodyJsonDeserializationExample extends ZIOAppDefault {

  case class Book(title: String, authors: List[String])

  object Book {
    implicit val schema: Schema[Book] = DeriveSchema.gen
  }

  val routes: Routes[Ref[List[Book]], Nothing] =
    Routes(
      Method.POST / "books" ->
        handler { (req: Request) =>
          for {
            book  <- req.body.to[Book].catchAll(_ => ZIO.fail(Response.badRequest("unable to deserialize the request")))
            books <- ZIO.service[Ref[List[Book]]]
            _     <- books.updateAndGet(_ :+ book)
          } yield Response.ok
        },
      Method.GET / "books"  ->
        handler { (_: Request) =>
          ZIO
            .serviceWithZIO[Ref[List[Book]]](_.get)
            .map(books => Response(body = Body.from(books)))
        },
    )

  def run = Server.serve(routes).provide(Server.default, ZLayer.fromZIO(Ref.make(List.empty[Book])))
}
