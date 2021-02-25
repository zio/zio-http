package zio.web

import zio.Task

trait ClientService[Identities] {
  type AnyF[A] = Any

  def invoke[I, O](endpoint: Endpoint[AnyF, _, I, O])(input: I)(implicit ev: Identities <:< endpoint.Identity): Task[O]
}
