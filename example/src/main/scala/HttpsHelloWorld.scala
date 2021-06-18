import zhttp.http._
import zhttp.service.server.ServerChannelFactory
import zhttp.service.server.ServerSSLHandler.ServerSSLOptions._
import zhttp.service.{EventLoopGroup, Server}
import zio._

object HttpsHelloWorld extends App {

  // Create HTTP route
  val app: HttpApp[Any, Nothing] = HttpApp.collect {
    case Method.GET -> Root / "text" => Response.text("Hello World!")
    case Method.GET -> Root / "json" => Response.jsonString("""{"greetings": "Hello World!"}""")
  }

  /**
   * custom sslcontext can be created using SslContexBuilder. In this example an inbuilt API using keystore is used
   */

  import java.io.ByteArrayOutputStream

  val baos           = new ByteArrayOutputStream
  getClass.getResourceAsStream("keystore.jks").transferTo(baos)
  private val server = Server.port(8090) ++ Server.app(app) ++ Server.ssl(
    SSLFromKeystore(baos, "password", "password"),
  )

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = {
    server.make.useForever
      .provideCustomLayer(ServerChannelFactory.auto ++ EventLoopGroup.auto(0))
      .exitCode
  }
}
