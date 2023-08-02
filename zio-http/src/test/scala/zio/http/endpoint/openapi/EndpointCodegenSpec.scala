package zio.http.endpoint.openapi

import zio.internal.stacktracer.SourceLocation
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

  type Pets = Chunk[Pet]

  final case class Pet(id: Long, name: String, tag: Option[String])

  object Pet {
    implicit val schema: zio.schema.Schema[Pet] = zio.schema.DeriveSchema.gen[Pet]
  }

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

  val addPet = Endpoint(POST / "pets").in[NewPet].out[Pet](Status.Ok).outError[Error](Status.InternalServerError)

  val findPetById = Endpoint(GET / "pets" / PathCodec.long("id")).out[Pet](Status.Ok).outError[Error](Status.InternalServerError)

  val deletePet = Endpoint(DELETE / "pets" / PathCodec.long("id")).outError[Error](Status.InternalServerError)

}
    """.trim,
    ),
    testCodeGen(
      "uspto",
      """
package codegen.example

import zio.Chunk
import zio.http.Status
import zio.http.codec.{PathCodec, QueryCodec}
import zio.http.endpoint.Endpoint
import zio.http.Method._

object USPTODataSetAPI {

  final case class DataSetListApisItem(apiKey: Option[String], apiVersionNumber: Option[String], apiUrl: Option[String], apiDocumentationUrl: Option[String])

  object DataSetListApisItem {
    implicit val schema: zio.schema.Schema[DataSetListApisItem] = zio.schema.DeriveSchema.gen[DataSetListApisItem]
  }

  final case class DataSetList(total: Option[Int], apis: Option[Chunk[DataSetListApisItem]])

  object DataSetList {
    implicit val schema: zio.schema.Schema[DataSetList] = zio.schema.DeriveSchema.gen[DataSetList]
  }

  final case class PerformSearchRequest(criteria: String, start: Option[Int], rows: Option[Int])

  object PerformSearchRequest {
    implicit val schema: zio.schema.Schema[PerformSearchRequest] = zio.schema.DeriveSchema.gen[PerformSearchRequest]
  }

  final case class PerformSearchResponseItem()

  object PerformSearchResponseItem {
    implicit val schema: zio.schema.Schema[PerformSearchResponseItem] = zio.schema.DeriveSchema.gen[PerformSearchResponseItem]
  }

  val listDataSets = Endpoint(GET / "").out[DataSetList](Status.Ok)

  val listSearchableFields = Endpoint(GET / PathCodec.string("dataset") / PathCodec.string("version") / "fields").out[String](Status.Ok).outError[String](Status.NotFound)

  val performSearch = Endpoint(POST / PathCodec.string("dataset") / PathCodec.string("version") / "records").in[PerformSearchRequest].out[Chunk[PerformSearchResponseItem]](Status.Ok)

}
    """.trim,
    ),
    testCodeGen(
      "articles",
      """
package codegen.example

import zio.Chunk
import zio.http.Status
import zio.http.codec.{PathCodec, QueryCodec}
import zio.http.endpoint.Endpoint
import zio.http.Method._

object APISpecificationExample {

  type Id = Long

  final case class Error(code: String)

  object Error {
    implicit val schema: zio.schema.Schema[Error] = zio.schema.DeriveSchema.gen[Error]
  }

  final case class Article(id: Option[Id], category: Option[String])

  object Article {
    implicit val schema: zio.schema.Schema[Article] = zio.schema.DeriveSchema.gen[Article]
  }

  type ArticleList = Chunk[ArticleForList]

  final case class ArticleForList(id: Option[Id], category: Option[String])

  object ArticleForList {
    implicit val schema: zio.schema.Schema[ArticleForList] = zio.schema.DeriveSchema.gen[ArticleForList]
  }

  val listArticles = Endpoint(GET / "articles").query(QueryCodec.queryAs[Int]("limit")).query(QueryCodec.queryAs[Int]("offset")).out[ArticleList](Status.Ok)

  val createArticle = Endpoint(POST / "articles").in[Article].out[Article](Status.Created).outError[Error](Status.BadRequest)

  val getArticle = Endpoint(GET / "articles" / PathCodec.long("id")).out[Article](Status.Ok).outError[Error](Status.NotFound)

  val updateArticle = Endpoint(PUT / "articles" / PathCodec.long("id")).in[Article].out[Article](Status.Ok).outError[Error](Status.NotFound)

  val deleteArticle = Endpoint(DELETE / "articles" / PathCodec.long("id")).outError[Error](Status.NotFound)

}
    """.trim,
    ),
  )

  def testCodeGen(name: String, expected: String)(implicit sourceLocation: SourceLocation): Spec[Any, Nothing] =
    test(s"generates endpoints from $name") {
      val jsonString = scala.io.Source.fromResource(s"openapi/$name.json").mkString
      val yamlString = scala.io.Source.fromResource(s"openapi/$name.yaml").mkString

      assertTrue(
        jsonCodeGen(jsonString) == expected,
        yamlCodeGen(yamlString) == expected,
      )
    }

  private def processCodeGen(codeString: Either[String, String]): String = {
    codeString.toOption.get.linesIterator
      .map(line => if (line.trim.isEmpty) "" else line)
      .mkString("\n")
      .trim
  }

  private def jsonCodeGen(jsonString: String): String =
    processCodeGen(Codegen.fromJsonSchema("codegen.example", jsonString))

  private def yamlCodeGen(jsonString: String): String =
    processCodeGen(Codegen.fromYamlSchema("codegen.example", jsonString))

}
