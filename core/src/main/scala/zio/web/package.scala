package zio

import zio.schema.Schema
import zio.web.docs._

/**
 * In gRPC, services are described by 1 or more "methods" with a name, e.g.:
 *
 *    rpc HelloWorld(HelloRequest) returns (HelloResponse);
 *
 *    rpc GetUserProfile(UserIdRequest) returns (UserProfileResponse)
 *
 * In HTTP, services are described by 1 or more routes, consisting of HTTP Method, URL pattern, header pattern, e.g.:
 *
 *    GET /users/{id}
 *    Content-type: application/json
 *
 *    POST /users-service/get-user-profile?userId=123
 *
 * In Thrift, services are described by 1 or more "methods" with a name, e.g.:
 *
 *    string helloWorld(string input)
 */
package object web {
  type AnyF[+A]        = Any
  type Id[A]           = A
  type NothingF[+A]    = Nothing
  type Unannotated[+A] = Nothing

  /**
   * Constructs a new endpoint with the specified name.
   */
  final def endpoint(name: String): Endpoint[Unannotated, Unit, Unit, Unit] =
    Endpoint(name, Doc.Empty, Schema[Unit], Schema[Unit], Annotations.none)

  /**
   * Constructs a new endpoint with the specified name and text documentation.
   */
  final def endpoint(name: String, text: String): Endpoint[Unannotated, Unit, Unit, Unit] =
    endpoint(name) ?? text
}
