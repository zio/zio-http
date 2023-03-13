package zio.http

import zio.durationInt
import zio.test.Assertion.{anything, equalTo, fails, isSubtype}
import zio.test.TestAspect.{ignore, timeout}
import zio.test.{ZIOSpecDefault, assertZIO}

import zio.http.model.Status
import zio.http.netty.client.NettyClientDriver

import io.netty.handler.codec.DecoderException

object ClientHttpsSpec extends ZIOSpecDefault {

  val sslConfig = ClientSSLConfig.FromTrustStoreResource(
    trustStorePath = "truststore.jks",
    trustStorePassword = "changeit",
  )

  override def spec = suite("Https Client request")(
    test("respond Ok") {
      val actual = Client.request("https://sports.api.decathlon.com/groups/water-aerobics")
      assertZIO(actual)(anything)
    },
    test("respond Ok with sslConfig") {
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
    ClientConfig.live(ClientConfig.empty.ssl(sslConfig)),
    Client.live,
    NettyClientDriver.fromConfig,
  ) @@ timeout(
    30 seconds,
  ) @@ ignore
}
