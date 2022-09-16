package zio.http.service

import io.netty.handler.codec.DecoderException
import io.netty.handler.ssl.SslContextBuilder
import zio.http.service.ClientSSLHandler.ClientSSLOptions
import zio.http.{Client, ClientConfig, Status}
import zio.test.Assertion.{anything, equalTo, fails, isSubtype}
import zio.test.TestAspect.{ignore, timeout}
import zio.test.{ZIOSpecDefault, assertZIO}
import zio.{Scope, durationInt}

import java.io.InputStream
import java.security.KeyStore
import javax.net.ssl.TrustManagerFactory

object ClientHttpsSpec extends ZIOSpecDefault {

  val trustStore: KeyStore        = KeyStore.getInstance("JKS")
  val trustStorePassword: String  = "changeit"
  val trustStoreFile: InputStream = getClass().getClassLoader().getResourceAsStream("truststore.jks")

  val trustManagerFactory: TrustManagerFactory =
    TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm)

  trustStore.load(trustStoreFile, trustStorePassword.toCharArray)
  trustManagerFactory.init(trustStore)

  val sslOption: ClientSSLOptions =
    ClientSSLOptions.CustomSSL(SslContextBuilder.forClient().trustManager(trustManagerFactory).build())
  override def spec               = suite("Https Client request")(
    test("respond Ok") {
      val actual = Client.request("https://sports.api.decathlon.com/groups/water-aerobics")
      assertZIO(actual)(anything)
    },
    test("respond Ok with sslOption") {
      val actual = Client.request("https://sports.api.decathlon.com/groups/water-aerobics")
      assertZIO(actual)(anything)
    },
    test("should respond as Bad Request") {
      val actual = Client
        .request(
          "https://www.whatissslcertificate.com/google-has-made-the-list-of-untrusted-providers-of-digital-certificates/",
        )
        .map(_.status)
      assertZIO(actual)(equalTo(Status.BadRequest))
    },
    test("should throw DecoderException for handshake failure") {
      val actual = Client
        .request(
          "https://untrusted-root.badssl.com/",
        )
        .exit
      assertZIO(actual)(fails(isSubtype[DecoderException](anything)))
    },
  ).provide(
    ClientConfig.live(ClientConfig.empty.ssl(sslOption)),
    Client.live,
    ConnectionPool.disabled,
    Scope.default,
  ) @@ timeout(
    30 seconds,
  ) @@ ignore
}
