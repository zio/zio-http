package zhttp.service

import zhttp.http._
import zhttp.service.server.Transport
import zio.ZIO
import zio.test.Assertion._
import zio.test.{TestFailure, assertM}

object TransportSpec extends HttpRunnableSpec(9091) {

  val env = EventLoopGroup.auto() ++ ChannelFactory.auto

  val http: HttpApp[Any, Throwable] = HttpApp.collectM { case Method.GET -> !! / "success" =>
    ZIO.succeed(Response.ok)
  }

  val appWithAuto  = serve(http)
  val appWithEpoll = serve(http, 9092, Transport.Epoll)

  val appWithAutoLayer  = appWithAuto.toLayer
  val appWithEpollLayer = appWithEpoll.toLayer

  override def spec = suite("Server")(
    suite("Transport With Auto")(
      testM("200 response with Auto transport mode") {
        val actual = status(!! / "success")
        assertM(actual)(equalTo(Status.OK))
      },
    ).provideCustomLayerShared(env ++ appWithAutoLayer),
    suite("Transport With Epoll")(
      testM("200 response with Epoll transport mode") {
        val actual = status(!! / "success")
        assertM(actual)(equalTo(Status.OK))
      },
    ).provideCustomLayerShared(env ++ appWithEpollLayer),
  ).mapError(TestFailure.fail)

}
