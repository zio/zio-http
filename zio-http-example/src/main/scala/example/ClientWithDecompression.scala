//> using dep "dev.zio::zio-http:3.4.1"

package example

import zio._

import zio.http.Header.AcceptEncoding
import zio.http._
import zio.http.netty.NettyConfig

object ClientWithDecompression extends ZIOAppDefault {

  val program = for {
    url    <- ZIO.fromEither(URL.decode("https://jsonplaceholder.typicode.com"))
    client <- ZIO.serviceWith[Client](_.addUrl(url))
    res    <-
      client
        .addHeader(AcceptEncoding(AcceptEncoding.GZip(), AcceptEncoding.Deflate()))
        .batched(Request.get("/todos"))
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
    )

}
