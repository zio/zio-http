import io.netty.handler.ssl.SslContextBuilder
import zhttp.http.{HttpData, Method, Request, URL}
import zhttp.service.client.ClientSSLHandler.ClientSSLOptions
import zhttp.service.{ChannelFactory, Client, EventLoopGroup}
import zio._

object Http2Client extends App {
  val env = ChannelFactory.auto ++ EventLoopGroup.auto()
  val url1 = "https://localhost:8090/text"
  val url2 = "https://localhost:8090/multi"

  val sslOption: ClientSSLOptions =
    ClientSSLOptions.CustomSSL(SslContextBuilder.forClient().trustManager(getClass.getResourceAsStream("server.crt")))

  val program = for {

    multi<-Client.multi(sslOption)
    _<- multi match {
      case Left(value) => {
        console.putStrLn(s"not workinf left ${value}")
      }
      case Right(putter) => for {
        ur1 <- ZIO.fromEither(URL.fromString(url1))
        ur2 <- ZIO.fromEither(URL.fromString(url2))
        res1 <- putter.apply(Request(Method.GET -> ur1),5).fork
        res2 <- putter.apply(Request(Method.GET -> ur2),6).fork
        r1<-res1.join
        r2<-res2.join
        _   <- console.putStrLn {
          r1.content match {
            case HttpData.CompleteData(_) => "data.map(_.toChar).mkString"
            case HttpData.StreamData(_)      => "<Chunked>"
            case HttpData.Empty              => ""
          }
        }
        _   <- console.putStrLn {
          r2.content match {
            case HttpData.CompleteData(_) => "data.map(_.toChar).mkString"
            case HttpData.StreamData(_)      => "<Chunked>"
            case HttpData.Empty              => ""
          }
        }
      } yield ()
    }
  } yield ()

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = program.exitCode.provideCustomLayer(env)

}
