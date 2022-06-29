package example

import zhttp.http._
import zhttp.service.server.ServerChannelFactory
import zhttp.service.{EventLoopGroup, Server}
import zio._

object MultipleCallsOfRequestBodyAsString extends App {

  val app: HttpApp[Any, Nothing] = Http.collectZIO[Request] {
    case req @ Method.POST -> !! / "text" =>
      val reqBody = req.bodyAsString
      reqBody.map(input => Response.text(s"Hello World! $input")).tap(res => ZIO.debug(res)).catchAll {
        case err: Throwable =>
          ZIO.succeed(Response.text(s"Failed with ${err.getMessage}"))
      }

    case Method.GET -> !! / "json" => ZIO.succeed(Response.json("""{"greetings": "Hello World!"}"""))
  }

  val logMiddleware =
    Middleware.interceptZIOPatch(req => zio.clock.nanoTime.map(start => (req.method, req, req.url, start))) {
      case (response, (method, reqBody, url, start)) =>
        for {
          end  <- clock.nanoTime
          body <- reqBody.bodyAsString.orDie
          _    <- console
            .putStrLn(s"$body ${response.status.asJava.code()} ${method} ${url.encode} ${(end - start) / 1000000}ms")
            .mapError(Option(_))
        } yield Patch.empty
    }

  private val server =
    Server.port(8090) ++ Server.app(app @@ logMiddleware) ++ Server.enableObjectAggregator(Int.MaxValue)

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    server.make.useForever
      .provideCustomLayer(ServerChannelFactory.auto ++ EventLoopGroup.auto(0))
      .exitCode
}
