
import java.io.InputStream
import java.security.KeyStore

import io.netty.handler.ssl.{ApplicationProtocolConfig, ApplicationProtocolNames, SslContextBuilder, SslProvider}
import io.netty.handler.ssl.ApplicationProtocolConfig.{Protocol, SelectedListenerFailureBehavior, SelectorFailureBehavior}
import javax.net.ssl.KeyManagerFactory
import zhttp.http._
import zhttp.service.Server
import zhttp.service.server.ServerSslHandler.SslServerOptions._
import zio._

object HttpsHelloWorld extends App {

  // Create HTTP route
  val app: HttpApp[Any, Nothing] = HttpApp.collect {
    case Method.GET -> Root / "text" => Response.text("Hello World!")
    case Method.GET -> Root / "json" => Response.jsonString("""{"greetings": "Hello World!"}""")
  }

  /**
   * sslcontext can be created using SslContexBuilder. In this example a custom keystore is used.
   */
  val keyStore: KeyStore = KeyStore.getInstance("JKS")
  val keyStorePassword: String = "123456"
  val certPassword: String = "123456"
  val keyStroreInputStream: InputStream= getClass.getResourceAsStream("mysslstore.jks")

  keyStore.load(keyStroreInputStream, keyStorePassword.toCharArray)
  val kmf = KeyManagerFactory.getInstance("SunX509")
  kmf.init(keyStore, certPassword.toCharArray)
  val sslctx=SslContextBuilder
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
    Server.start(8090, app.silent,CustomSsl(sslctx)).exitCode
}
