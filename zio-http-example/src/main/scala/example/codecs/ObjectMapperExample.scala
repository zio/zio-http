//> using dep "dev.zio::zio-http:3.3.1"
//> using dep "dev.zio::zio-schema:1.7.2"
//> using dep "dev.zio::zio-schema-derivation:1.7.2"

package example.codecs

import zio._

import zio.schema.codec.JsonCodec._
import zio.schema.{DeriveSchema, Schema}

import zio.http._

case class Book(title: String, authors: List[String])

object Book {
  implicit val schema: Schema[Book] = DeriveSchema.gen
}

case class PersonDTO(firstName: String, lastName: String, years: Int)
object PersonDTO {
  implicit val schema: Schema[PersonDTO] = DeriveSchema.gen
}

case class Person(name: String, age: Int)
object Person {
  implicit val personTransformation: Schema[Person] =
    PersonDTO.schema.transform[Person](
      f = (dto: PersonDTO) => Person(dto.firstName + " " + dto.lastName, dto.years),
      g = (person: Person) => {
        val name = person.name.split(" ").toSeq
        PersonDTO(name.head, name.tail.mkString(" "), person.age)
      },
    )
}

object ObjectMapperExample extends ZIOAppDefault {
  val routes: Routes[Ref[List[Person]], Nothing] =
    Routes(
      Method.POST / "person" ->
        handler { (req: Request) =>
          for {
            book  <- req.body.to[Person]
            books <- ZIO.service[Ref[List[Person]]]
            _     <- books.updateAndGet(_ :+ book)
          } yield Response.ok
        }.orDie,
      Method.GET / "persons" ->
        handler { (_: Request) =>
          ZIO
            .serviceWithZIO[Ref[List[Person]]](_.get)
            .map(books => Response(body = Body.from(books)))
        },
    )

  def run =
    Server
      .serve(routes)
      .provide(
        Server.default,
        ZLayer.fromZIO(Ref.make(List(Person("John Doe", 42)))),
      )
}
