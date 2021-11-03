package zhttp.service

import io.netty.handler.ssl.SslContextBuilder
import zhttp.http._
import zhttp.service.client.ClientSSLHandler.ClientSSLOptions
import zhttp.service.server.ServerSSLHandler.{ServerSSLOptions, ctxFromKeystore}
import zhttp.service.server.Transport

//import javax.net.ssl.SSLHandshakeException
//import zhttp.service.server._
import zio.ZIO
import zio.duration.durationInt
import zio.test.Assertion.equalTo
import zio.test.TestAspect.{flaky, timeout}
import zio.test.assertM

//import javax.net.ssl.SSLHandshakeException

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

  val app = HttpApp.collectM[Any, Throwable] { case Method.GET -> !! / "success" =>
    ZIO.succeed(Response.ok)
  }

  private val server: Server[Any,Throwable] =
    Server.port(8073) ++              // Setup port
      Server.paranoidLeakDetection ++ // Paranoid leak detection (affects performance)
      Server.app(app)   ++  // Setup the Http app
      Server.serverChannel(Transport.Auto) ++
      Server.ssl(ServerSSLOptions(serverssl))

  override def spec = suite("SSL")(
    testM("200 response") {
      server
        .make
        .use{_ =>
            val actual = Client
              .request("https://localhost:8073/success", ClientSSLOptions.CustomSSL(clientssl1))
              .map(_.status)
            assertM(actual)(equalTo(Status.OK))
        }
    }//+
//    testM("fail with SSLHandshakeException when client doesn't have the server certificate") {
//      val actual = Client
//        .request("https://localhost:8073/success", ClientSSLOptions.CustomSSL(clientssl2))
//        .map(_.status)
//        .catchSome(_.getCause match {
//          case _: SSLHandshakeException => ZIO.succeed("SSLHandshakeException")
//        })
//      assertM(actual)(equalTo("SSLHandshakeException"))
//    } @@ timeout(5 second) @@ flaky +
//    testM("succeed when client has default SSL") {
//      val actual = Client
//        .request("https://localhost:8073/success", ClientSSLOptions.DefaultSSL)
//        .map(_.status)
//      assertM(actual)(equalTo(Status.OK))
//    } +
//    testM("Https Redirect when client makes http request") {
//      val actual = Client
//        .request("http://localhost:8073/success", ClientSSLOptions.CustomSSL(clientssl1))
//        .map(_.status)
//      assertM(actual)(equalTo(Status.PERMANENT_REDIRECT))
//    }

    //    server
//      .make
//      .orDie
//      .as(
//        List(
//          testM("succeed when client has the server certificate") {
//            val actual = Client
//              .request("https://localhost:8073/success", ClientSSLOptions.CustomSSL(clientssl1))
//              .map(_.status)
//            assertM(actual)(equalTo(Status.OK))
//          } +
//            testM("fail with SSLHandshakeException when client doesn't have the server certificate") {
//              val actual = Client
//                .request("https://localhost:8073/success", ClientSSLOptions.CustomSSL(clientssl2))
//                .map(_.status)
//                .catchSome(_.getCause match {
//                  case _: SSLHandshakeException => ZIO.succeed("SSLHandshakeException")
//                })
//              assertM(actual)(equalTo("SSLHandshakeException"))
//            } @@ timeout(5 second) @@ flaky +
//            testM("succeed when client has default SSL") {
//              val actual = Client
//                .request("https://localhost:8073/success", ClientSSLOptions.DefaultSSL)
//                .map(_.status)
//              assertM(actual)(equalTo(Status.OK))
//            } +
//            testM("Https Redirect when client makes http request") {
//              val actual = Client
//                .request("http://localhost:8073/success", ClientSSLOptions.CustomSSL(clientssl1))
//                .map(_.status)
//              assertM(actual)(equalTo(Status.PERMANENT_REDIRECT))
//            },
//        ),
//      )
//      .useNow,
  ).provideCustomLayer(env) @@ flaky @@ timeout(5 second)
}
