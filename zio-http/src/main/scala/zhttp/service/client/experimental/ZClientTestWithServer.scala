package zhttp.service.client.experimental

//import io.netty.handler.codec.http.{HttpHeaderNames, HttpHeaderValues}

import zhttp.http.URL.Location
import zhttp.http._
import zhttp.service.{EventLoopGroup, Server}
import zhttp.service.server.ServerChannelFactory
import zio.{App, ExitCode, URIO, ZIO}

object ZClientTestWithServer extends App {

  private val PORT = 8081

  private val fooBar: HttpApp[Any, Nothing] = Http.collect[Request] {
    case Method.GET -> !! / "foo" => Response.text("bar")
    case Method.GET -> !! / "bar" => Response.text("foo")
  }

  private val server =
    Server.port(PORT) ++              // Setup port
      Server.paranoidLeakDetection ++ // Paranoid leak detection (affects performance)
      Server.app(fooBar) ++ Server.keepAlive       // Setup the Http app

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = {
    server.make
      .use { start =>
        println(s"Server started on port ${start.port}")
        // Waiting for the server to start
        val client = ZClient.port(start.port) ++ ZClient.threads(2)
        //  val keepAliveHeader = Headers(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE)
        val emptyHeaders = Headers.empty
        val req = ReqParams(Method.GET, URL(!! / "foo", Location.Absolute(Scheme.HTTP, "localhost", start.port)), emptyHeaders, HttpData.empty)
        client.make(req).use(triggerClientMultipleTimes) *> ZIO.never
      }
      .provideCustomLayer(ServerChannelFactory.auto ++ EventLoopGroup.auto(2))
      .exitCode
  }

  def triggerClientMultipleTimes(cl: ZClientImpl) = for {
    resp <- cl.run
    _ <- ZIO.effect(println(s"GOT RESP: $resp"))
    _ <- ZIO.effect(Thread.sleep(5000))
    resp <- cl.run
    _ <- ZIO.effect(println(s"GOT ANOTHER RESP USING SAME CONNECTION $resp"))
  } yield ()

}
