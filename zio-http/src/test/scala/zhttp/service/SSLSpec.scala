package zhttp.service

import io.netty.handler.codec.DecoderException
import io.netty.handler.ssl.SslContextBuilder
import zhttp.http._
import zhttp.internal.HttpRunnableSpec
import zhttp.service.client.ClientSSLHandler.ClientSSLOptions
import zhttp.service.server.ServerSSLHandler.{ServerSSLOptions, ctxFromCert}
import zhttp.service.server._
import zio.ZIO
import zio.duration.durationInt
import zio.test.Assertion.equalTo
import zio.test.TestAspect.{nonFlaky, sequential, timeout}
import zio.test.assertM

object SSLSpec extends HttpRunnableSpec(8073) {
  val env = EventLoopGroup.auto() ++ ChannelFactory.auto ++ ServerChannelFactory.auto

  val serverSSL  = ctxFromCert(
    getClass().getClassLoader().getResourceAsStream("server.crt"),
    getClass().getClassLoader().getResourceAsStream("server.key"),
  )
  val clientSSL1 =
    SslContextBuilder.forClient().trustManager(getClass().getClassLoader().getResourceAsStream("server.crt")).build()
  val clientSSL2 =
    SslContextBuilder.forClient().trustManager(getClass().getClassLoader().getResourceAsStream("ss2.crt.pem")).build()

  val app: HttpApp[Any, Nothing] = Http.collectM[Request] { case Method.GET -> !! / "success" =>
    ZIO.succeed(Response.ok)
  }

  override def spec = suiteM("SSL")(
    Server
      .make(Server.app(app) ++ Server.port(8073) ++ Server.ssl(ServerSSLOptions(serverSSL)))
      .orDie
      .as(
        List(
          testM("succeed when client has the server certificate") {
            val actual = Client
              .request("https://localhost:8073/success", ClientSSLOptions.CustomSSL(clientSSL1))
              .map(_.status)
            assertM(actual)(equalTo(Status.OK))
          } @@ nonFlaky +
            testM("fail with DecoderException when client doesn't have the server certificate") {
              val actual = Client
                .request("https://localhost:8073/success", ClientSSLOptions.CustomSSL(clientSSL2))
                .catchSome(_ match {
                  case _: DecoderException => ZIO.succeed("DecoderException")
                })
              assertM(actual)(equalTo("DecoderException"))
            } @@ nonFlaky +
            testM("succeed when client has default SSL") {
              val actual = Client
                .request("https://localhost:8073/success", ClientSSLOptions.DefaultSSL)
                .map(_.status)
              assertM(actual)(equalTo(Status.OK))
            } @@ nonFlaky +
            testM("Https Redirect when client makes http request") {
              val actual = Client
                .request("http://localhost:8073/success", ClientSSLOptions.CustomSSL(clientSSL1))
                .map(_.status)
              assertM(actual)(equalTo(Status.PERMANENT_REDIRECT))
            } @@ nonFlaky,
        ),
      )
      .useNow,
  ).provideCustomLayer(env) @@ timeout(30 second) @@ sequential
}
