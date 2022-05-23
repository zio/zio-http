package zhttp.service

import io.netty.handler.codec.DecoderException
import io.netty.handler.ssl.SslContextBuilder
import zhttp.http._
import zhttp.service.client.ClientSSLHandler.ClientSSLOptions
import zhttp.service.server.ServerSSLHandler.{ServerSSLOptions, ctxFromCert}
import zhttp.service.server._
import zio.ZIO
import zio.duration.durationInt
import zio.test.Assertion.equalTo
import zio.test.TestAspect.{ignore, timeout}
import zio.test.{DefaultRunnableSpec, Gen, assertM, checkM}

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

  val payload = Gen.alphaNumericStringBounded(10000, 20000)

  val app: HttpApp[Any, Throwable] = Http.collectZIO[Request] {
    case Method.GET -> !! / "success"     =>
      ZIO.succeed(Response.ok)
    case req @ Method.POST -> !! / "text" =>
      for {
        body <- req.bodyAsString
      } yield Response.text(body)
  }

  override def spec = suiteM("SSL")(
    Server
      .make(Server.app(app) ++ Server.port(8073) ++ Server.ssl(ServerSSLOptions(serverSSL)))
      .orDie
      .as(
        List(
          testM("succeed when client has the server certificate") {
            val actual = Client
              .request("https://localhost:8073/success", ssl = ClientSSLOptions.CustomSSL(clientSSL1))
              .map(_.status)
            assertM(actual)(equalTo(Status.Ok))
          } +
            testM("fail with DecoderException when client doesn't have the server certificate") {
              val actual = Client
                .request("https://localhost:8073/success", ssl = ClientSSLOptions.CustomSSL(clientSSL2))
                .catchSome { case _: DecoderException =>
                  ZIO.succeed("DecoderException")
                }
              assertM(actual)(equalTo("DecoderException"))
            } +
            testM("succeed when client has default SSL") {
              val actual = Client
                .request("https://localhost:8073/success", ssl = ClientSSLOptions.DefaultSSL)
                .map(_.status)
              assertM(actual)(equalTo(Status.Ok))
            } +
            testM("Https Redirect when client makes http request") {
              val actual = Client
                .request("http://localhost:8073/success", ssl = ClientSSLOptions.CustomSSL(clientSSL1))
                .map(_.status)
              assertM(actual)(equalTo(Status.PermanentRedirect))
            } +
            testM("Https request with a large payload should respond with 413") {
              checkM(payload) { payload =>
                val actual = Client
                  .request(
                    "https://localhost:8073/text",
                    Method.POST,
                    ssl = ClientSSLOptions.CustomSSL(clientSSL1),
                    content = HttpData.fromString(payload),
                  )
                  .map(_.status)
                assertM(actual)(equalTo(Status.RequestEntityTooLarge))
              }
            },
        ),
      )
      .useNow,
  ).provideCustomLayer(env) @@ timeout(5 second) @@ ignore
}
