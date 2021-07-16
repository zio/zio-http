import zhttp.http._
import zhttp.service.server.ServerChannelFactory
import zhttp.service.server.ServerSSLHandler.{SSLHttpBehaviour, ServerSSLOptions, ctxFromCert}
import zhttp.service.{EventLoopGroup, Server}
import zio._

object HttpsServer extends App {

  // Create HTTP route
  val app: HttpApp[Any, Nothing] = HttpApp.collect {
    case Method.GET -> Root / "text" => Response.text("Hello World!")
    case Method.GET -> Root / "json" => Response.jsonString("""{"greetings": "Hello World!"}""")
  }

  /**
   * sslcontext can be created using SslContexBuilder. In this example an inbuilt API a certificate and key is used
   */
  val sslctx = ctxFromCert(getClass.getResourceAsStream("server.crt"), getClass.getResourceAsStream("server.key"))

  private val server =
    Server.port(8090) ++ Server.app(app) ++ Server.http2 ++ Server.ssl(
      ServerSSLOptions(sslctx, SSLHttpBehaviour.Accept),
    )

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = {
    server.make.useForever
      .provideCustomLayer(ServerChannelFactory.auto ++ EventLoopGroup.auto(0))
      .exitCode
  }
}
