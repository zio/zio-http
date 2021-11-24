import zhttp.http._
import zhttp.service.server.ServerChannelFactory
import zhttp.service.{EventLoopGroup, Server}
import zio._

object Composability extends zio.App {
  def embeddedApp: Http[Any, Nothing, Request, UResponse] = Http.collect[Request] {
    case Method.GET -> _ / "health"     => Response.text("health")
    case req @ Method.POST -> _ / "add" =>
      Response.text(s"You have reached embedded app's section. ${req.method}")
  }

  def app(embeddedApp: Http[Any, Nothing, Request, UResponse]): Http[Any, Nothing, Request, UResponse] = Http
    .collect[Request] { case _ -> !! / "d11" / _ => embeddedApp }
    .flatten

  override def run(args: List[String]): URIO[ZEnv, ExitCode] = {
    (Server.port(80) ++ Server.app(app(embeddedApp))).make.useForever
      .provideSomeLayer(ServerChannelFactory.auto ++ EventLoopGroup.auto())
  }.exitCode
}
