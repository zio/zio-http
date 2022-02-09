# ZIO HTTP Client

This section describes, ZIO HTTP Client and different configurations you can provide while creating the Client.

#Client API

ZIO HTTP Client API consists of:
 
 - `Client.make` - to create a new Client instance.

```scala
import zhttp.service.Client

val client = Client.make[Any] 
```
  - `Client.request` - it will create a client request. `url` is the only mandatory parameter, all the other parameters are optional. When no other parameters are provided, a GET request it is issued to that `url`.

```scala
import zhttp.service.Client

val url = "http://sports.api.decathlon.com/groups/water-aerobics"

val response = Client.request(url)

```
A call to `Client.request()` API will always return a `Response` wrapped into an  effect (`ZIO`).

A `Request` might be created in multiple ways:

- by providing parameters in call of `Client.request` API:
```scala
import zhttp.service.Client

val url = "http://sports.api.decathlon.com/groups/water-aerobics"

val request = Client.request(
    url,
    method: Method = Method.GET,   //  Method to be executed
    headers: Headers = Headers.empty,  // Headers to be added to the request
    content: HttpData = HttpData.empty, // Request content
    ssl: ClientSSLOptions = ClientSSLOptions.DefaultSSL,  // SSL configuration - used `DefaultSSL` when you do not need SSL
```
- by creating an instance of `ClientRequest` and pass it to `Client.request` API:
```scala
import zhttp.service.Client

val url = "http://sports.api.decathlon.com/groups/water-aerobics"
val headers = Headers.host("sports.api.decathlon.com")

val clientRequest = CLientRequest(url, Method.PUT, headers)

val response = Client.request(clientRequest)
```


#Create a simple client instance

```scala
package example

import zhttp.service.{ChannelFactory, Client, EventLoopGroup}
import zio.{App, ExitCode, URIO, console}

object SimpleClient extends App {
  val env = ChannelFactory.auto ++ EventLoopGroup.auto()
  val url = "http://sports.api.decathlon.com/groups/water-aerobics"

  val program = for {
    res  <- Client.request(url)
    data <- res.bodyAsString
    _    <- console.putStrLn { data }
  } yield ()

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = program.exitCode.provideCustomLayer(env)

}

```

This simple example is showing how to use the `Client` API to execute a simple `GET` request. The response returned is decoded to `String` and printed to the console.

# Simple HTTPS client

```scala

package example

import io.netty.handler.ssl.SslContextBuilder
import zhttp.http.Headers
import zhttp.service.client.ClientSSLHandler.ClientSSLOptions
import zhttp.service.{ChannelFactory, Client, EventLoopGroup}
import zio.{App, ExitCode, URIO, console}

import java.io.InputStream
import java.security.KeyStore
import javax.net.ssl.TrustManagerFactory

object HttpsClient extends App {
  val env     = ChannelFactory.auto ++ EventLoopGroup.auto()
  val url     = "https://sports.api.decathlon.com/groups/water-aerobics"
  val headers = Headers.host("sports.api.decathlon.com")

  // Configuring Truststore for https(optional)
  val trustStore: KeyStore                     = KeyStore.getInstance("JKS")
  val trustStorePath: InputStream              = getClass.getClassLoader.getResourceAsStream("truststore.jks")
  val trustStorePassword: String               = "changeit"
  val trustManagerFactory: TrustManagerFactory =
    TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm)

  trustStore.load(trustStorePath, trustStorePassword.toCharArray)
  trustManagerFactory.init(trustStore)

  val sslOption: ClientSSLOptions =
    ClientSSLOptions.CustomSSL(SslContextBuilder.forClient().trustManager(trustManagerFactory).build())

  val program = for {
    res  <- Client.request(url, headers = headers, ssl = sslOption)
    data <- res.bodyAsString
    _    <- console.putStrLn { data }
  } yield ()

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = program.exitCode.provideCustomLayer(env)

}

```

In case of `HTTPS` client before a request could be issued the `SSL` options has to be created. 

TODO: need more details on SSL side.

# Simple Websocket client

```scala
package example

import zhttp.service.{ChannelFactory, EventLoopGroup}
import zhttp.socket.{Socket, WebSocketFrame}
import zio._
import zio.stream.ZStream

object WebSocketSimpleClient extends zio.App {

  // Setup client envs
  val env = EventLoopGroup.auto() ++ ChannelFactory.auto

  val url = "ws://localhost:8090/subscriptions"

  val app = Socket
    .collect[WebSocketFrame] {
      case WebSocketFrame.Text("BAZ") => ZStream.succeed(WebSocketFrame.close(1000))
      case frame                      => ZStream.succeed(frame)
    }
    .toSocketApp
    .connect(url)

  override def run(args: List[String]): URIO[ZEnv, ExitCode] = {
    app.exitCode.provideCustomLayer(env)
  }
}

```