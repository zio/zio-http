package zhttp.internal

//import zhttp.http._
import zhttp.service.{Client, ClientSettings}
import zhttp.service.client.model.DefaultClient
import zio._

object DynamicClient {

  type DynamicClientEnv = Has[DynamicClient.Service]

  val oneClient = Client.make(ClientSettings.maxTotalConnections(20))
  def getClient: ZIO[DynamicClientEnv, Throwable, DefaultClient] = {
    println(s"INVOKING GET CLIENT")
    ZIO.accessM[DynamicClientEnv](_.get.getClient)
  }

  val live: ZLayer[Any, Nothing, DynamicClientEnv] = ZLayer.succeed{
    new Service {
      override def getClient: Task[DefaultClient] = oneClient
    }
  }

  sealed trait Service {
    def getClient: Task[DefaultClient]
  }
}
