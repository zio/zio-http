package zio.http.endpoint.openapi

import zio.test._

object EndpointCodegenSpec extends ZIOSpecDefault {
  def spec = suite("EndpointGenerator")(
    testCodeGen(
      "petstore",
      """
package codegen.example

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

  val listPets = Endpoint(GET / "pets").query(QueryCodec.queryAs[Int]("limit")).out[Pets](Status.Ok).outError[Error](Status.InternalServerError)

  val createPets = Endpoint(POST / "pets").outError[Error](Status.InternalServerError)

  val showPetById = Endpoint(GET / "pets" / PathCodec.string("petId")).out[Pet](Status.Ok).outError[Error](Status.InternalServerError)

}
         """.trim,
    ),
    testCodeGen(
      "petstore-expanded",
      """
package codegen.example

import zio.Chunk
import zio.http.Status
import zio.http.codec.{PathCodec, QueryCodec}
import zio.http.endpoint.Endpoint
import zio.http.Method._

object SwaggerPetstore {

  final case class Pet(name: String, tag: Option[String], id: Long)

  object Pet {
    implicit val schema: zio.schema.Schema[Pet] = zio.schema.DeriveSchema.gen[Pet]
  }

  final case class NewPet(name: String, tag: Option[String])

  object NewPet {
    implicit val schema: zio.schema.Schema[NewPet] = zio.schema.DeriveSchema.gen[NewPet]
  }

  final case class Error(code: Int, message: String)

  object Error {
    implicit val schema: zio.schema.Schema[Error] = zio.schema.DeriveSchema.gen[Error]
  }

  val findPets = Endpoint(GET / "pets").query(QueryCodec.queryAs[Int]("limit")).out[Chunk[Pet]](Status.Ok).outError[Error](Status.InternalServerError)

  val addPet = Endpoint(POST / "pets").out[Pet](Status.Ok).outError[Error](Status.InternalServerError)

  val findPetById = Endpoint(GET / "pets" / PathCodec.long("id")).out[Pet](Status.Ok).outError[Error](Status.InternalServerError)

  val deletePet = Endpoint(DELETE / "pets" / PathCodec.long("id")).outError[Error](Status.InternalServerError)

}
    """.trim,
    ),
  )

  def testCodeGen(name: String, expected: String): Spec[Any, Nothing] =
    test(s"generates endpoints from $name") {
      val jsonString = scala.io.Source.fromResource(s"openapi/$name.json").mkString
      val yamlString = scala.io.Source.fromResource(s"openapi/$name.yaml").mkString

      assertTrue(
        jsonCodeGen(jsonString) == expected,
        yamlCodeGen(yamlString) == expected,
      )
    }

  private def processCodeGen(codeString: Either[String, String]): String =
    codeString.toOption.get
      .split("\n")
      .map(line => if (line.isBlank) line.trim else line)
      .mkString("\n")
      .trim

  private def jsonCodeGen(jsonString: String): String =
    processCodeGen(Codegen.fromJsonSchema("codegen.example", jsonString))

  private def yamlCodeGen(jsonString: String): String =
    processCodeGen(Codegen.fromYamlSchema("codegen.example", jsonString))

}
