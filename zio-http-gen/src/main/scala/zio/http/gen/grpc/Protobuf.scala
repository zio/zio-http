package zio.http.gen.grpc

/**
 * The minimal model of a Protocol Buffers file that the gRPC endpoint generator reads: a file, its services and their methods.
 */
sealed trait Protobuf

/**
 * The cases of `Protobuf`.
 */
object Protobuf {

  /**
   * A Protocol Buffers file.
   *
   * @param name the name of the file, used as the package and directory name of the generated sources
   * @param pkgPath the directories that prefix the paths of the generated files
   * @param dependencies the names of the files that this file imports
   * @param services the services that the file declares
   */
  case class File(name: String, pkgPath: List[String], dependencies: List[String], services: List[Service])
      extends Protobuf
  /**
   * A gRPC service with its methods.
   */
  case class Service(name: String, methods: List[Method]) extends Protobuf
  /**
   * A gRPC method.
   *
   * @param inputType the name of the request message type
   * @param outputType the name of the response message type
   * @param clientStreaming whether the client sends a stream of requests
   * @param serverStreaming whether the server sends a stream of responses
   */
  case class Method(
    name: String,
    inputType: String,
    outputType: String,
    clientStreaming: Boolean,
    serverStreaming: Boolean,
  ) extends Protobuf
}
