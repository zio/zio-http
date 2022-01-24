package example

import zhttp.http._
import zhttp.service.server.ServerChannelFactory
import zhttp.service.server.ServerSSLHandler._
import zhttp.service.{EventLoopGroup, Server}
import zhttp.socket.{Socket, WebSocketFrame}
import zio.stream.ZStream
import zio.{App, ExitCode, URIO, ZIO}

object HttpsHelloWorld extends App {
  private val socket = Socket.collect[WebSocketFrame] { case WebSocketFrame.Text(_) =>
    ZStream.succeed(WebSocketFrame.text("Hello, World!"))
  }

  // Create HTTP route
  val app: HttpApp[Any, Nothing] = Http.collectZIO[Request] {
    case Method.GET -> !! / "text"          => ZIO.succeed(Response.text("Hello World!"))
    case Method.GET -> !! / "json"          => ZIO.succeed(Response.json("""{"greetings": "Hello World!"}"""))
    case Method.GET -> !! / "subscriptions" => socket.toResponse
  }

  /**
   * sslcontext can be created using SslContexBuilder. In this example an inbuilt API using keystore is used. For
   * testing this example using curl, setup the certificate named "server.crt" from resources for the OS. Alternatively
   * you can create the keystore and certificate using the following link
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
