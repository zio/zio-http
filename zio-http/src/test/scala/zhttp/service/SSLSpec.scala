package zhttp.service

import io.netty.handler.ssl.ApplicationProtocolConfig.{
  Protocol,
  SelectedListenerFailureBehavior,
  SelectorFailureBehavior,
}
import io.netty.handler.ssl.util.SelfSignedCertificate
import io.netty.handler.ssl.{ApplicationProtocolConfig, ApplicationProtocolNames, SslContextBuilder, SslProvider}
import zhttp.http._
import zhttp.service.client.ClientSSLHandler.ClientSSLOptions
import zhttp.service.server.ServerSSLHandler.ServerSSLOptions._
import zhttp.service.server._
import zio.ZIO
import zio.duration.durationInt
import zio.test.Assertion.{equalTo, isNone}
import zio.test.assertM
import zio.test.environment.TestClock

import javax.net.ssl.SSLHandshakeException

object SSLSpec extends HttpRunnableSpec(8080) {
  val env = EventLoopGroup.auto() ++ ChannelFactory.auto ++ ServerChannelFactory.auto

  val ssc1       = new SelfSignedCertificate
  val serverssl  = SslContextBuilder
    .forServer(ssc1.certificate(), ssc1.privateKey())
    .sslProvider(SslProvider.JDK)
    .applicationProtocolConfig(
      new ApplicationProtocolConfig(
        Protocol.ALPN,
        SelectorFailureBehavior.NO_ADVERTISE,
        SelectedListenerFailureBehavior.ACCEPT,
        ApplicationProtocolNames.HTTP_1_1,
      ),
    )
    .build()
  val ssc2       = new SelfSignedCertificate()
  val clientssl1 = SslContextBuilder.forClient().trustManager(ssc1.cert()).build()
  val clientssl2 = SslContextBuilder.forClient().trustManager(ssc2.cert()).build()

  val app = serve(
    HttpApp.collectM[Any, Nothing] { case Method.GET -> Root / "success" =>
      ZIO.succeed(Response.ok)
    },
    CustomSSL(serverssl),
  )

  override def spec = suiteM("SSL")(
    app
      .as(
        List(
          testM("succeed when client has the server certificate") {
            val actual = Client
              .request("https://localhost:8080/success", ClientSSLOptions.CustomSSL(clientssl1))
              .map(_.status)
            assertM(actual)(equalTo(Status.OK))
          },
          testM("fail with SSLHandshakeException when client doesn't have the server certificate") {
            val actual = Client
              .request("https://localhost:8080/success", ClientSSLOptions.CustomSSL(clientssl2))
              .map(_.status)
              .catchSome(_.getCause match {
                case _: SSLHandshakeException => ZIO.succeed("SSLHandshakeException")
              })
            assertM(actual)(equalTo("SSLHandshakeException"))
          },
          testM("empty response when client makes http request") {
            val actual = for {
              fiber <- Client
                .request("http://localhost:8080/success", ClientSSLOptions.CustomSSL(clientssl1))
                .timeout(3 seconds)
                .fork
              _     <- TestClock.adjust(3 seconds)
              res   <- fiber.join
            } yield res
            assertM(actual)(isNone)
          },
        ),
      )
      .useNow,
  ).provideCustomLayer(env)
}
