package zhttp.service

import io.netty.handler.codec.DecoderException
import io.netty.handler.ssl.SslContextBuilder
import zhttp.service.client.ClientSSLHandler.ClientSSLOptions
import zio.test.Assertion.{anything, fails, isSubtype}
import zio.test.assertM

import java.io._
import java.security.KeyStore
import javax.net.ssl.TrustManagerFactory

object ClientHttpsSpec extends HttpRunnableSpec(8082) {
  val env                                      = ChannelFactory.auto ++ EventLoopGroup.auto()
  val trustStore: KeyStore                     = KeyStore.getInstance("JKS")
  val trustStoreFile: InputStream              = getClass.getResourceAsStream("truststore.jks")
  val trustStorePassword: String               = "changeit"
  val trustManagerFactory: TrustManagerFactory =
    TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm)

  trustStore.load(trustStoreFile, trustStorePassword.toCharArray)
  trustManagerFactory.init(trustStore)

  val sslOption: ClientSSLOptions =
    ClientSSLOptions.CustomSSL(SslContextBuilder.forClient().trustManager(trustManagerFactory).build())
  override def spec               = suite("Https Client request")(
    testM("respond Ok") {
      val actual = Client.request("https://api.github.com/users/zio/repos")
      assertM(actual)(anything)
    },
    testM("respond Ok with sslOption") {
      val actual = Client.request("https://api.github.com/users/zio/repos", sslOption)
      assertM(actual)(anything)
    },
    testM("should throw DecoderException for handshake failure") {
      val actual = Client
        .request(
          "https://www.whatissslcertificate.com/google-has-made-the-list-of-untrusted-providers-of-digital-certificates/",
          sslOption,
        )
        .run
      assertM(actual)(fails(isSubtype[DecoderException](anything)))
    },
  ).provideCustomLayer(env)
}
