# HTTPS Server
```scala
import zhttp.http._
import zhttp.service.server.ServerChannelFactory
import zhttp.service.server.ServerSSLHandler.{ServerSSLOptions, ctxFromKeystore}
import zhttp.service.{EventLoopGroup, Server}
import zio._

object HttpsHelloWorld extends App {

  // Create HTTP route
  val app: HttpApp[Any, Nothing] = HttpApp.collect {
    case Method.GET -> !! / "text" => Response.text("Hello World!")
    case Method.GET -> !! / "json" => Response.jsonString("""{"greetings": "Hello World!"}""")
  }

  /**
   * sslcontext can be created using SslContexBuilder.
   * In this example an inbuilt API using keystore is used
   */
  val sslctx = ctxFromKeystore(getClass.getResourceAsStream("keystore.jks"), "password", "password")

  private val server =
    Server.port(8090) ++ Server.app(app) ++ Server.ssl(ServerSSLOptions(sslctx))

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = {
    server.make.useForever
      .provideCustomLayer(ServerChannelFactory.auto ++ EventLoopGroup.auto(0))
      .exitCode
  }
}
```