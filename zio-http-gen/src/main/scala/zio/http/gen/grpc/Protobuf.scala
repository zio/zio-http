package zio.http.gen.grpc

sealed trait Protobuf

object Protobuf {

  case class File(name: String, pkgPath: List[String], dependencies: List[String], services: List[Service])
      extends Protobuf
  case class Service(name: String, methods: List[Method]) extends Protobuf
  case class Method(
    name: String,
    inputType: String,
    outputType: String,
    clientStreaming: Boolean,
    serverStreaming: Boolean,
  ) extends Protobuf
}
