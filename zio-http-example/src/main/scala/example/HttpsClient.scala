package example

import io.netty.handler.ssl.SslContextBuilder
import zio._
import zio.http.model.Headers
import zio.http.netty.client.ClientSSLHandler.ClientSSLOptions
import zio.http.{Client, ClientConfig}

import java.io.InputStream
import java.security.KeyStore
import javax.net.ssl.TrustManagerFactory

object HttpsClient extends ZIOAppDefault {
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
    res  <- Client.request(url, headers = headers)
    data <- res.body.asString
    _    <- Console.printLine(data)
  } yield ()

  val clientConfig = ClientConfig.empty.ssl(sslOption)
  val run          = program.provide(ClientConfig.live(clientConfig), Client.live, Scope.default)

}
