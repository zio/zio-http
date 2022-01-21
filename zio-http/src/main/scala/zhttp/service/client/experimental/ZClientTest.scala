package zhttp.service.client.experimental

import zhttp.http.URL.Location
import zhttp.http._
import zio.{App, ExitCode, URIO}

/**
 * Simple client usage
 */
object ZClientTest extends App {

  private val PORT = 8082

  val client: ZClient[Any, Nothing] = ZClient.port(PORT) ++ ZClient.threads(2)

  //  val keepAliveHeader = Headers(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE)
  val emptyHeaders = Headers.empty
  val req = ReqParams(
    Method.GET,
    URL(!! / "foo", Location.Absolute(Scheme.HTTP, "localhost", PORT)),
    emptyHeaders,
    HttpData.empty,
  )

  /*
    We can enhance it to use to more user friendly like
    client
     .fromRequest(req)
     .use (_.status)

    client
     .fromURL(url)
     .use (_.headers)
   */
  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = {
    val prog = for {
      cl <- client.make
      res <- cl.run(req)
      body <- res.getBodyAsString
      _ <- zio.console.putStrLn(body)
    } yield ()
    prog.exitCode
  }

}
