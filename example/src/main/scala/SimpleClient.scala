import zhttp.service.{ChannelFactory, Client, EventLoopGroup}
import zio._

object SimpleClient extends App {
  val env = ChannelFactory.auto ++ EventLoopGroup.auto()

  val program = for {
    _ <- Client.request("https://api.github.com/users/zio/repos")
    _ <- console.putStrLn {
      ???
    }
  } yield ()

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = program.exitCode.provideCustomLayer(env)

}
