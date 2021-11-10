package zhttp.service

import io.netty.handler.ssl.SslContextBuilder
import zhttp.http._
import zhttp.service.client.ClientSSLHandler.ClientSSLOptions
import zhttp.service.server.ServerSSLHandler.ctxFromKeystore
import zio.ZIO
import zio.duration.durationInt
import zio.test.Assertion.equalTo
import zio.test.TestAspect.{flaky, timeout}
import zio.test.assertM

import javax.net.ssl.SSLHandshakeException

object SSLSpec extends HttpRunnableSpec(8073) {
  val env = EventLoopGroup.auto() ++ ChannelFactory.auto // ++ ServerChannelFactory.auto

  /**
   * a custom keystore and a certificate from it
   */
  val keystore   = getClass().getClassLoader().getResourceAsStream("keystore.jks")
  val servercert = getClass().getClassLoader().getResourceAsStream("cert.crt.pem")

  /**
   * a second certificate
   */
  val ssc2 = getClass().getClassLoader().getResourceAsStream("ss2.crt.pem")

  val serverssl  = ctxFromKeystore(keystore, "password", "password")
  val clientssl1 = SslContextBuilder.forClient().trustManager(servercert).build()
  val clientssl2 = SslContextBuilder.forClient().trustManager(ssc2).build()

  val app = serveWithPortWithSSL(
    HttpApp.collectM[Any, Nothing] { case Method.GET -> !! / "success" =>
      ZIO.succeed(Response.ok)
    },
    serverssl,
  ) _

  override def spec = suite("SSL")(
    testM("succeed when client has the server certificate") {
      val p = 28073
      app(p).use { _ =>
        val actual = Client
          .request(s"https://localhost:$p/success", ClientSSLOptions.CustomSSL(clientssl1))
          .map(_.status)
        assertM(actual)(equalTo(Status.OK))
      }
    },
    testM("fail with SSLHandshakeException when client doesn't have the server certificate") {
      val p = 28074
      app(p).use { _ =>
        val actual = Client
          .request(s"https://localhost:$p/success", ClientSSLOptions.CustomSSL(clientssl2))
          .map(_.status)
          .catchSome(_.getCause match {
            case _: SSLHandshakeException => ZIO.succeed("SSLHandshakeException")
          })
        assertM(actual)(equalTo("SSLHandshakeException"))
      }
    } @@ timeout(5 second) @@ flaky,
    testM("succeed when client has default SSL") {
      val p = 28075
      app(p).use { _ =>
        val actual = Client
          .request(s"https://localhost:$p/success", ClientSSLOptions.DefaultSSL)
          .map(_.status)
        assertM(actual)(equalTo(Status.OK))
      }
    },
    testM("Https Redirect when client makes http request") {
      val p = 28076
      app(p).use { _ =>
        val actual = Client
          .request(s"http://localhost:$p/success", ClientSSLOptions.CustomSSL(clientssl1))
          .map(_.status)
        assertM(actual)(equalTo(Status.PERMANENT_REDIRECT))
      }
    },
  ).provideCustomLayer(env) @@ flaky @@ timeout(5 second)
}
