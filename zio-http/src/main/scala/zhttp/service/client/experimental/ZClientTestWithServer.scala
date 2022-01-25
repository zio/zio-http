package zhttp.service.client.experimental

import zhttp.http.URL.Location
import zhttp.http._
import zhttp.service.server.ServerChannelFactory
import zhttp.service.{EventLoopGroup, Server}
import zio.{App, ExitCode, URIO, ZIO}

/**
 * Self contained ZClient demo with server.
 */
object ZClientTestWithServer extends App {

  private val PORT = 8081

  private val fooBar: HttpApp[Any, Nothing] = Http.collect[Request] {
    case Method.GET -> !! / "foo" => Response.text("bar")
    case Method.GET -> !! / "bar" => Response.text("foo")
  }

  private val server =
    Server.port(PORT) ++                     // Setup port
      Server.paranoidLeakDetection ++        // Paranoid leak detection (affects performance)
      Server.app(fooBar) ++ Server.keepAlive // Setup the Http app

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = {
    server.make.use { start =>
      println(s"Server started on port ${start.port}")
      clientTest(start.port) *> ZIO.never
    }
      .provideCustomLayer(ServerChannelFactory.auto ++ EventLoopGroup.auto(2))
      .exitCode
  }

  def clientTest(port: Int) = {
    val client       = ZClient.port(port) ++ ZClient.threads(2)
    val emptyHeaders = Headers.empty
    val req          = ReqParams(
      Method.GET,
      URL(!! / "foo", Location.Absolute(Scheme.HTTP, "localhost", port)),
      emptyHeaders,
      HttpData.empty,
    )
    for {
      cl <- client.make
//      res1 <- cl.run("")
      _  <- triggerClientMultipleTimes(cl, req)
    } yield ()

  }

  // multiple client shared resources
  // different conn pool for diff host port
  // pool should correspond to host / port combo
  // have one client and
  // optimizations in the background
  // just one client serves all and sundry

  // use cases like pipelining ... httpclient document

  def triggerClientMultipleTimes(cl: DefaultZClient, req: ReqParams) = for {
    //    resp    <- cl.run(r1)
    resp <- cl.run(req)
    rval    <- resp.getBodyAsString
    _    <- ZIO.effect(println(s"GOT FIRST RESP: $rval"))
    _ <- ZIO.effect(println(s"======= NOW SLEEPING for 3000 ======== ms"))
    _ <- ZIO.effect(Thread.sleep(5000))
//    //    resp    <- cl.run(r2)
    resp2 <- cl.run("http://www.google.com")
//    resp2 <- cl.run("http://sports.api.decathlon.com/groups/water-aerobics")
    r2 <- resp2.getBodyAsString
    _ <- ZIO.effect(println(s"GOT second response : $r2"))

//    req2 = ReqParams(
//      Method.GET,
//      URL(!! / "foo", Location.Absolute(Scheme.HTTP, "localhost", 8082)),
//      Headers.empty,
//      HttpData.empty,
//    )

//    req2 = ReqParams(
//      Method.GET,
////      URL(!! , Location.Absolute(Scheme.HTTP, "localhost", 8082)),
////      Headers.empty,
//
//      URL(!!, Location.Absolute(Scheme.HTTP, "www.google.com", 80)),
//      Headers.host("www.google.com"),
//      HttpData.empty,
//    )
//
//    //    resp <- cl.run("http://sports.api.decathlon.com/groups/water-aerobics")
//    resp <- cl.run(req2)
//    r2   <- ZIO.effect(resp.status)
//    _    <- ZIO.effect(println(s"R!!!: $r2"))
//    _ <- ZIO.effect(Thread.sleep(2000))
//    resp <- cl.run("http://sports.api.decathlon.com/groups/water-aerobics")
//    _ <- resp.getBodyAsString

//    _ <- ZIO.effect(println(s"GOT ANOTHER RESP USING SAME CONNECTION ${result2}"))
  } yield ()

}
