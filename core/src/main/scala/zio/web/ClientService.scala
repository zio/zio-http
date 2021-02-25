package zio.web

import zio.Task

trait ClientService[Identities] {
  def invoke[I, O](endpoint: Endpoint[_, I, O])(input: I)(implicit ev: Identities <:< endpoint.Identity): Task[O]
}
