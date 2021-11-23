package zhttp

import zhttp.http.HttpData.CompleteData
import zhttp.http.Response.HttpResponse
import zhttp.http._
import zhttp.service._
import zio._
import zio.duration._
import zio.test.Assertion._
import zio.test.TestAspect.timeout
import zio.test._

object IntegrationSpec extends DefaultRunnableSpec {
  import IntegrationSpecHelper._

  def spec = suite("IntegrationSpec")(
    ApiSpec,
  ).provideCustomLayer(env) @@ timeout(10 seconds)

  def ApiSpec = suite("ApiSpec") {
    testM("100 continue on /continue") {
      val zResponse = Client.request(s"${baseAddr}/continue", List(Header("Expect", "100-continue")))
      assertM(zResponse)(
        equalTo(
          HttpResponse(
            Status.CONTINUE,
            List(Header("content-length", "0")),
            CompleteData(Chunk.empty),
          ),
        ),
      )
    } +
      testM("200 ok on /") {
        val zResponse = Client.request(baseAddr)
        assertM(zResponse.map(_.headerRemoved("date")))(
          equalTo(
            HttpResponse(
              Status.OK,
              List(Header("server", "ZIO-Http"), Header("content-length", "0")),
              CompleteData(Chunk.empty),
            ),
          ),
        )
      } +
      testM("201 created on /post") {
        val url       = URL(Path.apply(), URL.Location.Absolute(Scheme.HTTP, addr, port))
        val zResponse = Client.request((Method.POST, url))
        assertM(zResponse.map(_.headerRemoved("date")))(
          equalTo(
            HttpResponse(
              Status.CREATED,
              List(Header("server", "ZIO-Http"), Header("content-length", "0")),
              CompleteData(Chunk.empty),
            ),
          ),
        )
      } +
      testM("400 bad request on /subscriptions without the connection upgrade header") {
        val zResponse = Client.request(s"${baseAddr}/subscriptions")
        assertM(zResponse)(
          equalTo(
            HttpResponse(
              Status.BAD_REQUEST,
              List(Header("connection", "close"), Header("content-length", "50")),
              CompleteData("not a WebSocket handshake request: missing upgrade".toChunk),
            ),
          ),
        )
      } +
      testM("500 internal server error on /boom") {
        val zResponse = Client.request(s"${baseAddr}/boom")
        assertM(zResponse.map(_.status))(equalTo(Status.INTERNAL_SERVER_ERROR))
      }
  }

  Runtime.default.unsafeRun(server.make.useForever.provideSomeLayer(env).forkDaemon)
}
