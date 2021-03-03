package zio.web

import zio.Task

trait ClientService[Ids] {

  def invoke[M[+_], I, O](endpoint: Endpoint[M, Unit, I, O])(input: I)(
    implicit ev: Ids <:< endpoint.Id
  ): Task[O]

  def invoke[M[+_], P, I, O](endpoint: Endpoint[M, P, I, O])(input: I, params: P)(
    implicit ev: Ids <:< endpoint.Id
  ): Task[O]
}
