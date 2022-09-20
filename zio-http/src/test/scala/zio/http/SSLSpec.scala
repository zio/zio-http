package zio.http

import io.netty.handler.codec.DecoderException
import zio.http._
import zio.http.model._
import zio.test.Assertion.equalTo
import zio.test.TestAspect.{ignore, timeout}
import zio.test.{Gen, ZIOSpecDefault, assertZIO, check}
import zio.{Scope, ZIO, durationInt}

object SSLSpec extends ZIOSpecDefault {

  val sslConfig = SSLConfig.fromResource("server.crt", "server.key")
  val config    = ServerConfig.default.port(8073).ssl(sslConfig)

  val clientSSL1 = ClientSSLConfig.FromCertResource("server.crt")
  val clientSSL2 = ClientSSLConfig.FromCertResource("ss2.crt.pem")

  val payload = Gen.alphaNumericStringBounded(10000, 20000)

  val app: HttpApp[Any, Throwable] = Http.collectZIO[Request] {
    case Method.GET -> !! / "success"     =>
      ZIO.succeed(Response.ok)
    case req @ Method.POST -> !! / "text" =>
      for {
        body <- req.body.asString
      } yield Response.text(body)
  }

  override def spec = suite("SSL")(
    Server
      .serve(app)
      .as(
        List(
          test("succeed when client has the server certificate") {
            val actual = Client
              .request("https://localhost:8073/success")
              .map(_.status)
            assertZIO(actual)(equalTo(Status.Ok))
          }.provide(
            Scope.default,
            Client.live,
            ClientConfig.live(ClientConfig.empty.ssl(clientSSL1)),
          ),
          test("fail with DecoderException when client doesn't have the server certificate") {
            val actual = Client
              .request("https://localhost:8073/success")
              .catchSome { case _: DecoderException =>
                ZIO.succeed("DecoderException")
              }
            assertZIO(actual)(equalTo("DecoderException"))
          }.provide(
            Scope.default,
            Client.live,
            ClientConfig.live(ClientConfig.empty.ssl(clientSSL2)),
          ),
          test("succeed when client has default SSL") {
            val actual = Client
              .request("https://localhost:8073/success")
              .map(_.status)
            assertZIO(actual)(equalTo(Status.Ok))
          }.provide(Scope.default, Client.live, ClientConfig.live(ClientConfig.empty.ssl(ClientSSLConfig.Default))),
          test("Https Redirect when client makes http request") {
            val actual = Client
              .request("http://localhost:8073/success")
              .map(_.status)
            assertZIO(actual)(equalTo(Status.PermanentRedirect))
          }.provide(
            Scope.default,
            Client.live,
            ClientConfig.live(ClientConfig.empty.ssl(clientSSL1)),
          ),
          test("Https request with a large payload should respond with 413") {
            check(payload) { payload =>
              val actual = Client
                .request(
                  "https://localhost:8073/text",
                  Method.POST,
                  content = Body.fromString(payload),
                )
                .map(_.status)
              assertZIO(actual)(equalTo(Status.RequestEntityTooLarge))
            }
          }.provide(
            Scope.default,
            Client.live,
            ClientConfig.live(ClientConfig.empty.ssl(clientSSL1)),
          ),
        ),
      ),
  ).provideShared(
    ServerConfig.live(config),
    Server.live,
  ) @@
    timeout(5 second) @@ ignore

}
