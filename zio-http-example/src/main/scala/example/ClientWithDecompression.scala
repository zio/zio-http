package example

import zio._

import zio.http.Header.AcceptEncoding
import zio.http.netty.NettyConfig
import zio.http.{Client, DnsResolver, Headers, ZClient}

object ClientWithDecompression extends ZIOAppDefault {
  val url = "http://sports.api.decathlon.com/groups/water-aerobics"

  val program = for {
    res  <- Client.request(url, headers = Headers(AcceptEncoding(AcceptEncoding.GZip(), AcceptEncoding.Deflate())))
    data <- res.body.asString
    _    <- Console.printLine(data)
  } yield ()

  val config       = ZClient.Config.default.requestDecompression(true)
  override val run =
    program.provide(
      ZLayer.succeed(config),
      Client.live,
      ZLayer.succeed(NettyConfig.default),
      DnsResolver.default,
    )

}
