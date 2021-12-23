package zhttp.service

import io.netty.handler.codec.DecoderException
import io.netty.handler.ssl.SslContextBuilder
import zhttp.http._
import zhttp.service.client.ClientSSLHandler.ClientSSLOptions
import zhttp.service.server.ServerSSLHandler.{ServerSSLOptions, ctxFromCert}
import zhttp.service.server._
import zio._
import zio.test.Assertion.equalTo
import zio.test.TestAspect.{nonFlaky, sequential, timeout}
import zio.test.{DefaultRunnableSpec, assertM}

object SSLSpec extends DefaultRunnableSpec {
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

  val serverLayer: ZLayer[EventLoopGroup with ServerChannelFactory, Nothing, Unit] =
    Server
      .make(Server.app(app) ++ Server.port(8073) ++ Server.ssl(ServerSSLOptions(serverSSL)))
      .orDie
      .toLayer

  override def spec = suite("SSL")(
    test("Https Redirect when client makes http request") {
      val actual = Client
        .request("http://localhost:8073/success", ClientSSLOptions.CustomSSL(clientSSL1))
        .map(_.status)
      assertM(actual)(equalTo(Status.PERMANENT_REDIRECT))
    } @@ timeout(5.seconds) @@ nonFlaky +
      test("succeed when client has the server certificate") {
        val actual = Client
          .request("https://localhost:8073/success", ClientSSLOptions.CustomSSL(clientSSL1))
          .map(_.status)
        assertM(actual)(equalTo(Status.OK))
      } +
      test("fail with DecoderException when client doesn't have the server certificate") {
        val actual = Client
          .request("https://localhost:8073/success", ClientSSLOptions.CustomSSL(clientSSL2))
          .catchSome { case _: DecoderException =>
            ZIO.succeed("DecoderException")
          }
        assertM(actual)(equalTo("DecoderException"))
      } @@ timeout(5.seconds) +
      test("succeed when client has default SSL") {
        val actual = Client
          .request("https://localhost:8073/success", ClientSSLOptions.DefaultSSL)
          .map(_.status)
        assertM(actual)(equalTo(Status.OK))
      },
  ).provideCustomLayer(env >+> serverLayer) @@ sequential @@ timeout(120 seconds)
}
