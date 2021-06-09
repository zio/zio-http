import zhttp.http.{HttpData, Header}
import zhttp.service.{ChannelFactory, Client, EventLoopGroup}
import zio._

object SimpleClient extends App {
  val env     = ChannelFactory.auto ++ EventLoopGroup.auto()
  val url     = "http://sports.api.decathlon.com/groups/water-aerobics"
  val headers = List(Header.host("sports.api.decathlon.com"))

  val program = for {
    res <- Client.request(url, headers)
    _   <- console.putStrLn {
      res.content match {
        case HttpData.CompleteContent(data) => data.map(_.toChar).mkString
        case _                              => ""
      }
    }
  } yield ()

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = program.exitCode.provideCustomLayer(env)

}
