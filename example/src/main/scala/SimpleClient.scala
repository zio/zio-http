import zhttp.http.{Header, HttpData, TrustStoreConfig}
import zhttp.service.{ChannelFactory, Client, EventLoopGroup}
import zio._

object SimpleClient extends App {
  val env     = ChannelFactory.auto ++ EventLoopGroup.auto()
  val url     = "https://sports.api.decathlon.com/groups/water-aerobics"
  val headers = List(Header.host("sports.api.decathlon.com"))

  //Configuring Truststore for https(optional)
  val trustStorePath                     = System.getProperty("javax.net.ssl.trustStore")
  val trustStorePassword                 = System.getProperty("javax.net.ssl.trustStorePassword")
  val trustStoreConfig: TrustStoreConfig = TrustStoreConfig(trustStorePath, trustStorePassword)

  val program = for {
    res <- Client.request(url, headers, trustStoreConfig)
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
