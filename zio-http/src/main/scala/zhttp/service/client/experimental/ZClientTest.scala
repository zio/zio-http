package zhttp.service.client.experimental

//import io.netty.handler.codec.http.{HttpHeaderNames, HttpHeaderValues}
import zhttp.http.URL.Location
import zhttp.http._
import zio.{App, ExitCode, URIO, ZIO}

/**
 * Simple client usage
 */
object ZClientTest extends App {

  private val PORT = 8081

  val client = ZClient.port(PORT) ++ ZClient.threads(2)
//  val keepAliveHeader = Headers(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE)
  val emptyHeaders = Headers.empty
  val req = ReqParams(Method.GET, URL(!! / "foo", Location.Absolute(Scheme.HTTP, "localhost", PORT)), emptyHeaders, HttpData.empty)

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = {
    val resp: ZIO[Any, Throwable, Resp] = client
      .make(req)
      .use (_.run)
    resp.exitCode
  }
}
