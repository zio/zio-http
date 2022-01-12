package zhttp.service

import io.netty.handler.codec.DecoderException
import io.netty.handler.ssl.SslContextBuilder
import zhttp.http.Status
import zhttp.service.client.ClientSSLHandler.ClientSSLOptions
import zio.durationInt
import zio.test.Assertion.{anything, equalTo, fails, isSubtype}
import zio.test.TestAspect.{ignore, timeout}
import zio.test.{DefaultRunnableSpec, assertM}

import java.io._
import java.security.KeyStore
import javax.net.ssl.TrustManagerFactory

object ClientHttpsSpec extends DefaultRunnableSpec {

  val env                         = ChannelFactory.auto ++ EventLoopGroup.auto()
  val trustStore: KeyStore        = KeyStore.getInstance("JKS")
  val trustStorePassword: String  = "changeit"
  val trustStoreFile: InputStream = getClass().getClassLoader().getResourceAsStream("truststore.jks")

  val trustManagerFactory: TrustManagerFactory =
    TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm)

  trustStore.load(trustStoreFile, trustStorePassword.toCharArray)
  trustManagerFactory.init(trustStore)

  val sslOption: ClientSSLOptions =
    ClientSSLOptions.CustomSSL(SslContextBuilder.forClient().trustManager(trustManagerFactory).build())

  override def spec = suite("Https Client request") {
    test("should throw DecoderException for handshake failure") {
      val actual = Client
        .request(
          "https://untrusted-root.badssl.com/",
          sslOption,
        )
        .exit
      assertM(actual)(fails(isSubtype[DecoderException](anything)))
    } +
      test("respond Ok") {
        val actual = Client.request("https://sports.api.decathlon.com/groups/water-aerobics")
        assertM(actual)(anything)
      } +
      test("respond Ok with sslOption") {
        val actual = Client.request("https://sports.api.decathlon.com/groups/water-aerobics", sslOption)
        assertM(actual)(anything)
      } +
      test("should respond as Bad Request") {
        val actual = Client
          .request(
            "https://www.whatissslcertificate.com/google-has-made-the-list-of-untrusted-providers-of-digital-certificates/",
            sslOption,
          )
          .map(_.status)
        assertM(actual)(equalTo(Status.BAD_REQUEST))
      }
  }.provideCustomLayerShared(env) @@ timeout(30 seconds) @@ ignore
}
