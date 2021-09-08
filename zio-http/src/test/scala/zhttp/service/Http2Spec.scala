package zhttp.service

import io.netty.handler.ssl.util.SelfSignedCertificate
import io.netty.handler.ssl.{NotSslRecordException, SslContextBuilder, SslProvider}
import zhttp.http._
import zhttp.service.client.ClientSSLHandler.ClientSSLOptions
import zhttp.service.server.ServerSSLHandler.{SSLHttpBehaviour, ServerSSLOptions}
import zhttp.service.server._
import zio.ZIO
import zio.test.Assertion.equalTo
import zio.test.assertM

object Http2Spec extends HttpRunnableSpec(8023) {
  val env = EventLoopGroup.auto() ++ ChannelFactory.auto ++ ServerChannelFactory.auto

  val ssc1      = new SelfSignedCertificate
  val serverssl = SslContextBuilder
    .forServer(ssc1.certificate(), ssc1.privateKey())
    .sslProvider(SslProvider.JDK)

  val clientssl1 = SslContextBuilder.forClient().trustManager(ssc1.cert())

  val app = HttpApp.collectM[Any, Nothing] { case Method.GET -> !! / "success" =>
    ZIO.succeed(Response.ok)
  }

  override def spec = suiteM("Http2")(
    for {
      a <- Server
        .make(
          Server.app(app) ++ Server.port(8023) ++ Server.ssl(
            ServerSSLOptions(serverssl, SSLHttpBehaviour.Accept),
          ) ++ Server.http2,
        )
        .orDie
        .as(
          List(
            testM(
              "secure http2 request should succeed on a secure server which has http2 support and accept insecure requests",
            ) {
              val actual = Client
                .request("https://localhost:8023/success", ClientSSLOptions.CustomSSL(clientssl1), true)
                .map(_.status)
              assertM(actual)(equalTo(Status.OK))
            },
            testM(
              "insecure http2 request should succeed on a secure server which have http2 support and accept insecure requests",
            ) {
              val actual = Client
                .request("http://localhost:8023/success", ClientSSLOptions.CustomSSL(clientssl1), true)
                .map(_.status)
              assertM(actual)(equalTo(Status.OK))
            },
          ),
        )
        .useNow

      b <- Server
        .make(Server.app(app) ++ Server.port(8024) ++ Server.ssl(ServerSSLOptions(serverssl, SSLHttpBehaviour.Accept)))
        .orDie
        .as(
          List(
            testM(
              "secure http2 request should succeed on a secure server which doesn't have http2 support and accept insecure requests",
            ) {
              val actual = Client
                .request("https://localhost:8024/success", ClientSSLOptions.CustomSSL(clientssl1), true)
                .map(_.status)
              assertM(actual)(equalTo(Status.OK))
            },
            testM(
              "insecure http2 request should succeed on a secure server which doesn't have http2 support and accept insecure requests",
            ) {
              val actual = Client
                .request("http://localhost:8024/success", ClientSSLOptions.CustomSSL(clientssl1), true)
                .map(_.status)
              assertM(actual)(equalTo(Status.OK))
            },
          ),
        )
        .useNow
      c <- Server
        .make(
          Server.app(app) ++ Server.port(8025) ++ Server.ssl(
            ServerSSLOptions(serverssl, SSLHttpBehaviour.Redirect),
          ) ++ Server.http2,
        )
        .orDie
        .as(
          List(
            testM(
              "insecure http2 request should be redirected on a secure server which have http2 support and redirects insecure requests",
            ) {
              val actual = Client
                .request("http://localhost:8025/success", ClientSSLOptions.CustomSSL(clientssl1), true)
                .map(_.status)
              assertM(actual)(equalTo(Status.PERMANENT_REDIRECT))
            },
          ),
        )
        .useNow

      d <- Server
        .make(
          Server.app(app) ++ Server.port(8026) ++ Server.ssl(ServerSSLOptions(serverssl, SSLHttpBehaviour.Redirect)),
        )
        .orDie
        .as(
          List(
            testM(
              "insecure http2 request should be redirected on a secure server which doesn't have http2 support and redirects insecure requests",
            ) {
              val actual = Client
                .request("http://localhost:8026/success", ClientSSLOptions.CustomSSL(clientssl1), true)
                .map(_.status)
              assertM(actual)(equalTo(Status.PERMANENT_REDIRECT))
            },
          ),
        )
        .useNow
      e <- Server
        .make(
          Server.app(app) ++ Server.port(8027) ++ Server.ssl(
            ServerSSLOptions(serverssl, SSLHttpBehaviour.Fail),
          ) ++ Server.http2,
        )
        .orDie
        .as(
          List(
            testM(
              "insecure http2 request should be failed on a secure server which have http2 support and fails insecure requests",
            ) {
              val actual = Client
                .request("http://localhost:8027/success", ClientSSLOptions.CustomSSL(clientssl1), true)
                .map(_.status)
              assertM(actual)(equalTo(Status.NOT_ACCEPTABLE))
            },
          ),
        )
        .useNow

      f <- Server
        .make(Server.app(app) ++ Server.port(8028) ++ Server.ssl(ServerSSLOptions(serverssl, SSLHttpBehaviour.Fail)))
        .orDie
        .as(
          List(
            testM(
              "insecure http2 request should be failed on a secure server which doesn't have http2 support and fails insecure requests",
            ) {
              val actual = Client
                .request("http://localhost:8028/success", ClientSSLOptions.CustomSSL(clientssl1), true)
                .map(_.status)
              assertM(actual)(equalTo(Status.NOT_ACCEPTABLE))
            },
          ),
        )
        .useNow
      g <- Server
        .make(Server.app(app) ++ Server.port(8029) ++ Server.http2)
        .orDie
        .as(
          List(
            testM("secure http2 request should fail on an  insecure server which has http2 support") {
              val actual = Client
                .request("https://localhost:8029/success", ClientSSLOptions.CustomSSL(clientssl1), true)
                .map(_.status)
                .catchSome(_.getCause match {
                  case _: NotSslRecordException => ZIO.succeed("SSLHandshakeException")
                })
              assertM(actual)(equalTo("SSLHandshakeException"))
            },
            testM("insecure http2 request should succeed on an insecure server which have http2 support") {
              val actual = Client
                .request("http://localhost:8029/success", ClientSSLOptions.CustomSSL(clientssl1), true)
                .map(_.status)
              assertM(actual)(equalTo(Status.OK))
            },
          ),
        )
        .useNow

      h <- Server
        .make(Server.app(app) ++ Server.port(8030))
        .orDie
        .as(
          List(
            testM("secure http2 request fail succeed on an insecure server which doesn't have http2 support") {
              val actual = Client
                .request("https://localhost:8030/success", ClientSSLOptions.CustomSSL(clientssl1), true)
                .map(_.status)
                .catchSome(_.getCause match {
                  case _: NotSslRecordException => ZIO.succeed("SSLHandshakeException")
                })
              assertM(actual)(equalTo("SSLHandshakeException"))
            },
            testM("insecure http2 request should succeed on an insecure server which doesn't have http2 support") {
              val actual = Client
                .request("http://localhost:8030/success", ClientSSLOptions.CustomSSL(clientssl1), true)
                .map(_.status)
              assertM(actual)(equalTo(Status.OK))
            },
          ),
        )
        .useNow
    } yield a ++ b ++ c ++ d ++ e ++ f ++ g ++ h,
  ).provideCustomLayer(env)
}
