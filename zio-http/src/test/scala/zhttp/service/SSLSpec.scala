package zhttp.service

import io.netty.handler.ssl.util.SelfSignedCertificate
import io.netty.handler.ssl.{SslContextBuilder, SslProvider}
import zhttp.http._
import zhttp.service.client.ClientSSLHandler.ClientSSLOptions
import zhttp.service.server.ServerSSLHandler.ServerSSLOptions
import zhttp.service.server._
import zio.ZIO
import zio.duration.durationInt
import zio.test.Assertion.equalTo
import zio.test.TestAspect.{flaky, timeout}
import zio.test.assertM

import javax.net.ssl.SSLHandshakeException

object SSLSpec extends HttpRunnableSpec(8073) {
  val env = EventLoopGroup.auto() ++ ChannelFactory.auto ++ ServerChannelFactory.auto

  val ssc1      = new SelfSignedCertificate
  val serverssl = SslContextBuilder
    .forServer(ssc1.certificate(), ssc1.privateKey())
    .sslProvider(SslProvider.JDK)

  val ssc2       = new SelfSignedCertificate()
  val clientssl1 = SslContextBuilder.forClient().trustManager(ssc1.cert()).build()
  val clientssl2 = SslContextBuilder.forClient().trustManager(ssc2.cert()).build()

  val app = HttpApp.collectM[Any, Nothing] { case Method.GET -> !! / "success" =>
    ZIO.succeed(Response.ok)
  }

  override def spec = suiteM("SSL")(
    Server
      .make(Server.app(app) ++ Server.port(8073) ++ Server.ssl(ServerSSLOptions(serverssl)))
      .orDie
      .as(
        List(
          testM("succeed when client has the server certificate") {
            val actual = Client
              .request("https://localhost:8073/success", ClientSSLOptions.CustomSSL(clientssl1))
              .map(_.status)
            assertM(actual)(equalTo(Status.OK))
          },
          testM("fail with SSLHandshakeException when client doesn't have the server certificate") {
            val actual = Client
              .request("https://localhost:8073/success", ClientSSLOptions.CustomSSL(clientssl2))
              .map(_.status)
              .catchSome(_.getCause match {
                case _: SSLHandshakeException => ZIO.succeed("SSLHandshakeException")
              })
            assertM(actual)(equalTo("SSLHandshakeException"))
          } @@ timeout(5 second) @@ flaky,
          testM("succeed when client has default SSL") {
            val actual = Client
              .request("https://localhost:8073/success", ClientSSLOptions.DefaultSSL)
              .map(_.status)
            assertM(actual)(equalTo(Status.OK))
          },
          testM("Https Redirect when client makes http request") {
            val actual = Client
              .request("http://localhost:8073/success", ClientSSLOptions.CustomSSL(clientssl1))
              .map(_.status)
            assertM(actual)(equalTo(Status.PERMANENT_REDIRECT))
          },
        ),
      )
      .useNow,
  ).provideCustomLayer(env)
}
