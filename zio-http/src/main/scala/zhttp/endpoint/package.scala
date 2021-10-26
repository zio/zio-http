package zhttp

import zhttp.http.Method

package object endpoint {

  /**
   * Extends Http Method to support syntax to create endpoints.
   */
  implicit class EndpointSyntax(method: Method) {
    def /(name: String): Endpoint[Unit] = Endpoint.fromMethod(method) / name
    def /[A](token: Parameter[A])(implicit ev: CanCombine.Aux[Unit, A, A]): Endpoint[A] =
      Endpoint.fromMethod(method) / token
  }

  /**
   * Alias to `Parameter[A]`
   */
  final def *[A](implicit ev: CanExtract[A]): Parameter[A] = Parameter[A]
}
