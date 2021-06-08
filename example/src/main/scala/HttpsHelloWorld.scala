import io.netty.handler.ssl.ApplicationProtocolConfig.{
  Protocol,
  SelectedListenerFailureBehavior,
  SelectorFailureBehavior,
}
import io.netty.handler.ssl.{ApplicationProtocolConfig, ApplicationProtocolNames, SslContextBuilder, SslProvider}
import zhttp.http._
import zhttp.service.Server
import zhttp.service.server.ServerSSLHandler.ServerSSLOptions._
import zio._

import java.io.InputStream
import java.security.KeyStore
import javax.net.ssl.KeyManagerFactory

object HttpsHelloWorld extends App {

  // Create HTTP route
  val app: HttpApp[Any, Nothing] = HttpApp.collect {
    case Method.GET -> Root / "text" => Response.text("Hello World!")
    case Method.GET -> Root / "json" => Response.jsonString("""{"greetings": "Hello World!"}""")
  }

  /**
   * sslcontext can be created using SslContexBuilder. In this example a custom keystore is used.
   */
  val keyStore: KeyStore                = KeyStore.getInstance("JKS")
  val keyStorePassword: String          = "123456"
  val certPassword: String              = "123456"
  val keyStroreInputStream: InputStream = getClass.getResourceAsStream("mysslstore.jks")

  keyStore.load(keyStroreInputStream, keyStorePassword.toCharArray)
  val kmf    = KeyManagerFactory.getInstance("SunX509")
  kmf.init(keyStore, certPassword.toCharArray)
  val sslctx = SslContextBuilder
    .forServer(kmf)
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

  // Run it like any simple app
  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    Server.start(8090, app.silent, CustomSSL(sslctx)).exitCode
}
