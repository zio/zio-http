import zhttp.http.HttpData
import zhttp.service.{ChannelFactory, Client, EventLoopGroup}
import zio._

object SimpleClient extends App {
  val env                = ChannelFactory.auto ++ EventLoopGroup.auto()
  val trustStorePath     = System.getProperty("javax.net.ssl.trustStore")
  val trustStorePassword = System.getProperty("javax.net.ssl.trustStorePassword")

  val program            = for {
    res <- Client.request("https://api.github.com/users/zio/repos", trustStorePath, trustStorePassword)
    _   <- console.putStrLn {
      res.content match {
        case HttpData.CompleteData(data) => data.map(_.toChar).mkString
        case HttpData.StreamData(_)      => "<Chunked>"
        case HttpData.Empty              => ""
      }
    }
  } yield ()

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = program.exitCode.provideCustomLayer(env)

}
