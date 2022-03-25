package example

import zhttp.http.{Http, Method, Request, Response, _}
import zhttp.service.{ChannelFactory, Client, EventLoopGroup}
import zio.{App, ExitCode, URIO, console}

object FileUploader extends App {

  val app = Http.collect[Request] { case req @ Method.POST -> !! / "upload" =>
    Response(data = HttpData.fromStream(req.bodyAsStream))
  }

  val env = ChannelFactory.auto ++ EventLoopGroup.auto()
  val url = "http://localhost:8090"

  val program = for {
    res  <- Client.request(url)
    data <- res.bodyAsString
    _    <- console.putStrLn { data }
  } yield ()

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = ???
}
