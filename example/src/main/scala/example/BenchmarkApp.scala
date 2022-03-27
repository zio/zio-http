import io.netty.util.AsciiString
import zhttp.http._
import zhttp.service.server.ServerChannelFactory
import zhttp.service.{EventLoopGroup, Server, UServer}
import zio.{ExitCode, UIO, URIO, ZEnv, ZIO, system}

/**
 * This server is used to run plaintext benchmarks on CI.
 */
object BenchmarkApp extends zio.App {

  private def leakDetectionLevel(level: String): UServer = level match {
    case "disabled" => Server.disableLeakDetection
    case "simple" => Server.simpleLeakDetection
    case "advanced" => Server.advancedLeakDetection
    case "paranoid" => Server.paranoidLeakDetection
  }

  private def settings: ZIO[system.System, SecurityException, Server[Any, Nothing]] = zio.system.envs.map { envs =>
    val acceptContinue = envs.getOrElse("ACCEPT_CONTINUE", "false").toBoolean
    val disableKeepAlive = envs.getOrElse("DISABLE_KEEP_ALIVE", "false").toBoolean
    val consolidateFlush = envs.getOrElse("CONSOLIDATE_FLUSH", "true").toBoolean
    val disableFlowControl = envs.getOrElse("DISABLE_FLOW_CONTROL", "true").toBoolean
    val maxRequestSize = envs.getOrElse("MAX_REQUEST_SIZE", "-1").toInt

    val server = Server.port(8080) ++ leakDetectionLevel(envs.getOrElse("LEAK_DETECTION_LEVEL", "disabled"))

    if (acceptContinue) server ++ Server.acceptContinue
    if (disableKeepAlive) server ++ Server.disableKeepAlive
    if (consolidateFlush) server ++ Server.consolidateFlush
    if (disableFlowControl) server ++ Server.disableFlowControl
    if (maxRequestSize > -1) server ++ Server.enableObjectAggregator(maxRequestSize)

    server
  }

  private val plainTextMessage: String = "Hello, World!"
  private val jsonMessage: String = """{"greetings": "Hello World!"}"""

  private val plaintextPath = "/plaintext"
  private val jsonPath = "/json"

  private val STATIC_SERVER_NAME = AsciiString.cached("zio-http")

  private val frozenJsonResponse = Response
    .json(jsonMessage)
    .withServerTime
    .withServer(STATIC_SERVER_NAME)
    .freeze

  private val frozenPlainTextResponse = Response
    .text(plainTextMessage)
    .withServerTime
    .withServer(STATIC_SERVER_NAME)
    .freeze

  private def plainTextApp(response: Response) = Http.fromHExit(HExit.succeed(response)).whenPathEq(plaintextPath)

  private def jsonApp(json: Response) = Http.fromHExit(HExit.succeed(json)).whenPathEq(jsonPath)

  val zApp = for {
    plainTextResponse <- frozenPlainTextResponse
    jsonResponse <- frozenJsonResponse
  } yield plainTextApp(plainTextResponse) ++ jsonApp(jsonResponse)

  override def run(args: List[String]): URIO[ZEnv, ExitCode] = {
    zApp.flatMap { app =>
      settings.flatMap { server =>
        (Server.app(app) ++ server ++ Server.error(_ => UIO.unit)).make.useForever
      }
    }
  }.exitCode
    .provideCustomLayer(ServerChannelFactory.auto ++ EventLoopGroup.auto(8))
}
