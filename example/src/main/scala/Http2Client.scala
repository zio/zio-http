import io.netty.handler.ssl.SslContextBuilder
import zhttp.http.HttpData
import zhttp.service.client.ClientSSLHandler.ClientSSLOptions
import zhttp.service.{ChannelFactory, Client, EventLoopGroup}
import zio._

object Http2Client extends App {
  val env = ChannelFactory.auto ++ EventLoopGroup.auto()
  val url = "http://localhost:8090/text"

  val sslOption: ClientSSLOptions =
    ClientSSLOptions.CustomSSL(SslContextBuilder.forClient().trustManager(getClass.getResourceAsStream("server.crt")))

  val program = for {
    res <- Client.request(url, sslOption)
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
