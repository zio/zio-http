package zhttp.service.client.experimental

//import io.netty.handler.codec.http.{HttpHeaderNames, HttpHeaderValues}
import zhttp.http.URL.Location
import zhttp.http._
import zio.{App, ExitCode, URIO}

object ZClientTest extends App {

  private val PORT = 8081

  val client = ZClient.port(PORT) ++ ZClient.threads(2)
//  val keepAliveHeader = Headers(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE)
  val emptyHeaders = Headers.empty
  val req = ReqParams(Method.GET, URL(!! / "foo", Location.Absolute(Scheme.HTTP, "localhost", PORT)), emptyHeaders, HttpData.empty)

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = {
    val resp = client
      .make(req)
      .use (_.run)
      resp.exitCode
  }

  // The code below shows the re-use of connection for the same request and messages regarding connection idle
//  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
//      client.make(req)
//        .use { cl =>
//          println(s"invoking client")
//          (for {
//            resp <- cl.run
//            _ <- ZIO.effect(println(s"GOT RESP: $resp"))
//            resp <- cl.run
//            _ <- ZIO.effect(println(s"GOT ANOTHER RESP: $resp"))
//            _ <- ZIO.effect(Thread.sleep(13000))
//            resp <- cl.run
//            //                    _ <- ZIO.effect(Thread.sleep(5000))
//            _ <- ZIO.effect(println(s"GOT ANOTHER RESP: $resp"))
//            resp <- cl.run
//            //                    _ <- ZIO.effect(Thread.sleep(5000))
//          } yield resp) *> ZIO.unit
//        }
//        .exitCode

}
