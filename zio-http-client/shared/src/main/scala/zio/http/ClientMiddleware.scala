package zio.http

trait ClientMiddleware { self =>
  def apply(client: Client): Client

  def andThen(that: ClientMiddleware): ClientMiddleware =
    new ClientMiddleware {
      def apply(client: Client): Client = that(self(client))
    }
}

object ClientMiddleware {
  def apply(f: Client => Client): ClientMiddleware =
    new ClientMiddleware {
      def apply(client: Client): Client = f(client)
    }

  val identity: ClientMiddleware =
    new ClientMiddleware {
      def apply(client: Client): Client = client
    }
}
