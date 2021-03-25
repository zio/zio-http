import zhttp.http.HttpContent
import zhttp.service.{ChannelFactory, Client, EventLoopGroup}
import zio._

object SimpleClient extends App {
  val env = ChannelFactory.auto ++ EventLoopGroup.auto()

  val program = for {
    res <- Client.request("https://api.github.com/users/zio/repos")
    _   <- console.putStrLn {
      res.content match {
        case HttpContent.Complete(data) => data
        case HttpContent.Chunked(_)     => "<Chunked>"
      }
    }
  } yield ()

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = program.exitCode.provideCustomLayer(env)

}
