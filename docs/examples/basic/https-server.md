---
id: https-server
title: Https Server Example
sidebar_label: Https Server
---

```scala mdoc:silent
import zio._
import zio.http._

object HttpsHelloWorld extends ZIOAppDefault {
  // Create HTTP route
  val app: HttpApp[Any, Nothing] = Http.collect[Request] {
    case Method.GET -> Root / "text" => Response.text("Hello World!")
    case Method.GET -> Root / "json" => Response.json("""{"greetings": "Hello World!"}""")
  }

  /**
   * In this example an inbuilt API using keystore is used. For testing this
   * example using curl, setup the certificate named "server.crt" from resources
   * for the OS. Alternatively you can create the keystore and certificate using
   * the following link
   * https://medium.com/@maanadev/netty-with-https-tls-9bf699e07f01
   */

  val sslConfig = SSLConfig.fromResource(
    behaviour = SSLConfig.HttpBehaviour.Accept,
    certPath = "server.crt",
    keyPath = "server.key",
  )

  private val config = Server.Config.default
    .port(8090)
    .ssl(sslConfig)

  private val configLayer = ZLayer.succeed(config)

  override val run =
    Server.serve(app).provide(configLayer, Server.live)

}

```