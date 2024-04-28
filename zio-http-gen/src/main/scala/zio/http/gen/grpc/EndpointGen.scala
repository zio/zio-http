package zio.http.gen.grpc

import scala.jdk.CollectionConverters._

import zio.http.Method
import zio.http.gen.scala.Code
import zio.http.gen.scala.Code.ScalaType

import com.google.protobuf.Descriptors

object EndpointGen {

  def fromProtobuf(protobuf: Descriptors.FileDescriptor): Code.Files = {
    val imports = protobuf
      .getDependencies()
      .asScala
      .toList
      .map(_.getName())
      .flatMap(dep => Code.Import.FromBase(s"$dep._") :: Code.Import.FromBase(s"$dep.Schemas._") :: Nil)
    val opt     = protobuf.getOptions()
    val pkg  = if (opt.hasJavaPackage() && opt.getJavaPackage() != "") opt.getJavaPackage() else protobuf.getPackage()
    val name =
      if (opt.hasJavaOuterClassname() && opt.getJavaOuterClassname() != "") opt.getJavaOuterClassname()
      else protobuf.getName()
    val pkgPath = if (pkg == "") Nil else pkg.split('.').toList

    Code.Files {
      protobuf.getServices().asScala.toList.map { s =>
        fromService(s, pkgPath, if (name.endsWith(".proto")) name.dropRight(6) else name, imports)
      }
    }
  }

  private def fromService(
    service: Descriptors.ServiceDescriptor,
    pkg: List[String],
    name: String,
    imports: List[Code.Import],
  ): Code.File = {
    val className = service.getName().capitalize
    val obj       = objFromService(service)
    Code.File(
      path = pkg ++ List(name, s"$className.scala"),
      pkgPath = List(name),
      imports = (Code.Import("zio.http.endpoint.grpc.GRPC._") :: Code.Import
        .FromBase(s"$name._") :: Code.Import.FromBase(s"$name.Schemas._") :: imports).distinct,
      objects = List(obj),
      caseClasses = Nil,
      enums = Nil,
    )
  }

  private def objFromService(service: Descriptors.ServiceDescriptor): Code.Object = {
    val name      = service.getName()
    val endpoints = service.getMethods.asScala.toList.map { m =>
      Code.Field(m.getName()) -> endpoint(name, m)
    }
    val obj       = Code.Object(
      name.capitalize,
      schema = false,
      endpoints = endpoints.toMap,
      objects = Nil,
      caseClasses = Nil,
      enums = Nil,
    )
    obj
  }

  private def endpoint(
    service: String,
    method: Descriptors.MethodDescriptor,
  ): Code.EndpointCode = {
    val pathSegments = List(service, method.getName()).map(Code.PathSegmentCode.apply)
    val imports      = Nil
    val endpoint     = Code.EndpointCode(
      method = Method.POST,
      pathPatternCode = Code.PathPatternCode(pathSegments),
      queryParamsCode = Set.empty,
      headersCode = Code.HeadersCode.empty,
      inCode = Code.InCode(method.getInputType().getName()).copy(streaming = method.isClientStreaming()),
      outCodes = List(
        Code.OutCode(
          method.getOutputType().getName(),
          zio.http.Status.Ok,
          Some("application/grpc"),
          None,
          method.isServerStreaming(),
        ),
      ),
      errorsCode = Nil,
    )
    endpoint
  }

}
