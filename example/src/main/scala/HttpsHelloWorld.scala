import zhttp.http._
import zhttp.service.Server
import zhttp.service.server.ServerSSLHandler.ServerSSLOptions._
import zio._

import java.io.InputStream

object HttpsHelloWorld extends App {

  // Create HTTP route
  val app: HttpApp[Any, Nothing] = HttpApp.collect {
    case Method.GET -> Root / "text" => Response.text("Hello World!")
    case Method.GET -> Root / "json" => Response.jsonString("""{"greetings": "Hello World!"}""")
  }

  /**
   * custom sslcontext can be created using SslContexBuilder. In this example an inbuilt API using keystore is used
   */
  val keyStroreInputStream: InputStream                          = getClass.getResourceAsStream("mysslstore.jks")
  // Run it like any simple app
  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    Server.start(8090, app.silent, SSLFromKeystore(keyStroreInputStream, "123456", "123456")).exitCode
}
