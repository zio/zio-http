import zhttp.http._
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
      Request(
        (Method.GET, URL.fromString(url).getOrElse(null)),
        List(
          Header.host("github.com"),
        ),
      ).cookiesFromHeader(res1.headers).addCookies(List(Cookie("a", "value"))),
    ) //add set-cookie from response header to request
    _    <- console.putStrLn {
      Response.cookies(res2.headers).toString //Empty as cookies are already set
    }
  } yield ()

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = program.exitCode.provideCustomLayer(env)

}
