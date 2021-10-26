package zhttp

import zhttp.endpoint.Endpoint.{EParam, EToken}
import zhttp.http.Method

package object endpoint {

  /**
   * Implicitly convert any method to Route
   */
  implicit class MethodToRoute(method: Method) {
    def /(name: String): Endpoint[Unit]     = Endpoint.fromMethod(method) / name
    def /[A](token: EToken[A]): Endpoint[A] = Endpoint.fromMethod(method) / token
  }

  /**
   * Operator to create RouteToken with param of type A
   */
  final def *[A](implicit ev: EParam[A]): EToken[A] = Endpoint[A]
}
