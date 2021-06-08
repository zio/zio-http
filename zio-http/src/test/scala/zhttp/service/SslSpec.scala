package zhttp.service

import io.netty.handler.ssl.{ApplicationProtocolConfig, ApplicationProtocolNames, NotSslRecordException, SslContextBuilder, SslProvider}
import io.netty.handler.ssl.ApplicationProtocolConfig.{Protocol, SelectedListenerFailureBehavior, SelectorFailureBehavior}
import io.netty.handler.ssl.util.SelfSignedCertificate
import javax.net.ssl.SSLHandshakeException
import zhttp.http._
import zhttp.service.client.ClientSSLHandler.SslClientOptions
import zhttp.service.server.ServerSslHandler.SslServerOptions._
import zhttp.service.server._
import zio.ZIO
import zio.test.Assertion.equalTo
import zio.test.assertM

object SslSpec extends HttpRunnableSpec(8080) {
  val env = EventLoopGroup.auto() ++ ChannelFactory.auto ++ ServerChannelFactory.auto

  val ssc1 = new SelfSignedCertificate
  val serverssl= SslContextBuilder
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
val ssc2 = new SelfSignedCertificate()
  val clientssl1= SslContextBuilder.forClient().trustManager(ssc1.cert()).build()
  val clientssl2= SslContextBuilder.forClient().trustManager(ssc2.cert()).build()

  val app = serve(
    HttpApp.collectM {
      case Method.GET -> Root / "success" => ZIO.succeed(Response.ok)
    },
    CustomSsl(serverssl),
  )

  override def spec = suiteM("SSL")(
    app
      .as(
        List(
          testM("succeed when client has the server certificate") {
            val actual = Client.request("https://localhost:8080/success",SslClientOptions.CustomSslClient(clientssl1)).map(_.status)
            assertM(actual)(equalTo(Status.OK))
          },
          testM("fail with SSLHandshakeException when client doesn't have the server certificate") {
            val actual = Client.request("https://localhost:8080/success",SslClientOptions.CustomSslClient(clientssl2)).map(_.status).catchSome(_.getCause match {
              case  _:SSLHandshakeException=>ZIO.succeed("SSLHandshakeException")
            })
            assertM(actual)(equalTo("SSLHandshakeException"))
          },
          testM("fail with NotSslRecordException when client doesn't have the server certificate") {
            val actual = Client.request("http://localhost:8080/success",SslClientOptions.CustomSslClient(clientssl1)).map(_.status).catchSome(_.getCause match {
              case  _:NotSslRecordException=>ZIO.succeed("NotSslRecordException")
            })
            assertM(actual)(equalTo("NotSslRecordException"))
          },
        ),
      )
      .useNow,
  ).provideCustomLayer(env)
}