package zio.http.grpc

import zio.http.gen.grpc.EndpointGen
import zio.http.gen.scala.{Code, CodeGen}

import com.google.protobuf.Descriptors.FileDescriptor
import com.google.protobuf.ExtensionRegistry
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse
import protocgen.{CodeGenApp, CodeGenRequest, CodeGenResponse}
import scalapb.compiler.{DescriptorImplicits, ProtobufGenerator}
import scalapb.options.Scalapb

object ZIOHttpGRPCGen extends CodeGenApp {
  override def registerExtensions(registry: ExtensionRegistry): Unit =
    Scalapb.registerAllExtensions(registry)

  def process(request: CodeGenRequest): CodeGenResponse =
    ProtobufGenerator.parseParameters(request.parameter) match {
      case Right(params) =>
        val services = request.filesToGenerate.flatMap(fromProtobuf(_).files)
        val schemas  = services.map(getImplicitSchemas(_)).map { case (pkg, tpes) =>
          schemasFile(pkg, tpes)
        }
        CodeGenResponse.succeed(
          schemas ++ services.map(fileToPluginCode(_)),
          Set(CodeGeneratorResponse.Feature.FEATURE_PROTO3_OPTIONAL),
        )
      case Left(error)   =>
        CodeGenResponse.fail(error)
    }

  def fromProtobuf(file: FileDescriptor): Code.Files = {
    EndpointGen.fromProtobuf(file)
  }

  def fileToPluginCode(file: Code.File): CodeGeneratorResponse.File = {
    val b = CodeGeneratorResponse.File.newBuilder()
    b.setName(file.path.mkString("/"))
    b.setContent(CodeGen.render(file.path.dropRight(2).mkString("."))(file)._2)
    b.build
  }

  def getImplicitSchemas(file: Code.File): (List[String], List[String]) = {
    val msgs = file.objects
      .flatMap(_.endpoints.toList.map(_._2))
      .flatMap { endpoint =>
        endpoint.inCode.inType :: endpoint.outCodes.map(_.outType)
      }

    (file.path.init, msgs)
  }

  def schemasFile(pkg: List[String], tpes: List[String]): CodeGeneratorResponse.File = {
    val b       = CodeGeneratorResponse.File.newBuilder()
    b.setName((pkg ++ List("Schemas.scala")).mkString("/"))
    val content =
      s"package ${pkg.mkString(".")} \nobject Schemas {\n" +
        "import zio.schema.{DeriveSchema, Schema}\n" +
        tpes.distinct.map(tpeToSchema(_)).mkString("") +
        "\n}"
    b.setContent(content)
    b.build
  }

  def tpeToSchema(tpe: String): String = {
    s"\n\n implicit val ${tpe}Codec: Schema[$tpe] = DeriveSchema.gen[$tpe]"
  }

}
