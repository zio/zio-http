package zio.http.grpc

import scala.jdk.CollectionConverters._

import zio.http.gen.grpc._
import zio.http.gen.scala.{Code, CodeGen}

import com.google.protobuf._
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse
import protocgen.{CodeGenApp, CodeGenRequest, CodeGenResponse}
import scalapb.compiler._

/**
 * A protoc plugin, built on ScalaPB's `CodeGenApp`, that generates zio-http
 * endpoint code for the services of `.proto` files. For each service it emits a
 * Scala file with the endpoints, and next to it a `Schemas.scala` file in the
 * same package with an implicit `Schema` for the request and response types of
 * that service's endpoints. The `Schemas.scala` file is named by the file path
 * without its last element, so two services in one `.proto` file produce two
 * `Schemas.scala` files with the same path.
 */
object ZIOHttpGRPCGen extends CodeGenApp {

  /**
   * Runs the plugin on a protoc request. Returns the `Schemas.scala` files
   * followed by the service files for the requested `.proto` files, or a
   * failure carrying the error when the plugin parameters cannot be parsed by
   * ScalaPB.
   */
  def process(request: CodeGenRequest): CodeGenResponse =
    ProtobufGenerator.parseParameters(request.parameter) match {
      case Right(_)    =>
        val services = request.filesToGenerate.flatMap(fromProtobuf(_).files)
        val schemas  = services.map(getImplicitSchemas).map { case (pkg, tpes) =>
          schemasFile(pkg, tpes)
        }
        CodeGenResponse.succeed(
          schemas ++ services.map(fileToPluginCode),
          Set(CodeGeneratorResponse.Feature.FEATURE_PROTO3_OPTIONAL),
        )
      case Left(error) =>
        CodeGenResponse.fail(error)
    }

  /**
   * Returns the endpoint code files that zio-http-gen generates for the
   * services of the given proto file descriptor, one file per service.
   */
  def fromProtobuf(file: Descriptors.FileDescriptor): Code.Files = {
    EndpointGen.fromProtobuf(fromDescriptor(file))
  }

  /**
   * Converts a generated code file to a protoc response file. The response file
   * is named by the code file's path joined with `/`, and its content is the
   * rendered source, with the package made of the path without its last two
   * elements (the proto package) followed by the proto file name.
   */
  def fileToPluginCode(file: Code.File): CodeGeneratorResponse.File = {
    val b = CodeGeneratorResponse.File.newBuilder()
    b.setName(file.path.mkString("/"))
    b.setContent(CodeGen.render(file.path.dropRight(2).mkString("."))(file)._2)
    b.build
  }

  /**
   * Returns the package path of the file and the request and response type
   * names of every endpoint in it. A type name appears once per use, so the
   * list can contain duplicates.
   */
  def getImplicitSchemas(file: Code.File): (List[String], List[String]) = {
    val msgs = file.objects
      .flatMap(_.endpoints.toList.map(_._2))
      .flatMap { endpoint =>
        endpoint.inCode.inType :: endpoint.outCodes.map(_.outType)
      }

    (file.path.init, msgs)
  }

  /**
   * Returns a protoc response file named `Schemas.scala` under the package
   * path. Its `Schemas` object holds one implicit `Schema` for each distinct
   * type name in `tpes`.
   */
  def schemasFile(pkg: List[String], tpes: List[String]): CodeGeneratorResponse.File = {
    val b       = CodeGeneratorResponse.File.newBuilder()
    b.setName((pkg ++ List("Schemas.scala")).mkString("/"))
    val content =
      s"package ${pkg.mkString(".")} \nobject Schemas {\n" +
        "import zio.schema.{DeriveSchema, Schema}\n" +
        tpes.distinct.map(tpeToSchema).mkString("") +
        "\n}"
    b.setContent(content)
    b.build
  }

  /**
   * Returns the source text of an implicit `Schema` for the type `tpe`, named
   * `<tpe>Codec` and derived with `DeriveSchema.gen`.
   */
  def tpeToSchema(tpe: String): String = {
    s"\n\n implicit val ${tpe}Codec: Schema[$tpe] = DeriveSchema.gen[$tpe]"
  }

  /**
   * Converts a protobuf file descriptor to the `Protobuf.File` model that
   * zio-http-gen reads. The package is the `java_package` option when it is set
   * and not empty, otherwise the proto package. The file name is the
   * `java_outer_classname` option when set and not empty, otherwise the
   * descriptor name, without a trailing `.proto`. Dependencies are the names of
   * the imported files, and each method keeps its input and output type names
   * and its client and server streaming flags.
   */
  def fromDescriptor(file: Descriptors.FileDescriptor): Protobuf.File = {
    val deps    = file.getDependencies.asScala.toList
      .map(_.getName())
    val opt     = file.getOptions
    val pkg     = if (opt.hasJavaPackage && opt.getJavaPackage != "") opt.getJavaPackage else file.getPackage
    val pkgPath = if (pkg == "") Nil else pkg.split('.').toList
    val name0   =
      if (opt.hasJavaOuterClassname && opt.getJavaOuterClassname != "") opt.getJavaOuterClassname
      else file.getName
    val name    = if (name0.endsWith(".proto")) name0.dropRight(6) else name0

    def fromService(service: Descriptors.ServiceDescriptor): Protobuf.Service =
      Protobuf.Service(
        service.getName,
        service.getMethods.asScala.toList.map(fromMethod),
      )

    def fromMethod(method: Descriptors.MethodDescriptor): Protobuf.Method =
      Protobuf.Method(
        method.getName,
        method.getInputType.getName,
        method.getOutputType.getName,
        method.isClientStreaming,
        method.isServerStreaming,
      )

    Protobuf.File(
      name,
      pkgPath,
      deps,
      file.getServices.asScala.toList.map(fromService),
    )
  }

}
