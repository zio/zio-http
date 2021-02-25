package zio.web

import zio.Task

trait ClientService[Identities] {

  def invoke[M[+_], P, I, O](endpoint: Endpoint[M, P, I, O])(input: I, params: P)(
    implicit ev: Identities <:< endpoint.Identity
  ): Task[O]
}
