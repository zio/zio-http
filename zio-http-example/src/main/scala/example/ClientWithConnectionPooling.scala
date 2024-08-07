package example

import zio._

import zio.http._
import zio.http.netty.NettyConfig

object ClientWithConnectionPooling extends ZIOAppDefault {
  val program = for {
    url    <- ZIO.fromEither(URL.decode("http://jsonplaceholder.typicode.com/posts"))
    client <- ZIO.serviceWith[Client](_.addUrl(url))
    _      <- ZIO.foreachParDiscard(Chunk.fromIterable(1 to 100)) { i =>
      client.quickWithZIO(Request.get(i.toString))(_.body.asString).debug
    }
  } yield ()

  val config = ZClient.Config.default.dynamicConnectionPool(10, 20, 5.second)

  override val run =
    program.provide(
      ZLayer.succeed(config),
      Client.live,
      ZLayer.succeed(NettyConfig.default),
      DnsResolver.default,
    )
}
