package example

import io.netty.handler.ssl.SslContextBuilder
import zhttp.http.Headers
import zhttp.service.client.ClientSSLHandler.ClientSSLOptions
import zhttp.service.{ChannelFactory, EventLoopGroup}
import zio.http.service.Client
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
