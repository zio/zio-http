---
id: https-client
title:  HTTPS Client 
---

This code demonstrate a simple HTTPS client that send an HTTP GET request to a specific URL and retrieve the response.


## code

```scala
import zio._

import zio.http._
import zio.http.netty.NettyConfig
import zio.http.netty.client.NettyClientDriver

object HttpsClient extends ZIOAppDefault {
  val url     = "https://sports.api.decathlon.com/groups/water-aerobics"
  val headers = Headers(Header.Host("sports.api.decathlon.com"))

  val sslConfig = ClientSSLConfig.FromTrustStoreResource(
    trustStorePath = "truststore.jks",
    trustStorePassword = "changeit",
  )

  val clientConfig = ZClient.Config.default.ssl(sslConfig)

  val program = for {
    res  <- Client.request(url, headers = headers)
    data <- res.body.asString
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

```