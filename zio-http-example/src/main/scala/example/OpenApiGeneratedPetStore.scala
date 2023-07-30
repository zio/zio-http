package example.petstore

import zio.Chunk
import zio.http.Status
import zio.http.codec.{PathCodec, QueryCodec}
import zio.http.endpoint.Endpoint
import zio.http.Method._

object SwaggerPetstore {

  final case class Pet(id: Long, name: String, tag: Option[String])

  object Pet {
    implicit val schema: zio.schema.Schema[Pet] = zio.schema.DeriveSchema.gen[Pet]
  }

  type Pets = Chunk[Pet]

  final case class Error(code: Int, message: String)

  object Error {
    implicit val schema: zio.schema.Schema[Error] = zio.schema.DeriveSchema.gen[Error]
  }

  val listPets = Endpoint(GET / "pets")
    .query(QueryCodec.queryAs[Int]("limit"))
    .out[Pets](Status.Ok)
    .outError[Error](Status.InternalServerError)

  val createPets = Endpoint(POST / "pets").outError[Error](Status.InternalServerError)

  val showPetById =
    Endpoint(GET / "pets" / PathCodec.string("petId")).out[Pet](Status.Ok).outError[Error](Status.InternalServerError)

}
