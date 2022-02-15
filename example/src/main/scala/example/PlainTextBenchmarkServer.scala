package example

import io.netty.util.AsciiString
import zhttp.http._
import zhttp.service.server.ServerChannelFactory
import zhttp.service.{EventLoopGroup, Server}
import zio.{App, ExitCode, UIO, URIO}

/**
 * This server is used to run plaintext benchmarks on CI.
 */
object Main extends App {

  private val message: String = "Hello, World!"
  private val json: String    = """{"greetings": "Hello World!"}"""

  private val STATIC_SERVER_NAME = AsciiString.cached("zio-http")

  private val frozenResponse = Response
    .text(message)
    .withServerTime
    .withServer(STATIC_SERVER_NAME)
    .freeze

  private val frozenJsonResponse = Response
    .json(json)
    .withServerTime
    .withServer(STATIC_SERVER_NAME)
    .freeze

  val path1 = "/plaintext"
  val path2 = "/json"

  def app(res: Response, json: Response) = {
    Http.fromFunctionHExit((_: Request) => HExit.succeed(res)).whenPath(path1) ++
      Http.fromFunctionHExit((_: Request) => HExit.succeed(json)).whenPath(path2)
  }

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = {
    val s = for {
      res1 <- frozenResponse
      res2 <- frozenJsonResponse
    } yield server(res1, res2)
    s.flatMap(_.make.useForever.provideCustomLayer(ServerChannelFactory.auto ++ EventLoopGroup.auto(8))).exitCode
  }

  private def server(response: Response, json: Response) =
    Server.app(app(response, json)) ++
      Server.port(8080) ++
      Server.error(_ => UIO.unit) ++
      Server.disableLeakDetection ++
      Server.consolidateFlush ++
      Server.disableFlowControl

}
