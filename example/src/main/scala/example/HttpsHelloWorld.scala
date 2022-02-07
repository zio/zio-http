package example

import zhttp.http._
import zhttp.service.server.ServerChannelFactory
import zhttp.service.server.ServerSSLBuilder._
import zhttp.service.{EventLoopGroup, Server}
import zio.{App, ExitCode, URIO}

object HttpsHelloWorld extends App {
  // Create HTTP route
  val app: HttpApp[Any, Nothing] = Http.collect[Request] {
    case Method.GET -> !! / "text" => Response.text("Hello World!")
    case Method.GET -> !! / "json" => Response.json("""{"greetings": "Hello World!"}""")
  }

  /**
   * sslcontextbuilder can be created using SslContexBuilder.forServer(). In
   * this example an inbuilt API using keystore is used. For testing this
   * example using curl, setup the certificate named "server.crt" from resources
   * for the OS. Alternatively you can create the keystore and certificate using
   * the following link
   * https://medium.com/@maanadev/netty-with-https-tls-9bf699e07f01
   */
  val sslctx = ctxFromCert(
    getClass().getClassLoader().getResourceAsStream("server.crt"),
    getClass().getClassLoader().getResourceAsStream("server.key"),
  )

  private val server =
    Server.port(8090) ++ Server.app(app) ++ Server.ssl(
      ServerSSLOptions(sslctx, SSLHttpBehaviour.Accept),
    )

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = {
    server.make.useForever
      .provideCustomLayer(ServerChannelFactory.auto ++ EventLoopGroup.auto(0))
      .exitCode
  }
}
