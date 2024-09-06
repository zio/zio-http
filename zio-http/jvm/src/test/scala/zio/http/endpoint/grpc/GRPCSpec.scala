package zio.http.endpoint.grpc

import zio.test._

import zio.schema._

import zio.http._
import zio.http.codec.HttpContentCodec

object GRPCSpec extends ZIOSpecDefault {

  import zio.http.endpoint.grpc.GRPC._

  case class Person(name: String, id: Int)

  implicit val schema: Schema[Person] = DeriveSchema.gen[Person]

  val codec = implicitly[HttpContentCodec[Person]]

  override def spec =
    suite("GRPC imports")(
      test("correct mediatype") {

        assertTrue(codec.lookup(MediaType.parseCustomMediaType("application/grpc").get).isDefined)

      },
      test("encode and decode") {
        import zio.http.endpoint.grpc.GRPC.fromSchema

        val person   = Person("somePerson", 1)
        val encoded  = codec.encode(person).getOrElse(Body.empty)
        val response = Response(
          Status.Ok,
          Headers(Header.ContentType(MediaType.parseCustomMediaType("application/grpc").get)),
          encoded,
        )
        for {
          decoded <- codec.decodeResponse(response)
        } yield assertTrue(decoded == person)
      },
    )

}
