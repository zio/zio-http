package zhttp.service

import io.netty.handler.codec.DecoderException
import io.netty.handler.ssl.SslContextBuilder
import zhttp.http.Status
import zhttp.service.client.ClientSSLHandler.ClientSSLOptions
import zio.test.Assertion.{anything, equalTo, fails, isSubtype}
import zio.test.TestAspect.flaky
import zio.test.assertM

import java.io._
import java.security.KeyStore
import javax.net.ssl.TrustManagerFactory

object ClientHttpsSpec extends HttpRunnableSpec(8082) {

  val env                         = ChannelFactory.auto ++ EventLoopGroup.auto()
  val trustStore: KeyStore        = KeyStore.getInstance("JKS")
  val trustStorePassword: String  = "changeit"
  val trustStoreFile: InputStream = getClass().getClassLoader().getResourceAsStream("truststore.jks")

  val trustManagerFactory: TrustManagerFactory =
    TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm)

  trustStore.load(trustStoreFile, trustStorePassword.toCharArray)
  trustManagerFactory.init(trustStore)

  val sslOption: ClientSSLOptions =
    ClientSSLOptions.CustomSSL(SslContextBuilder.forClient().trustManager(trustManagerFactory))
  override def spec               = suite("Https Client request")(
    testM("respond Ok") {
      val actual = Client.request("https://sports.api.decathlon.com/groups/water-aerobics", false)
      assertM(actual)(anything)
    } +
      testM("respond Ok with sslOption") {
        val actual = Client.request("https://sports.api.decathlon.com/groups/water-aerobics", sslOption, false)
        assertM(actual)(anything)
      } +
      testM("should respond as Bad Request") {
        val actual = Client
          .request(
            "https://www.whatissslcertificate.com/google-has-made-the-list-of-untrusted-providers-of-digital-certificates/",
            sslOption,
            false,
          )
          .map(_.status)
        assertM(actual)(equalTo(Status.BAD_REQUEST))
      } +
      testM("should throw DecoderException for handshake failure") {
        val actual = Client
          .request(
            "https://untrusted-root.badssl.com/",
            sslOption,
            false,
          )
          .run
        assertM(actual)(fails(isSubtype[DecoderException](anything)))
      } @@ flaky,
  ).provideCustomLayer(env)
}
