import zhttp.http.{Cookie, Header}
import zhttp.service.{ChannelFactory, Client, EventLoopGroup}
import zio._

/**
 * Example to make client request using cookies
 */
object CookieClientSide extends App {
  val env     = ChannelFactory.auto ++ EventLoopGroup.auto()
  val url     = "https://github.com/dream11/zio-http"
  val headers = List(Header.host("github.com"))

  val program = for {
    res1 <- Client.request(url, headers)
    res2 <- Client.request(
      url,
      List(Header.host("github.com"), Header.cookies(res1.cookies), Header.cookie(Cookie("name", "value"))),
    ) //add set-cookie from response header to request
    _    <- console.putStrLn {
      res2.cookies.toString //Empty as cookies are already set
    }
  } yield ()

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = program.exitCode.provideCustomLayer(env)

}
