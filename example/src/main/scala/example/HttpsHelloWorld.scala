package example

import zhttp.http.{HttpApp, Method, Response, _}
import zhttp.service.Server
import zhttp.service.server.Auto
import zhttp.service.server.ServerSSLHandler.{ServerSSLOptions, ctxFromKeystore}
import zio.{App, ExitCode, URIO}

object HttpsHelloWorld extends App {

  // Create HTTP route
  val app: HttpApp[Any, Nothing] = HttpApp.collect {
    case Method.GET -> !! / "text" => Response.text("Hello World!")
    case Method.GET -> !! / "json" => Response.jsonString("""{"greetings": "Hello World!"}""")
  }

  /**
   * sslcontext can be created using SslContexBuilder. In this example an inbuilt API using keystore is used. For
   * testing this example using curl, setup the certificate named "localhost.cert" from resources for the OS.
   * Alternatively you can create the keystore and certificate using the following link
   * https://medium.com/@maanadev/netty-with-https-tls-9bf699e07f01
   */
  val sslctx = ctxFromKeystore(getClass.getResourceAsStream("mysslstore.jks"), "password", "password")

  private val server =
    Server.port(8090) ++
      Server.app(app) ++
      Server.ssl(ServerSSLOptions(sslctx)) ++
      Server.transport(Auto) ++
      Server.threads(1)

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = {
    server.make.useForever.exitCode
  }
}
