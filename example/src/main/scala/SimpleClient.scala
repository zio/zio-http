import io.netty.handler.codec.http.HttpHeaderNames
import zhttp.http.{Header, HttpData}
import zhttp.service.{ChannelFactory, Client, EventLoopGroup}
import zio._

object SimpleClient extends App {
  val env     = ChannelFactory.auto ++ EventLoopGroup.auto()
  val url     = "http://sports.api.decathlon.com/groups/water-aerobics"
  val headers = List(
    Header.host("sports.api.decathlon.com"),
    Header.custom(HttpHeaderNames.CONTENT_TYPE.toString, "text/html; charset=US_ASCII"),
  )

  val program = for {
    res <- Client.request(url, headers)
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
