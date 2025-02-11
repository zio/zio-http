package zio.http.gen.grpc

import java.nio.file._

import zio._
import zio.test._

import zio.http._
import zio.http.gen.scala.Code

object EndpointGenSpec extends ZIOSpecDefault {

  def descriptor(
    name: String,
    imports: List[Protobuf.File],
    services: List[Protobuf.Service],
  ): Protobuf.File =
    Protobuf.File(
      name,
      List.empty,
      imports.map(_.name),
      services,
    )

  def protobuf(in: String, out: String, imports: List[Protobuf.File] = List.empty) =
    descriptor(
      "api",
      imports,
      Protobuf.Service(
        "v1",
        Protobuf.Method(
          "users",
          in,
          out,
          false,
          false,
        ) :: Nil,
      ) :: Nil,
    )

  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("EndpointGenSpec")(
      test("right package and file name") {
        val scala     = EndpointGen.fromProtobuf(protobuf("Unit", "Unit"))
        val filePath  = Paths.get("/api", "V1.scala")
        val pkgPath   = List("api")
        val firstFile = scala.files.head
        assertTrue(firstFile.pkgPath == pkgPath, firstFile.path.mkString("/", "/", "") == filePath.toString)
      },
      test("request and response") {
        val scala    = EndpointGen.fromProtobuf(protobuf("Request", "Response"))
        val expected = Code.File(
          List("api", "V1.scala"),
          pkgPath = List("api"),
          imports = List(
            Code.Import("zio.http.endpoint.grpc.GRPC._"),
            Code.Import.FromBase(path = "api._"),
            Code.Import.FromBase(path = "api.Schemas._"),
          ),
          objects = List(
            Code.Object(
              "V1",
              Map(
                Code.Field("users") -> Code.EndpointCode(
                  Method.POST,
                  Code.PathPatternCode(segments = List(Code.PathSegmentCode("v1"), Code.PathSegmentCode("users"))),
                  queryParamsCode = Set.empty,
                  headersCode = Code.HeadersCode.empty,
                  inCode = Code.InCode("Request"),
                  outCodes = List(
                    Code.OutCode(
                      outType = "Response",
                      status = Status.Ok,
                      mediaType = Some("application/grpc"),
                      doc = None,
                      streaming = false,
                    ),
                  ),
                  errorsCode = Nil,
                ),
              ),
            ),
          ),
          Nil,
          Nil,
        )
        assertTrue(scala.files.head == expected)
      },
      test("adding imports") {
        val dependency =
          descriptor(
            "dep",
            Nil,
            Nil,
          )
        val scala      = EndpointGen.fromProtobuf(protobuf("Request", "Response", List(dependency)))
        val expected   = Code.File(
          List("api", "V1.scala"),
          pkgPath = List("api"),
          imports = List(
            Code.Import("zio.http.endpoint.grpc.GRPC._"),
            Code.Import.FromBase(path = "api._"),
            Code.Import.FromBase(path = "api.Schemas._"),
            Code.Import.FromBase(path = "dep._"),
            Code.Import.FromBase(path = "dep.Schemas._"),
          ),
          objects = List(
            Code.Object(
              "V1",
              Map(
                Code.Field("users") -> Code.EndpointCode(
                  Method.POST,
                  Code.PathPatternCode(segments = List(Code.PathSegmentCode("v1"), Code.PathSegmentCode("users"))),
                  queryParamsCode = Set.empty,
                  headersCode = Code.HeadersCode.empty,
                  inCode = Code.InCode("Request"),
                  outCodes = List(
                    Code.OutCode(
                      outType = "Response",
                      status = Status.Ok,
                      mediaType = Some("application/grpc"),
                      doc = None,
                      streaming = false,
                    ),
                  ),
                  errorsCode = Nil,
                ),
              ),
            ),
          ),
          Nil,
          Nil,
        )
        assertTrue(scala.files.head == expected)
      },
    )

}
