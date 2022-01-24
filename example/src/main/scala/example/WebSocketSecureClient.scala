package example

import io.netty.handler.ssl.SslContextBuilder
import zhttp.service.client.ClientSSLHandler.ClientSSLOptions
import zhttp.service.{ChannelFactory, Client, EventLoopGroup}
import zhttp.socket.{Socket, SocketProtocol, WebSocketFrame}
import zio.stream.ZStream
import zio.{ExitCode, URIO, ZEnv, ZIO, console}

import java.io.InputStream
import java.security.KeyStore
import javax.net.ssl.TrustManagerFactory

object WebSocketSecureClient extends zio.App {
  private val env = EventLoopGroup.auto() ++ ChannelFactory.auto
  private val url = "wss://localhost:8090/subscriptions"

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

  private val sa = Socket
    .collect[WebSocketFrame] { case WebSocketFrame.Text("Hello, World!") =>
      ZStream.succeed(WebSocketFrame.close(1000))
    }
    .toSocketApp
    .onOpen(Socket.succeed(WebSocketFrame.text("Hello")))
    .onClose(_ => ZIO.unit)
    .onError(thr => ZIO.die(thr))
    .withProtocol(SocketProtocol.uri(url))

  override def run(args: List[String]): URIO[ZEnv, ExitCode] =
    Client
      .socket(sa, sslOptions = sslOption)
      .flatMap(response => console.putStr(s"${response.status.asJava}"))
      .exitCode
      .provideCustomLayer(env)
}
