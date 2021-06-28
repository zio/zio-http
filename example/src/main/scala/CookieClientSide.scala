import zhttp.http.Header
import zhttp.service.{ChannelFactory, Client, EventLoopGroup}
import zio._

object CookieClientSide extends App {
  val env     = ChannelFactory.auto ++ EventLoopGroup.auto()
  val url     = "https://github.com/dream11/zio-http"
  val headers = List(Header.host("github.com"))

  val program = for {
    res1 <- Client.request(url, headers)
    _    <- console.putStrLn { res1.cookies().toString() }
    res2 <- Client.request(url, List(Header.host("github.com"), Header.cookies(res1.cookies())))
    _    <- console.putStrLn {
      res2.cookies().toString()
    }
  } yield ()

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = program.exitCode.provideCustomLayer(env)

}
