//> using dep "dev.zio::zio-http:3.4.1"

package example

import zio._

import zio.http._
import zio.http.netty.NettyConfig
import zio.http.netty.client.NettyClientDriver

object HttpsClient extends ZIOAppDefault {
  val url     = URL.decode("https://jsonplaceholder.typicode.com/todos/1").toOption.get
  val headers = Headers(Header.Host("jsonplaceholder.typicode.com"))

  val sslConfig = ClientSSLConfig.FromTrustStoreResource(
    trustStorePath = "truststore.jks",
    trustStorePassword = "changeit",
  )

  val clientConfig = ZClient.Config.default.ssl(sslConfig)

  val program = for {
    data <- ZClient.batched(Request.get(url).addHeaders(headers))
    _    <- Console.printLine(data)
  } yield ()

  val run =
    program.provide(
      ZLayer.succeed(clientConfig),
      Client.customized,
      NettyClientDriver.live,
      DnsResolver.default,
      ZLayer.succeed(NettyConfig.default),
    )

}
