package zhttp.service

import zhttp.service.client.domain.DefaultClient
import zio._

/**
 * Simple wrapper for getting a Client as ZLayer
 */
object ClientFactory {
  type ClientEnv = Has[DefaultClient]
  def client: ZLayer[Any, Nothing, ClientEnv] = ClientFactory.Live.defaultClient.toLayer

  object Live {
    def defaultClient: ZManaged[Any, Nothing, DefaultClient] =
      make(Client.make(ClientSettings.maxTotalConnections(20)))

    def make(dc: Task[DefaultClient]): ZManaged[Any, Nothing, DefaultClient] =
      dc.toManaged_.orDie
  }

}
