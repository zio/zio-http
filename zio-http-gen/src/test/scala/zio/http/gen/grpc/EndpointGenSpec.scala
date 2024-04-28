package zio.http.gen.grpc

import java.nio.file._

import scala.jdk.CollectionConverters._

import zio._
import zio.test._

import zio.http._
import zio.http.codec.HeaderCodec
import zio.http.codec.HttpCodec.{query, queryInt}
import zio.http.endpoint._
import zio.http.gen.model._
import zio.http.gen.scala.Code
import zio.http.gen.scala.Code.Collection.Opt

import com.google.protobuf.{DescriptorProtos, Descriptors}

object EndpointGenSpec extends ZIOSpecDefault {

  def toDescriptor(
    file: DescriptorProtos.FileDescriptorProto,
    imports: Array[Descriptors.FileDescriptor] = Array.empty,
  ): Descriptors.FileDescriptor = {
    Descriptors.FileDescriptor.buildFrom(file, imports, true)
  }

  def file(
    name: String,
    services: List[DescriptorProtos.ServiceDescriptorProto],
  ): DescriptorProtos.FileDescriptorProto = {
    val builder = DescriptorProtos.FileDescriptorProto.newBuilder
    builder.setName(name)
    builder.addAllService(services.toSeq.asJava)
    builder.build
  }

  def service(
    name: String,
    methods: List[DescriptorProtos.MethodDescriptorProto],
  ): DescriptorProtos.ServiceDescriptorProto = {
    val builder = DescriptorProtos.ServiceDescriptorProto.newBuilder
    builder.setName(name)
    builder.addAllMethod(methods.toSeq.asJava)
    builder.build
  }

  def method(name: String, in: String, out: String): DescriptorProtos.MethodDescriptorProto = {
    val builder = DescriptorProtos.MethodDescriptorProto.newBuilder
    builder.setName(name)
    builder.setInputType(in)
    builder.setOutputType(out)
    builder.build
  }

  def protobuf(in: String, out: String, imports: Array[Descriptors.FileDescriptor] = Array.empty) =
    toDescriptor(
      file(
        "api",
        service(
          "v1",
          method(
            "users",
            in,
            out,
          ) :: Nil,
        ) :: Nil,
      ),
      imports,
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
          toDescriptor(
            file(
              "dep",
              Nil,
            ),
          )
        val scala      = EndpointGen.fromProtobuf(protobuf("Request", "Response", Array(dependency)))
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
