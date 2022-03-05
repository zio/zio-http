package zhttp.service

import zhttp.service.client.domain.DefaultClient
import zio._

/**
 * Simple wrapper for getting a Client as ZLayer
 */
object ClientFactory {
  type ClientEnv = Has[DefaultClient]
  def clientLayer(clientSettings: ClientSettings = ClientSettings.defaultSetting): ZLayer[Any, Nothing, ClientEnv] =
    ClientFactory.Live.defaultClient(clientSettings).toLayer

  object Live {
    def defaultClient(clientSettings: ClientSettings): ZManaged[Any, Nothing, DefaultClient] = {
      Client
        .make(clientSettings)
        .toManaged_
        .orDie
    }
  }

}
