package zio.web.example

import zio._
//import zio.duration._
import zio.logging.{ LogFormat, LogLevel, Logging, log }
import zio.schema.{ DeriveSchema, Schema }
import zio.web.{ Endpoints, Handler, Handlers, endpoint }
import zio.web.codec.JsonCodec
import zio.web.http.{ HttpMiddleware, HttpProtocol, HttpServer, HttpServerConfig }
import zio.web.http.model.{ Method, Route }
import zio.web.http.HttpClientConfig

object HelloServer extends App with HelloExample {

  // just define handlers for all endpoints
  lazy val sayHelloHandlers =
    Handlers(Handler.make(sayHello) { (name: String, req: HelloRequest) =>
      for {
        _ <- console.putStrLn(s"Handling sayHello/$name request with $req")
      } yield TextPlainResponse(s"Hi $name!")
    })

  // generate the server
  lazy val helloServerLayer =
    makeServer(HttpMiddleware.none, sayHelloService, sayHelloHandlers)

  // and run it
  def run(args: List[String]): URIO[ZEnv, ExitCode] =
    program.provideSomeLayer[ZEnv](loggingLayer).exitCode

  lazy val program =
    for {
      _      <- log.info("Hello server started")
      config = HttpServerConfig("localhost", 8080)
      server <- HttpServer.run.provideLayer(httpServer(config))
      _      <- server.awaitShutdown.orDie
      _      <- log.info("Hello server stopped")
    } yield ()

  // misc utils
  lazy val loggingLayer =
    Logging.console(LogLevel.Debug, LogFormat.ColoredLogFormat()) >>>
      Logging.withRootLoggerName("hello-example-server")

  def httpServer(config: HttpServerConfig) =
    (ZLayer.requires[ZEnv with Logging] ++ ZLayer.succeed(config)) >+> helloServerLayer
}

object HelloClient extends App with HelloExample {

  // just generate the client
  lazy val helloClientLayer =
    makeClient(sayHelloService)

  // and run it
  def run(args: List[String]): URIO[ZEnv, ExitCode] =
    program.provideSomeLayer[ZEnv](loggingLayer).exitCode

  lazy val program =
    for {
      _        <- log.info("Hello client started")
      config   = HttpClientConfig("localhost", 8080)
      request  = HelloRequest("Secret")
      response <- sayHelloService.invoke(sayHello)("Janet", request).provideLayer(httpClient(config))
      _        <- log.info(s"Got ${response}")
      _        <- log.info("Press [enter] to stop the client")
      _        <- console.getStrLn
      _        <- log.info("Hello client stopped")
    } yield ()

  // misc utils
  lazy val loggingLayer =
    Logging.console(LogLevel.Debug, LogFormat.ColoredLogFormat()) >>>
      Logging.withRootLoggerName("hello-example-client")

  def httpClient(config: HttpClientConfig) =
    (ZLayer.requires[ZEnv with Logging] ++ ZLayer.succeed(config)) >+> helloClientLayer
}

trait HelloExample extends HttpProtocol {

  // code shared between client and server
  val allProtocols    = Map.empty
  val defaultProtocol = JsonCodec

  sealed case class HelloRequest(message: String)
  sealed case class TextPlainResponse(content: String)

  val helloSchema: Schema[HelloRequest]          = DeriveSchema.gen
  val textPlainSchema: Schema[TextPlainResponse] = DeriveSchema.gen

  import Route._

  val sayHello =
    endpoint("sayHello")
      .withRequest(helloSchema)
      .withResponse(textPlainSchema) @@ Route(_ / "sayHello" / StringVal) @@ Method.POST

  lazy val sayHelloService = Endpoints(sayHello)
}
