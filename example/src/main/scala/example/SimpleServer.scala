package example

import zhttp.http._
import zhttp.service.server.ServerChannelFactory
import zhttp.service.{EventLoopGroup, Server}
import zio._
object SimpleServer extends App {

  val app: HttpApp[Any, Throwable] = Http.collectZIO[Request] {
    case Method.GET -> !! / "get"       => Response.ok.wrapZIO
    case r @ Method.POST -> !! / "post" =>
      for {
        content <- r.getBodyAsString
      } yield Response.text(content)
  }

  // Run it like any simple app
  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    (Server.app(app) ++ Server.port(7777)).make
      .flatMap(start => ZManaged.succeed(println(s"Server started on port: ${start.port}")))
      .useForever
      .provideSomeLayer(ServerChannelFactory.auto ++ EventLoopGroup.auto(0))
      .exitCode
}
