package zio.http.gen.grpc

import zio.http.Method
import zio.http.gen.scala.Code

/**
 * Generates ZIO HTTP endpoint code from a parsed Protocol Buffers file.
 */
object EndpointGen {

  /**
   * Returns one generated source file per service of `protobuf`.
   *
   * Each service becomes an object named after the capitalized service name, in
   * the file `<pkgPath>/<file name>/<Service>.scala`. The object holds one
   * `POST` endpoint per method, at the path `/<service>/<method>`. A
   * client-streaming method gets a streaming input (`inStream`) and a
   * server-streaming method a streaming output (`outStream`).
   *
   * Each entry of `protobuf.dependencies` adds the imports `<dependency>._` and
   * `<dependency>.Schemas._`, relative to the base package.
   */
  def fromProtobuf(protobuf: Protobuf.File): Code.Files = {
    val imports = protobuf.dependencies
      .flatMap(dep => Code.Import.FromBase(s"$dep._") :: Code.Import.FromBase(s"$dep.Schemas._") :: Nil)

    Code.Files {
      protobuf.services.map { s =>
        fromService(s, protobuf.pkgPath, protobuf.name, imports)
      }
    }
  }

  private def fromService(
    service: Protobuf.Service,
    pkg: List[String],
    name: String,
    imports: List[Code.Import],
  ): Code.File = {
    val className = service.name.capitalize
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

  private def objFromService(service: Protobuf.Service): Code.Object = {
    val name      = service.name
    val endpoints = service.methods.map { m =>
      Code.Field(m.name) -> endpoint(name, m)
    }
    val obj       = Code.Object(
      name = name.capitalize,
      extensions = Nil,
      schema = None,
      endpoints = endpoints.toMap,
      objects = Nil,
      caseClasses = Nil,
      enums = Nil,
    )
    obj
  }

  private def endpoint(
    service: String,
    method: Protobuf.Method,
  ): Code.EndpointCode = {
    val pathSegments = List(service, method.name).map(Code.PathSegmentCode.apply)
    val endpoint     = Code.EndpointCode(
      method = Method.POST,
      pathPatternCode = Code.PathPatternCode(pathSegments),
      queryParamsCode = Set.empty,
      headersCode = Code.HeadersCode.empty,
      inCode = Code.InCode(method.inputType).copy(streaming = method.clientStreaming),
      outCodes = List(
        Code.OutCode(
          outType = method.outputType,
          status = zio.http.Status.Ok,
          mediaType = Some("application/grpc"),
          doc = None,
          streaming = method.serverStreaming,
        ),
      ),
      errorsCode = Nil,
      authTypeCode = None,
    )
    endpoint
  }

}
