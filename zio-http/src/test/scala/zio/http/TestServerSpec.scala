package zio.http

import zio._
import zio.http.URL.Location
import zio.http.model._
import zio.http.netty.server.NettyDriver
import zio.test._

object TestServerSpec extends ZIOSpecDefault{
  def spec = test("use our test server"){
    for {
      _ <- ZIO.serviceWithZIO[TestServer[Unit]](_.addHandler {
        case _: Request => Response.ok
      })

//      _ <- ZIO.serviceWithZIO[Driver](_.start)
//      _ <- ZIO.serviceWithZIO[Server](_.install(Http.ok))
      port <- ZIO.serviceWith[Server](_.port)
      _ <-
        Client.request(
          Request(url = URL(Path.root, Location.Absolute(Scheme.HTTP, "localhost", port)))
        ).debug
//      _ <-
//        Client.request(
//          Request(url = URL(Path.root / "users", Location.Absolute(Scheme.HTTP, "localhost", port)))
//        )
//      finalRequests <- TestServerOld.interactions

    } yield assertCompletes
  }.provideSome[Scope](
//    ZLayer.succeed(ClientConfig()),
    ServerConfig.live,
    ZLayer.fromZIO(TestServer.make),
    Client.default,
    NettyDriver.default,
//    ZLayer.fromZIO(TestServer.make)
  )

}
