package example

import zio._

import zio.http.Header.AcceptEncoding
import zio.http._
import zio.http.netty.NettyConfig

object ClientWithDecompression extends ZIOAppDefault {
  val url = URL.decode("https://jsonplaceholder.typicode.com/todos").toOption.get

  val program = for {
    client <- ZIO.service[Client]
    res    <- client.addHeader(AcceptEncoding(AcceptEncoding.GZip(), AcceptEncoding.Deflate())).url(url).get("")
    data   <- res.body.asString
    _      <- Console.printLine(data)
  } yield ()

  val config       = ZClient.Config.default.requestDecompression(true)
  override val run =
    program.provide(
      ZLayer.succeed(config),
      Client.live,
      ZLayer.succeed(NettyConfig.default),
      DnsResolver.default,
      Scope.default,
    )

}
