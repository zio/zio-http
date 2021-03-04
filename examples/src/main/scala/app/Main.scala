package app

import io.circe.generic.auto._
import io.circe.syntax._
import zhttp.domain.http.HttpApp
import zhttp.domain.http.model.Method.GET
import zhttp.domain.http.model.Path.{/, Root}
import zhttp.domain.http.model.Response
import zhttp.service.netty.EventLoopGroup
import zhttp.service.netty.server.{Server, ServerChannelFactory}
import zio._
import zio.logging.Logging

object Main extends App {
  private val PORT = 3001

  case class Message(str: String)

  private val httpApp = HttpApp.route {
    case GET -> Root / "plaintext" => Response.text("Hello, World!")
    case GET -> Root / "json"      => Response.jsonString(Message("Hello, World!").asJson.noSpaces)
  }

  private def server = for {
    s <- Server.make(httpApp)
    _ <- s.start(PORT)
    _ <- Logging.info(s"Server started on $PORT")
  } yield ()

  private val env = ServerChannelFactory.Live.auto.toManaged_.toLayer ++ EventLoopGroup.Live.auto(0).toLayer ++
    Logging.console()

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    (server.toManaged_).useForever.provideCustomLayer(env).exitCode
}
