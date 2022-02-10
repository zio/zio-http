package zhttp.internal

//import zhttp.http._
import zhttp.service.{Client, ClientSettings}
import zhttp.service.client.model.DefaultClient
import zio._

object DynamicClient {

  val singletonLive = new Live()

  val client = {
    Client.make(ClientSettings.maxTotalConnections(20))
  }

  type DynamicClientEnv = Has[DynamicClient.Service]

  def getClient: ZIO[DynamicClientEnv, Throwable, DefaultClient] = {
    println(s"INVOKING GET CLIENT")
    ZIO.accessM[DynamicClientEnv](_.get.getClient)
  }

  def live: ZLayer[Any, Nothing, DynamicClientEnv] = ZLayer.succeed{
    singletonLive
  }

  sealed trait Service {
    def getClient: Task[DefaultClient]
  }

  final class Live extends Service {
    def getClient = client
  }
}
