package zio.web

import zio.Task

trait Client[MinMetadata[_], Ids] {
  final def invoke[M[+_] <: MinMetadata[_], I, O](endpoint: Endpoint[M, Unit, I, O])(input: I)(
    implicit ev: Ids <:< endpoint.Id
  ): Task[O] = invoke[Unit, M, I, O](endpoint)((), input)

  def invoke[P, M[+_] <: MinMetadata[_], I, O](endpoint: Endpoint[M, P, I, O])(params: P, input: I)(
    implicit ev: Ids <:< endpoint.Id
  ): Task[O]
}
