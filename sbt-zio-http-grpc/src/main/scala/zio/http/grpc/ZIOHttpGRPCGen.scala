package zio.http.grpc

import scala.jdk.CollectionConverters._

import zio.http.gen.grpc._
import zio.http.gen.scala.{Code, CodeGen}

import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse
import com.google.protobuf.{Descriptors, ExtensionRegistry}
import protocgen.{CodeGenApp, CodeGenRequest, CodeGenResponse}
import scalapb.compiler.{DescriptorImplicits, ProtobufGenerator}
import scalapb.options.Scalapb

object ZIOHttpGRPCGen extends CodeGenApp {

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

  def fromProtobuf(file: Descriptors.FileDescriptor): Code.Files = {
    EndpointGen.fromProtobuf(fromDescriptor(file))
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

  def fromDescriptor(file: Descriptors.FileDescriptor): Protobuf.File = {
    val deps    = file
      .getDependencies()
      .asScala
      .toList
      .map(_.getName())
    val opt     = file.getOptions()
    val pkg     = if (opt.hasJavaPackage() && opt.getJavaPackage() != "") opt.getJavaPackage() else file.getPackage()
    val pkgPath = if (pkg == "") Nil else pkg.split('.').toList
    val name0   =
      if (opt.hasJavaOuterClassname() && opt.getJavaOuterClassname() != "") opt.getJavaOuterClassname()
      else file.getName()
    val name    = if (name0.endsWith(".proto")) name0.dropRight(6) else name0

    def fromService(service: Descriptors.ServiceDescriptor): Protobuf.Service =
      Protobuf.Service(
        service.getName(),
        service.getMethods.asScala.toList.map(fromMethod(_)),
      )

    def fromMethod(method: Descriptors.MethodDescriptor): Protobuf.Method =
      Protobuf.Method(
        method.getName(),
        method.getInputType().getName(),
        method.getOutputType().getName(),
        method.isClientStreaming(),
        method.isServerStreaming(),
      )

    Protobuf.File(
      name,
      pkgPath,
      deps,
      file.getServices().asScala.toList.map(fromService(_)),
    )
  }

}
